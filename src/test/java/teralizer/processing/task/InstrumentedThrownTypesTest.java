package teralizer.processing.task;

import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.compiler.VirtualFile;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.codegen.InstrumentedClassBuilder;

public class InstrumentedThrownTypesTest {

    private static final String CONVERTER_SOURCE =
        "public class Converter {\n"
        + "  public Object convertTo(java.net.URL url) { return url; }\n"
        + "}";

    @Example
    void wrapperDeclaresCheckedExceptionsOfLiftedConstructor() {
        // new java.net.URL(String) throws MalformedURLException. When the receiver/argument
        // constructor is cloned into the instrumented wrapper, the wrapper must declare that
        // exception or BUILD_PROJECT_INSTRUMENTED fails (spike regression: JadConfig URLConverter).
        CtInvocation<?> testedCall = testedCallFrom(
            "public class ConverterTest {\n"
            + "  public void t() throws java.net.MalformedURLException {\n"
            + "    Converter c = new Converter();\n"
            + "    org.junit.Assert.assertEquals(\"x\", c.convertTo(new java.net.URL(\"http://x\")));\n"
            + "  }\n"
            + "}");

        Set<CtTypeReference<? extends Throwable>> thrown =
            InstrumentedClassBuilder.collectThrownTypes(
                (CtMethod<?>) testedCall.getExecutable().getDeclaration(), testedCall);

        Assert.assertTrue("wrapper must declare MalformedURLException from the lifted ctor",
            thrown.stream().anyMatch(t -> "java.net.MalformedURLException".equals(t.getQualifiedName())));
    }

    @Example
    void wrapperKeepsTestedMethodThrows() {
        CtInvocation<?> testedCall = testedCallFrom(
            "public class ThrowerTest {\n"
            + "  public void t() throws java.io.IOException {\n"
            + "    org.junit.Assert.assertEquals(1, new Thrower().read(3));\n"
            + "  }\n"
            + "}",
            "public class Thrower {\n"
            + "  public int read(int n) throws java.io.IOException { return n; }\n"
            + "}");

        Set<CtTypeReference<? extends Throwable>> thrown =
            InstrumentedClassBuilder.collectThrownTypes(
                (CtMethod<?>) testedCall.getExecutable().getDeclaration(), testedCall);

        Assert.assertTrue("tested method's own throws must be preserved",
            thrown.stream().anyMatch(t -> "java.io.IOException".equals(t.getQualifiedName())));
    }

    private static String fileNameFor(String source) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("public class (\\w+)").matcher(source);
        if (!m.find()) {
            throw new IllegalArgumentException("no public class in snippet");
        }
        return m.group(1) + ".java";
    }

    private static CtInvocation<?> testedCallFrom(String testSource, String... otherSources) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(false);
        launcher.addInputResource(new VirtualFile(testSource, fileNameFor(testSource)));
        launcher.addInputResource(new VirtualFile(CONVERTER_SOURCE, "Converter.java"));
        for (String other : otherSources) {
            launcher.addInputResource(new VirtualFile(other, fileNameFor(other)));
        }
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(CtClass.class::isInstance).stream()
            .map(e -> (CtClass<?>) e)
            .filter(c -> c.getSimpleName().endsWith("Test"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        return (CtInvocation<?>) assertion.getArguments().get(1);
    }
}
