package teralizer.spoon;

import java.nio.file.Paths;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class InheritedTestMethodScreensTest {
    @Example
    void genericParentMethodIsExcludedWithTypedReason() {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public abstract class AbstractBase<T> {\n"
                + "  @Test public void inherited() {\n"
                + "    T value = null;\n"
                + "    org.junit.Assert.assertNull(value);\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase<String> {\n"
                + "}\n"
        );

        InheritedTestMethodScreens.Result result = InheritedTestMethodScreens.evaluate(scenario.child, scenario.method);

        Assert.assertFalse(result.isFlattenable());
        Assert.assertEquals(
            InheritedTestMethodScreens.INHERITED_METHOD_NOT_FLATTENABLE + ":TYPE_VARIABLE",
            result.getExclusionInfo()
        );
    }

    @Example
    void privateParentHelperReferenceIsExcludedWithTypedReason() {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public class AbstractBase {\n"
                + "  @Test public void inherited() {\n"
                + "    org.junit.Assert.assertEquals(1, hidden());\n"
                + "  }\n"
                + "  private int hidden() { return 1; }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n"
        );

        InheritedTestMethodScreens.Result result = InheritedTestMethodScreens.evaluate(scenario.child, scenario.method);

        Assert.assertFalse(result.isFlattenable());
        Assert.assertEquals(
            InheritedTestMethodScreens.INHERITED_METHOD_NOT_FLATTENABLE + ":PRIVATE_MEMBER",
            result.getExclusionInfo()
        );
    }

    @Example
    void protectedParentHelperReferenceIsFlattenable() {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public class AbstractBase {\n"
                + "  @Test public void inherited() {\n"
                + "    org.junit.Assert.assertEquals(1, visible());\n"
                + "  }\n"
                + "  protected int visible() { return 1; }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n"
        );

        InheritedTestMethodScreens.Result result = InheritedTestMethodScreens.evaluate(scenario.child, scenario.method);

        Assert.assertTrue(result.isFlattenable());
        Assert.assertNull(result.getExclusionInfo());
    }

    private static Scenario scenario(String parentSource, String childSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(parentSource, Paths.get(System.getProperty("user.dir"), "AbstractBase.java").toString()));
        launcher.addInputResource(new VirtualFile(childSource, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> child = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtClass<?> parent = model.getElements(new NamedElementFilter<>(CtClass.class, "AbstractBase")).get(0);
        CtMethod<?> method = parent.getMethodsByName("inherited").get(0);
        return new Scenario(child, method);
    }

    private static final class Scenario {
        private final CtClass<?> child;
        private final CtMethod<?> method;

        private Scenario(CtClass<?> child, CtMethod<?> method) {
            this.child = child;
            this.method = method;
        }
    }
}
