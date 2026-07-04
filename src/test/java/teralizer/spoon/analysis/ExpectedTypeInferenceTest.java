package teralizer.spoon.analysis;

import java.nio.file.Paths;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class ExpectedTypeInferenceTest {
    @Example
    void infersAssertionOverloadTypeForPlainCallActual() {
        CtInvocation<?> call = actualCallFrom(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  static int add(int left, int right) { return left + right; }\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(7, add(3, 4));\n"
                + "  }\n"
                + "}\n"
        );

        CtTypeReference<?> inferred = ExpectedTypeInference.inferExpectedType(call);

        Assert.assertEquals("long", inferred.getQualifiedName());
    }

    @Example
    void erasesTypeParametersToObject() {
        CtInvocation<?> call = actualCallFrom(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  static <T> T id(T value) { return value; }\n"
                + "  public void t() {\n"
                + "    Object value = id(\"x\");\n"
                + "    org.junit.Assert.assertNotNull(value);\n"
                + "  }\n"
                + "}\n"
        );

        CtTypeReference<?> inferred = ExpectedTypeInference.inferExpectedType(call);

        Assert.assertEquals("java.lang.Object", inferred.getQualifiedName());
    }

    private static CtInvocation<?> actualCallFrom(String source) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        return testMethod.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> !invocation.getExecutable().getSimpleName().startsWith("assert"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
    }
}
