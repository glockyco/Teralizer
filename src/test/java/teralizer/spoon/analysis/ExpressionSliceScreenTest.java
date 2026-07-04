package teralizer.spoon.analysis;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class ExpressionSliceScreenTest {
    @Example
    void admitsSelfContainedExpressions() {
        assertSelfContained("intLiteral");
        assertSelfContained("staticCallWithLiteralArgs");
        assertSelfContained("constructorCall");
        assertSelfContained("binaryOperatorOverStaticCalls");
        assertSelfContained("unaryNegationOfStaticCall");
        assertSelfContained("chainedInstanceCallRootedAtStaticFactory");
    }

    @Example
    void admitsCastWrappedStaticCallStoredAsTypeCastOnInnerExpression() {
        CtExpression<?> expression = actualExpression("castWrappedStaticCall");

        Assert.assertTrue(expression instanceof CtInvocation<?>);
        Assert.assertFalse(expression.getTypeCasts().isEmpty());
        Assert.assertTrue(ExpressionSliceScreen.isSelfContained(expression));
    }

    @Example
    void rejectsExpressionsThatReadExternalStateOrNeedStatements() {
        assertNotSelfContained("variableRead");
        assertNotSelfContained("fieldRead");
        assertNotSelfContained("arrayAccess");
        assertNotSelfContained("lambdaExpression");
        assertNotSelfContained("instanceCallRootedAtLocalVariable");
        assertNotSelfContained("anonymousClassExpression");
        assertNotSelfContained("assignmentExpression");
    }

    @Example
    void rejectsAnonymousClassConstructorCalls() {
        CtExpression<?> expression = actualExpression("anonymousClassExpression");

        Assert.assertTrue(expression instanceof CtNewClass<?>);
        Assert.assertFalse(ExpressionSliceScreen.isSelfContained(expression));
    }

    private static void assertSelfContained(String methodName) {
        Assert.assertTrue(methodName, ExpressionSliceScreen.isSelfContained(actualExpression(methodName)));
    }

    private static void assertNotSelfContained(String methodName) {
        Assert.assertFalse(methodName, ExpressionSliceScreen.isSelfContained(actualExpression(methodName)));
    }

    private static CtExpression<?> actualExpression(String methodName) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(SOURCE, "SubjectTest.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName(methodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        int actualIndex = TestAnalysis.getActualParameterIndex(assertion).get();
        return assertion.getArguments().get(actualIndex);
    }

    private static final String SOURCE = ""
        + "package smoke;\n"
        + "import static org.junit.Assert.assertEquals;\n"
        + "import static org.junit.Assert.assertTrue;\n"
        + "public class SubjectTest {\n"
        + "  private int x = 1;\n"
        + "  public static final class P {\n"
        + "    public P(int left, int right) {}\n"
        + "  }\n"
        + "  public static final class Box {\n"
        + "    private final int value;\n"
        + "    private Box(int value) { this.value = value; }\n"
        + "    public int value() { return value; }\n"
        + "  }\n"
        + "  public static final class Helper {\n"
        + "    public static int f(int left, int right) { return left + right; }\n"
        + "    public static int g(int value) { return value; }\n"
        + "    public static Box of(int value) { return new Box(value); }\n"
        + "  }\n"
        + "  public static final class Comparator {\n"
        + "    public int compare(int left, int right) { return left - right; }\n"
        + "  }\n"
        + "  public void intLiteral() {\n"
        + "    assertEquals(1, 1);\n"
        + "  }\n"
        + "  public void staticCallWithLiteralArgs() {\n"
        + "    assertEquals(3, Helper.f(1, 2));\n"
        + "  }\n"
        + "  public void constructorCall() {\n"
        + "    assertEquals(new P(1, 2), new P(1, 2));\n"
        + "  }\n"
        + "  public void binaryOperatorOverStaticCalls() {\n"
        + "    assertTrue(Helper.f(1, 2) > Helper.g(3));\n"
        + "  }\n"
        + "  public void unaryNegationOfStaticCall() {\n"
        + "    assertEquals(-3, -Helper.f(1, 2));\n"
        + "  }\n"
        + "  public void castWrappedStaticCall() {\n"
        + "    assertEquals(3L, (long) Helper.f(1, 2));\n"
        + "  }\n"
        + "  public void chainedInstanceCallRootedAtStaticFactory() {\n"
        + "    assertEquals(5, Helper.of(5).value());\n"
        + "  }\n"
        + "  public void variableRead() {\n"
        + "    int value = 1;\n"
        + "    assertEquals(1, value);\n"
        + "  }\n"
        + "  public void fieldRead() {\n"
        + "    assertEquals(1, this.x);\n"
        + "  }\n"
        + "  public void arrayAccess() {\n"
        + "    int[] values = new int[] {1};\n"
        + "    assertEquals(1, values[0]);\n"
        + "  }\n"
        + "  public void lambdaExpression() {\n"
        + "    assertEquals((java.util.function.IntSupplier) null, (java.util.function.IntSupplier) () -> 1);\n"
        + "  }\n"
        + "  public void instanceCallRootedAtLocalVariable() {\n"
        + "    Comparator c = new Comparator();\n"
        + "    int a = 1;\n"
        + "    int b = 2;\n"
        + "    assertEquals(-1, c.compare(a, b));\n"
        + "  }\n"
        + "  public void anonymousClassExpression() {\n"
        + "    assertEquals((java.util.function.IntSupplier) null, new java.util.function.IntSupplier() {\n"
        + "      public int getAsInt() { return 1; }\n"
        + "    });\n"
        + "  }\n"
        + "  public void assignmentExpression() {\n"
        + "    int value = 0;\n"
        + "    assertEquals(3, value = 3);\n"
        + "  }\n"
        + "}\n";
}
