package teralizer.spoon.analysis;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.util.Configuration;

public class TestShapeTest {

    @Example
    void junit3TestMethodRequiresTheShapeItsRunnerAccepts() {
        CtClass<?> testClass = classOf(
            "public class T extends junit.framework.TestCase {\n"
                + "  public void testOk() {}\n"
                + "  public void testWithParameter(int x) {}\n"
                + "  protected void testNotPublic() {}\n"
                + "  public int testNotVoid() { return 1; }\n"
                + "  public void helper() {}\n"
                + "}\n", "T");

        Assert.assertTrue(TestShape.isJUnit3TestMethod(method(testClass, "testOk"), testClass));
        Assert.assertFalse(TestShape.isJUnit3TestMethod(method(testClass, "testWithParameter"), testClass));
        Assert.assertFalse(TestShape.isJUnit3TestMethod(method(testClass, "testNotPublic"), testClass));
        Assert.assertFalse(TestShape.isJUnit3TestMethod(method(testClass, "testNotVoid"), testClass));
        Assert.assertFalse(TestShape.isJUnit3TestMethod(method(testClass, "helper"), testClass));
    }

    @Example
    void junit3ShapeRequiresATestCaseAncestor() {
        CtClass<?> plain = classOf("public class T { public void testOk() {} }\n", "T");
        Assert.assertFalse(TestShape.isJUnit3TestMethod(method(plain, "testOk"), plain));
        Assert.assertFalse(TestShape.extendsTestCase(plain));

        CtClass<?> viaBase = classOf(
            "public class T extends Base { public void testOk() {} }\n"
                + "class Base extends junit.framework.TestCase {}\n", "T");
        Assert.assertTrue(TestShape.extendsTestCase(viaBase));
        Assert.assertTrue(TestShape.isJUnit3TestMethod(method(viaBase, "testOk"), viaBase));
    }

    @Example
    void frameworkIsResolvedByQualifiedAnnotationName() {
        CtClass<?> testng = classOf(
            "public class T { @org.testng.annotations.Test public void t() {} }\n", "T");
        Assert.assertEquals(TestShape.Framework.TESTNG,
            TestShape.frameworkOf(method(testng, "t"), testng));
        Assert.assertEquals(Configuration.TEST_MARKER_TESTNG,
            TestShape.markerOf(method(testng, "t"), testng));

        CtClass<?> junit4 = classOf("public class T { @org.junit.Test public void t() {} }\n", "T");
        Assert.assertEquals(TestShape.Framework.JUNIT4,
            TestShape.frameworkOf(method(junit4, "t"), junit4));
        Assert.assertEquals("Test", TestShape.markerOf(method(junit4, "t"), junit4));

        CtClass<?> junit3 = classOf(
            "public class T extends junit.framework.TestCase { public void testOk() {} }\n", "T");
        Assert.assertEquals(TestShape.Framework.JUNIT3,
            TestShape.frameworkOf(method(junit3, "testOk"), junit3));
        Assert.assertEquals(Configuration.TEST_MARKER_JUNIT3,
            TestShape.markerOf(method(junit3, "testOk"), junit3));
    }

    @Example
    void disabledIsDetectedOnMethodAndOnClass() {
        CtClass<?> methodDisabled = classOf(
            "public class T { @org.junit.Test @org.junit.Ignore public void t() {} }\n", "T");
        Assert.assertTrue(TestShape.isDisabled(method(methodDisabled, "t"), methodDisabled));

        CtClass<?> classDisabled = classOf(
            "@org.junit.jupiter.api.Disabled\n"
                + "public class T { @org.junit.jupiter.api.Test public void t() {} }\n", "T");
        Assert.assertTrue(TestShape.isDisabled(method(classDisabled, "t"), classDisabled));

        CtClass<?> live = classOf("public class T { @org.junit.Test public void t() {} }\n", "T");
        Assert.assertFalse(TestShape.isDisabled(method(live, "t"), live));
    }

    @Example
    void junit3FixturesMapOntoJqwikLifecyclePhases() {
        CtClass<?> testClass = classOf(
            "public class T extends junit.framework.TestCase {\n"
                + "  protected void setUp() {}\n"
                + "  protected void tearDown() {}\n"
                + "  public void testOk() {}\n"
                + "}\n", "T");

        Assert.assertEquals(TestShape.LifecyclePhase.BEFORE_PROPERTY,
            TestShape.lifecyclePhaseOf(method(testClass, "setUp"), testClass));
        Assert.assertEquals(TestShape.LifecyclePhase.AFTER_PROPERTY,
            TestShape.lifecyclePhaseOf(method(testClass, "tearDown"), testClass));
        Assert.assertNull(TestShape.lifecyclePhaseOf(method(testClass, "testOk"), testClass));
    }

    @Example
    void junit3FixtureNamesOutsideATestCaseAreNotFixtures() {
        CtClass<?> plain = classOf("public class T { protected void setUp() {} }\n", "T");
        Assert.assertNull(TestShape.lifecyclePhaseOf(method(plain, "setUp"), plain));
    }

    @Example
    void annotatedLifecycleMethodsKeepTheirPhaseAndStaticRequirement() {
        CtClass<?> testClass = classOf(
            "public class T {\n"
                + "  @org.junit.Before public void b() {}\n"
                + "  @org.junit.jupiter.api.AfterEach public void a() {}\n"
                + "  @org.junit.BeforeClass public static void bc() {}\n"
                + "}\n", "T");

        Assert.assertEquals(TestShape.LifecyclePhase.BEFORE_PROPERTY,
            TestShape.lifecyclePhaseOf(method(testClass, "b"), testClass));
        Assert.assertEquals(TestShape.LifecyclePhase.AFTER_PROPERTY,
            TestShape.lifecyclePhaseOf(method(testClass, "a"), testClass));

        TestShape.LifecyclePhase container = TestShape.lifecyclePhaseOf(method(testClass, "bc"), testClass);
        Assert.assertEquals(TestShape.LifecyclePhase.BEFORE_CONTAINER, container);
        Assert.assertTrue(container.requiresStatic());
        Assert.assertTrue(container.isBefore());
        Assert.assertFalse(TestShape.LifecyclePhase.AFTER_PROPERTY.requiresStatic());
        Assert.assertFalse(TestShape.LifecyclePhase.AFTER_PROPERTY.isBefore());
    }

    private static CtClass<?> classOf(String source, String className) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source, className + ".java"));
        launcher.buildModel();
        return launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, className))
            .get(0);
    }

    private static CtMethod<?> method(CtClass<?> testClass, String name) {
        return testClass.getMethodsByName(name).get(0);
    }
}
