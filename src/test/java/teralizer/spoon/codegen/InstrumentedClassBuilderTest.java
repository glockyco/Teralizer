package teralizer.spoon.codegen;

import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.TestAnalysis;

public class InstrumentedClassBuilderTest {

    @Example
    void compositeRecipeWrapperReturnsRewrittenOracleExpression() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    assertTrue(ExpressionSliceCut.intCompare(4, 1) > 0);\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class ExpressionSliceCut {\n"
                + "  public static int intCompare(int left, int right) { return java.lang.Integer.compare(left, right); }\n"
                + "}\n"
        );
        GeneralizationRecipe recipe = compositeOracleRecipe(testMethod);

        CtClass<?> instrumented = build(testMethod, recipe, "expr_wrapper", "boolean");
        CtMethod<?> wrapper = instrumented.getMethodsByName("expr_wrapper").get(0);

        Assert.assertEquals("boolean", wrapper.getType().getQualifiedName());
        Assert.assertEquals(2, wrapper.getParameters().size());
        Assert.assertEquals("int", wrapper.getParameters().get(0).getType().getQualifiedName());
        Assert.assertEquals("site0", wrapper.getParameters().get(0).getSimpleName());
        Assert.assertEquals("int", wrapper.getParameters().get(1).getType().getQualifiedName());
        Assert.assertEquals("site1", wrapper.getParameters().get(1).getSimpleName());

