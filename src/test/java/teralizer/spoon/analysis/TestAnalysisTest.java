package teralizer.spoon.analysis;

import java.util.Optional;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class TestAnalysisTest {

    @Example
    void hamcrestIsViewExposesActualAndExpected() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.is(1));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(1)", view.getActualExpression().toString());
        Assert.assertEquals("1", view.getExpectedExpression().toString());
    }

    @Example
    void hamcrestEqualToViewExposesActualAndExpected() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(2), org.hamcrest.CoreMatchers.equalTo(2));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(2)", view.getActualExpression().toString());
        Assert.assertEquals("2", view.getExpectedExpression().toString());
    }

    @Example
    void hamcrestThreeArgumentOverloadUsesSecondArgumentAsActual() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(\"reason\", new Subject().id(3), org.hamcrest.CoreMatchers.is(3));");

        Assert.assertEquals("new Subject().id(3)", view.getActualExpression().toString());
        Assert.assertEquals("3", view.getExpectedExpression().toString());
    }

    @Example
    void nestedHamcrestIsEqualToUsesInnerExpectedExpression() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(4), org.hamcrest.CoreMatchers.is(org.hamcrest.CoreMatchers.equalTo(4)));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(4)", view.getActualExpression().toString());
        Assert.assertEquals("4", view.getExpectedExpression().toString());
    }

    @Example
    void nonEqualityHamcrestMatcherIsNotGeneralizable() {
        CtInvocation<?> assertion = assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.notNullValue());");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(assertion).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(assertion));
    }

    @Example
    void nonHamcrestIsMatcherIsNotGeneralizable() {
        CtInvocation<?> assertion = assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), SubjectTest.is(1));");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(assertion).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(assertion));
    }

    @Example
    void invocationAwareGateAdmitsOnlyEqualityIsomorphicAssertThat() {
        Assert.assertTrue(TestAnalysis.isGeneralizable(assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.is(1));")));
        Assert.assertTrue(TestAnalysis.isGeneralizable(assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.equalTo(1));")));
        Assert.assertFalse(TestAnalysis.isGeneralizable("assertThat"));
    }

    private static TestAnalysis.NormalizedAssertion normalizedAssertion(String assertionSource) {
        Optional<TestAnalysis.NormalizedAssertion> view = TestAnalysis.normalizedAssertion(assertion(assertionSource));
        Assert.assertTrue("expected normalized assertion for " + assertionSource, view.isPresent());
        return view.get();
    }

    private static CtInvocation<?> assertion(String assertionSource) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source(assertionSource), "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        return TestAnalysis.findAllAsserts(testMethod).get(0);
    }

    private static String source(String assertionSource) {
        return "public class SubjectTest {\n"
            + "  static Object is(int value) { return value; }\n"
            + "  public void t() { " + assertionSource + " }\n"
            + "}\n"
            + "class Subject { int id(int x) { return x; } }\n";
    }
}
