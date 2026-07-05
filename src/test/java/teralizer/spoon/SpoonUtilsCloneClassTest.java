package teralizer.spoon;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class SpoonUtilsCloneClassTest {
    @Example
    void cloneClassCopiesFlattenableInheritedTestAndLifecycleMethods() {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Before;\n"
                + "import org.junit.Test;\n"
                + "public class AbstractBase {\n"
                + "  protected int value;\n"
                + "  @Before public void setUp() { value = visible(); }\n"
                + "  @Test public void inherited() { org.junit.Assert.assertEquals(1, value); }\n"
                + "  protected int visible() { return 1; }\n"
                + "}\n",
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n"
        );

        CtClass<?> clone = SpoonUtils.cloneClass(
            scenario.child.getFactory(),
            scenario.child,
            "smoke",
            "smoke",
            "SubjectTest",
            "_SubjectTest_Generalized_inherited_1_Test",
            "smoke.SubjectTest",
            "smoke._SubjectTest_Generalized_inherited_1_Test"
        );

        Assert.assertEquals(1, clone.getMethodsByName("inherited").size());
        Assert.assertEquals(1, clone.getMethodsByName("setUp").size());
        Assert.assertTrue(annotationNames(clone.getMethodsByName("inherited").get(0)).contains("Test"));
        Assert.assertTrue(annotationNames(clone.getMethodsByName("setUp").get(0)).contains("Before"));
        CtTypeReference<?> superclass = clone.getSuperclass();
        Assert.assertNotNull(superclass);
        Assert.assertEquals("smoke.AbstractBase", superclass.getQualifiedName());
    }

    @Example
    void cloneClassKeepsUnflattenableInheritedTestsOutOfDeclaredMethods() {
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

        CtClass<?> clone = SpoonUtils.cloneClass(
            scenario.child.getFactory(),
            scenario.child,
            "smoke",
            "smoke",
            "SubjectTest",
            "_SubjectTest_Generalized_inherited_1_Test",
            "smoke.SubjectTest",
            "smoke._SubjectTest_Generalized_inherited_1_Test"
        );

        Assert.assertTrue(clone.getMethodsByName("inherited").isEmpty());
    }

    @Example
    void generatedClassImportsIncludeFlattenedMethodDeclaringUnitImportsOnce() {
        Scenario scenario = scenario(
            "package smoke;\n"
                + "import org.junit.Before;\n"
                + "import org.junit.Test;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class AbstractBase {\n"
                + "  protected int value;\n"
                + "  @Before public void setUp() { value = 1; }\n"
                + "  @Test public void inherited() { assertEquals(1, value); }\n"
                + "}\n",
            "package smoke;\n"
                + "import org.junit.Test;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n"
        );

        CtClass<?> clone = SpoonUtils.cloneClass(
            scenario.child.getFactory(),
            scenario.child,
            "smoke",
            "smoke",
            "SubjectTest",
            "_SubjectTest_Generalized_inherited_1_Test",
            "smoke.SubjectTest",
            "smoke._SubjectTest_Generalized_inherited_1_Test"
        );
        clone.getMethodsByName("setUp").get(0).addAnnotation(scenario.child.getFactory().Core().createAnnotation());
        CtCompilationUnit cu = scenario.child.getFactory().CompilationUnit().getOrCreate("Generated.java");
        List<CtImport> imports = SpoonUtils.importsForGeneratedClass(clone);
        cu.setImports(imports);
        cu.setDeclaredTypes(Collections.singletonList(clone));

        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(scenario.child.getFactory().getEnvironment());
        printer.setIgnoreImplicit(false);
        printer.calculate(cu, Collections.singletonList(clone));
        String source = printer.getResult();

        Assert.assertEquals(1, occurrences(source, "import org.junit.Before;"));
        Assert.assertEquals(1, occurrences(source, "import org.junit.Test;"));
        Assert.assertEquals(1, occurrences(source, "import static org.junit.Assert.assertEquals;"));
    }

    private static Set<String> annotationNames(CtMethod<?> method) {
        return method.getAnnotations().stream()
            .map(annotation -> annotation.getType().getSimpleName())
            .collect(Collectors.toSet());
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            count++;
            index = source.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static Scenario scenario(String parentSource, String childSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(parentSource, Paths.get(System.getProperty("user.dir"), "AbstractBase.java").toString()));
        launcher.addInputResource(new VirtualFile(childSource, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> child = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        return new Scenario(child);
    }

    private static final class Scenario {
        private final CtClass<?> child;

        private Scenario(CtClass<?> child) {
            this.child = child;
        }
    }
}
