package teralizer.processing.task;

import java.lang.reflect.Method;
import java.util.List;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.processing.ProcessingStage;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.TestAnalysis;

public class JpfInstrumentationTaskTest {

    @Example
    void expressionRecipeWrapperReturnsRewrittenOracleExpression() throws Exception {
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
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtExpression<?> oracleExpression = assertion.getArguments().get(0);
        CtInvocation<?> testedCall = oracleExpression.getElements(CtInvocation.class::isInstance).stream()
            .map(invocation -> (CtInvocation<?>) invocation)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals("intCompare"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        CtMethod<?> testedMethod = (CtMethod<?>) testedCall.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            oracleExpression,
            inputs,
            oracleExpression.getType().getQualifiedName()
        );
        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(
            testMethod,
            testMethod.getFactory().getModel().getRootPackage()
        );

        CtMethod<?> wrapper = createExpressionInstrumentedMethod(testMethod, resolved, "expr_wrapper", "boolean");

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
    void expressionRecipeWrapperRewritesEveryCompositeSiteFromRecipePaths() throws Exception {
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
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtExpression<?> oracleExpression = assertion.getArguments().get(0);
        CtInvocation<?> testedCall = oracleExpression.getElements(CtInvocation.class::isInstance).stream()
            .map(invocation -> (CtInvocation<?>) invocation)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals("intCompare"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        CtMethod<?> testedMethod = (CtMethod<?>) testedCall.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            oracleExpression,
            inputs,
            oracleExpression.getType().getQualifiedName()
        );
        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(
            testMethod,
            testMethod.getFactory().getModel().getRootPackage()
        );

        CtMethod<?> wrapper = createExpressionInstrumentedMethod(testMethod, resolved, "expr_wrapper", "boolean");

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
    void receiverlessStaticInvocationRecipeUsesPathBasedWrapperShape() throws Exception {
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

        CtMethod<?> wrapper = createInvocationInstrumentedMethod(testMethod, testedMethod, testedCall, inputs, "call_wrapper", "long");

        Assert.assertEquals("long", wrapper.getType().getQualifiedName());
        Assert.assertEquals(2, wrapper.getParameters().size());
        Assert.assertEquals("left", wrapper.getParameters().get(0).getSimpleName());
        Assert.assertEquals("right", wrapper.getParameters().get(1).getSimpleName());
        String statement = wrapper.getBody().getStatement(0).toString();
        Assert.assertTrue(statement, statement.contains("add(left, right)"));
    }

    private static CtMethod<?> createExpressionInstrumentedMethod(
        CtMethod<?> testMethod,
        GeneralizationRecipe.Resolved recipe,
        String methodName,
        String oracleExpressionType
    ) throws Exception {
        JpfInstrumentationTask task = task(methodName);
        Method method = JpfInstrumentationTask.class.getDeclaredMethod(
            "createInstrumentedMethod",
            Factory.class,
            CtClass.class,
            GeneralizationRecipe.Resolved.class,
            String.class
        );
        method.setAccessible(true);
        return (CtMethod<?>) method.invoke(
            task,
            testMethod.getFactory(),
            testMethod.getParent(CtClass.class),
            recipe,
            oracleExpressionType
        );
    }

    private static CtMethod<?> createInvocationInstrumentedMethod(
        CtMethod<?> testMethod,
        CtMethod<?> testedMethod,
        CtInvocation<?> testedCall,
        List<GeneralizableInput> inputs,
        String methodName,
        String oracleExpressionType
    ) throws Exception {
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            testedCall,
            inputs,
            oracleExpressionType
        );
        return createExpressionInstrumentedMethod(
            testMethod,
            recipe.resolveAgainst(testMethod, testMethod.getFactory().getModel().getRootPackage()),
            methodName,
            oracleExpressionType
        );
    }

    private static JpfInstrumentationTask task(String methodName) {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        AssertionRecord assertion = new AssertionRecord();
        assertion.setInstrumentedMethodName(methodName);
        assertion.setTestedClassQualifiedName("smoke.ExpressionSliceCut");
        return new JpfInstrumentationTask(ProcessingStage.ADD_JPF_INSTRUMENTATION, project, null, assertion);
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
