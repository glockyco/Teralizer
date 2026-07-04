package teralizer.spoon.analysis;

import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class GeneralizableInputTest {
    @Example
    void derivesMethodArgumentInputsWithMethodKind() {
        CtInvocation<?> assertion = assertionFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class SubjectTest {\n"
                + "  public static final class Subject {\n"
                + "    public static int sum(int left, int right) { return left + right; }\n"
                + "  }\n"
                + "  @org.junit.Test public void valueInsideInterval() {\n"
                + "    assertEquals(7, Subject.sum(3, 4));\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> testedMethodCall = assertion.getArguments().get(1).filterChildren(CtInvocation.class::isInstance)
            .map(CtInvocation.class::cast)
            .first();
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        Assert.assertEquals(2, inputs.size());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, inputs.get(0).getKind());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, inputs.get(1).getKind());
        Assert.assertFalse(inputs.get(0).isConstructorArgument());
        Assert.assertFalse(inputs.get(1).isConstructorArgument());
    }
    @Example
    void derivesSyntheticInputsFromInlineConstructorArguments() {
        CtInvocation<?> assertion = assertionFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  public static final class Interval {\n"
                + "    private final int lower;\n"
                + "    private final int upper;\n"
                + "    public Interval(int lower, int upper) { this.lower = lower; this.upper = upper; }\n"
                + "    public boolean contains(int value) { return lower <= value && value <= upper; }\n"
                + "  }\n"
                + "  public static final class Subject {\n"
                + "    public static boolean contains(Interval interval, int value) { return interval.contains(value); }\n"
                + "  }\n"
                + "  @org.junit.Test public void valueInsideInterval() {\n"
                + "    assertTrue(Subject.contains(new Interval(1, 10), 5));\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> testedMethodCall = assertion.getArguments().get(0).filterChildren(CtInvocation.class::isInstance)
            .map(CtInvocation.class::cast)
            .first();
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        Assert.assertEquals(3, inputs.size());
        Assert.assertEquals("_ctor_interval_zero_lower", inputs.get(0).toMethodParameter().getName());
        Assert.assertEquals("int", inputs.get(0).toMethodParameter().getType());
        Assert.assertEquals("1", inputs.get(0).toMethodArgument().getValue());
        Assert.assertEquals("_ctor_interval_one_upper", inputs.get(1).toMethodParameter().getName());
        Assert.assertEquals("10", inputs.get(1).toMethodArgument().getValue());
        Assert.assertEquals(GeneralizationRecipe.InputKind.CTOR_ARG, inputs.get(0).getKind());
        Assert.assertEquals(GeneralizationRecipe.InputKind.CTOR_ARG, inputs.get(1).getKind());
        Assert.assertEquals("value", inputs.get(2).toMethodParameter().getName());
        Assert.assertEquals("5", inputs.get(2).toMethodArgument().getValue());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, inputs.get(2).getKind());
    }

    @Example
    void derivesSyntheticInputsFromInlineReceiverConstructorArguments() {
        CtInvocation<?> assertion = assertionFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class SubjectTest {\n"
                + "  public static final class Interval {\n"
                + "    private final double lower;\n"
                + "    private final double upper;\n"
                + "    public Interval(double lower, double upper) { this.lower = lower; this.upper = upper; }\n"
                + "    public double getSize() { return upper - lower; }\n"
                + "  }\n"
                + "  @org.junit.Test public void valueInsideInterval() {\n"
                + "    assertEquals(9.0, new Interval(1.0, 10.0).getSize(), 0.0);\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> testedMethodCall = assertion.getArguments().get(1).filterChildren(CtInvocation.class::isInstance)
            .map(CtInvocation.class::cast)
            .first();
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        Assert.assertEquals(2, inputs.size());
        Assert.assertEquals("_ctor_receiver_zero_lower", inputs.get(0).toMethodParameter().getName());
        Assert.assertEquals("double", inputs.get(0).toMethodParameter().getType());
        Assert.assertEquals("1.0", inputs.get(0).toMethodArgument().getValue());
        Assert.assertEquals("_ctor_receiver_one_upper", inputs.get(1).toMethodParameter().getName());
        Assert.assertEquals("10.0", inputs.get(1).toMethodArgument().getValue());
        Assert.assertTrue(inputs.get(0).isReceiverConstructorArgument());
        Assert.assertTrue(inputs.get(1).isReceiverConstructorArgument());
        Assert.assertEquals(GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG, inputs.get(0).getKind());
        Assert.assertEquals(GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG, inputs.get(1).getKind());
    }


    @Example
    void deriveSkipsEmptyVarargsTailWithoutThrowing() {
        CtInvocation<?> assertion = assertionFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class SubjectTest {\n"
                + "  public static final class Subject {\n"
                + "    public static int join(int... xs) { return xs.length; }\n"
                + "  }\n"
                + "  @org.junit.Test public void valueInsideInterval() {\n"
                + "    assertEquals(0, Subject.join());\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> testedMethodCall = assertion.getArguments().get(1).filterChildren(CtInvocation.class::isInstance)
            .map(CtInvocation.class::cast)
            .first();
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        Assert.assertTrue(inputs.isEmpty());
    }

    @Example
    void deriveSkipsExpandedVarargsWithoutThrowing() {
        CtInvocation<?> assertion = assertionFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class SubjectTest {\n"
                + "  public static final class Subject {\n"
                + "    public static int join(int... xs) { return xs.length; }\n"
                + "  }\n"
                + "  @org.junit.Test public void valueInsideInterval() {\n"
                + "    assertEquals(3, Subject.join(1, 2, 3));\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> testedMethodCall = assertion.getArguments().get(1).filterChildren(CtInvocation.class::isInstance)
            .map(CtInvocation.class::cast)
            .first();
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        Assert.assertTrue(inputs.isEmpty());
    }
    private static CtInvocation<?> assertionFromSource(String source) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, "SubjectTest.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("valueInsideInterval").get(0);
        return TestAnalysis.findAllAsserts(testMethod).get(0);
    }
}