        CtReturn<?> statement = (CtReturn<?>) wrapper.getBody().getStatement(0);
        Assert.assertTrue(statement.getReturnedExpression() instanceof CtBinaryOperator<?>);
        CtBinaryOperator<?> returned = (CtBinaryOperator<?>) statement.getReturnedExpression();
        Assert.assertEquals("0", returned.getRightHandOperand().toString());
        Assert.assertTrue(returned.getLeftHandOperand() instanceof CtInvocation<?>);
        CtInvocation<?> returnedCall = (CtInvocation<?>) returned.getLeftHandOperand();
        Assert.assertEquals("intCompare", returnedCall.getExecutable().getSimpleName());
        Assert.assertEquals("site0", returnedCall.getArguments().get(0).toString());
        Assert.assertEquals("site1", returnedCall.getArguments().get(1).toString());
    }

    @Example
    void compositeRecipeWrapperRewritesEveryCompositeSiteFromRecipePaths() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    assertTrue(ExpressionSliceCut.intCompare(4, 1) > ExpressionSliceCut.intCompare(3, 2));\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class ExpressionSliceCut {\n"
                + "  public static int intCompare(int left, int right) { return java.lang.Integer.compare(left, right); }\n"
                + "}\n"
        );
        GeneralizationRecipe recipe = compositeOracleRecipe(testMethod);

        CtClass<?> instrumented = build(testMethod, recipe, "expr_wrapper", "boolean");
        CtMethod<?> wrapper = instrumented.getMethodsByName("expr_wrapper").get(0);

        Assert.assertEquals(4, wrapper.getParameters().size());
        CtReturn<?> statement = (CtReturn<?>) wrapper.getBody().getStatement(0);
        Assert.assertTrue(statement.getReturnedExpression() instanceof CtBinaryOperator<?>);
        CtBinaryOperator<?> returned = (CtBinaryOperator<?>) statement.getReturnedExpression();
        CtInvocation<?> leftCall = (CtInvocation<?>) returned.getLeftHandOperand();
        CtInvocation<?> rightCall = (CtInvocation<?>) returned.getRightHandOperand();
        Assert.assertEquals("site0", leftCall.getArguments().get(0).toString());
        Assert.assertEquals("site1", leftCall.getArguments().get(1).toString());
        Assert.assertEquals("site2", rightCall.getArguments().get(0).toString());
        Assert.assertEquals("site3", rightCall.getArguments().get(1).toString());
    }

    @Example
    void compositeInstanceRecipeKeepsReceiverInBodyAndCallSite() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    assertTrue(new Pair(2).compareTo(new Pair(5)) < 0);\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class ExpressionSliceCut { }\n"
                + "class Pair implements Comparable<Pair> {\n"
                + "  private final int value;\n"
                + "  Pair(int value) { this.value = value; }\n"
                + "  public int compareTo(Pair other) { return java.lang.Integer.compare(this.value, other.value); }\n"
                + "}\n"
        );
        GeneralizationRecipe recipe = compositeOracleRecipe(testMethod);

        CtClass<?> instrumented = build(testMethod, recipe, "expr_wrapper", "boolean");
        CtMethod<?> wrapper = instrumented.getMethodsByName("expr_wrapper").get(0);
        CtInvocation<?> wrapperCall = instrumented.getMethodsByName("t").get(0)
            .getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals("expr_wrapper"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);

        Assert.assertEquals(2, wrapper.getParameters().size());
        Assert.assertEquals("site0", wrapper.getParameters().get(0).getSimpleName());
        Assert.assertEquals("site1", wrapper.getParameters().get(1).getSimpleName());
        Assert.assertFalse(
            "composite recipes keep the receiver expression inside the wrapper instead of hoisting _target_",
            wrapper.toString().contains("_target_")
        );
        String body = wrapper.getBody().getStatement(0).toString();
        Assert.assertTrue(body, body.contains("new smoke.Pair(site0).compareTo(new smoke.Pair(site1))"));
        Assert.assertFalse(body, body.contains("new Pair(2)"));
        Assert.assertEquals(2, wrapperCall.getArguments().size());
        Assert.assertEquals("2", wrapperCall.getArguments().get(0).toString());
        Assert.assertEquals("5", wrapperCall.getArguments().get(1).toString());
    }

    @Example
    void plainInstanceCallRecipeHoistsReceiverTarget() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    Pair pair = new Pair(2);\n"
                + "    org.junit.Assert.assertEquals(1, pair.compareTo(new Pair(1)));\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class ExpressionSliceCut { }\n"
                + "class Pair implements Comparable<Pair> {\n"
                + "  private final int value;\n"
                + "  Pair(int value) { this.value = value; }\n"
                + "  public int compareTo(Pair other) { return java.lang.Integer.compare(this.value, other.value); }\n"
                + "}\n"
        );
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtInvocation<?> testedCall = (CtInvocation<?>) assertion.getArguments().get(1);
        CtMethod<?> testedMethod = (CtMethod<?>) testedCall.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedCall);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(testedMethod, testedCall, inputs, "long");

        CtClass<?> instrumented = build(testMethod, recipe, "call_wrapper", "long");
        CtMethod<?> wrapper = instrumented.getMethodsByName("call_wrapper").get(0);
        CtInvocation<?> wrapperCall = instrumented.getMethodsByName("t").get(0)
            .getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals("call_wrapper"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);

        Assert.assertTrue("plain instance-call recipes keep an explicit receiver parameter", wrapper.toString().contains("_target_"));
        Assert.assertEquals("_target_", wrapper.getParameters().get(0).getSimpleName());
        Assert.assertEquals("pair", wrapperCall.getArguments().get(0).toString());
    }

    @Example
    void receiverlessStaticInvocationRecipeUsesPathBasedWrapperShape() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(7, ExpressionSliceCut.add(3, 4));\n"
                + "  }\n"
                + "}\n",
            "package smoke;\n"
                + "public class ExpressionSliceCut {\n"
                + "  public static int add(int left, int right) { return left + right; }\n"
                + "}\n"
        );
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtInvocation<?> testedCall = (CtInvocation<?>) assertion.getArguments().get(1);
        CtMethod<?> testedMethod = (CtMethod<?>) testedCall.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedCall);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(testedMethod, testedCall, inputs, "long");

        CtClass<?> instrumented = build(testMethod, recipe, "call_wrapper", "long");
        CtMethod<?> wrapper = instrumented.getMethodsByName("call_wrapper").get(0);

        Assert.assertEquals("long", wrapper.getType().getQualifiedName());
        Assert.assertEquals(2, wrapper.getParameters().size());
        Assert.assertEquals("left", wrapper.getParameters().get(0).getSimpleName());
        Assert.assertEquals("right", wrapper.getParameters().get(1).getSimpleName());
        String statement = wrapper.getBody().getStatement(0).toString();
        Assert.assertTrue(statement, statement.contains("add(left, right)"));
    }

    private static GeneralizationRecipe compositeOracleRecipe(CtMethod<?> testMethod) {
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtExpression<?> oracleExpression = assertion.getArguments().get(0);
        CtInvocation<?> testedCall = oracleExpression.getElements(CtInvocation.class::isInstance).stream()
            .map(invocation -> (CtInvocation<?>) invocation)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals("intCompare")
                || invocation.getExecutable().getSimpleName().equals("compareTo"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        CtMethod<?> testedMethod = (CtMethod<?>) testedCall.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);
        return GeneralizationRecipe.from(testedMethod, oracleExpression, inputs, oracleExpression.getType().getQualifiedName());
    }

    private static CtClass<?> build(
        CtMethod<?> testMethod,
        GeneralizationRecipe recipe,
        String methodName,
        String oracleExpressionType
    ) {
        CtClass<?> sourceClass = testMethod.getParent(CtClass.class);
        InstrumentedClassBuilder.Names names = new InstrumentedClassBuilder.Names(
            "smoke",
            "smoke",
            "SubjectTest",
            "_SubjectTest_Instrumented_t_13_Test",
            "smoke.SubjectTest",
            "smoke._SubjectTest_Instrumented_t_13_Test",
            testMethod.getPath().relativePath(sourceClass).toString(),
            TestAnalysis.findAllAsserts(testMethod).get(0).getPath().relativePath(testMethod).toString(),
            methodName,
            "smoke.ExpressionSliceCut",
            oracleExpressionType
        );
        GeneralizationRecipe clonedRecipe = recipe.rewriteForClone(
            names.getSourceTestClassQualifiedName(),
            names.getInstrumentedClassQualifiedName()
        );
        return new InstrumentedClassBuilder().build(testMethod.getFactory(), clonedRecipe, names);
    }

    private static CtMethod<?> testMethodFromSource(String testSource, String cutSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        launcher.addInputResource(new VirtualFile(cutSource, "ExpressionSliceCut.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        return testClass.getMethodsByName("t").get(0);
    }
}
