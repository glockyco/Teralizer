package teralizer.spoon.analysis;

import java.nio.file.Paths;
import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class GeneralizableInputExpressionTest {
    @Example
    void derivesSitesFromSupportedLiteralArgumentsInsideCompositeExpression() {
        CtExpression<?> expression = actualExpressionFromAssertion(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  static int intCompare(int site0, int site1) { return java.lang.Integer.compare(site0, site1); }\n"
                + "  @org.junit.Test public void t() {\n"
                + "    assertTrue(intCompare(4, 1) > 0);\n"
                + "  }\n"
                + "}\n"
        );

        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(expression);

        Assert.assertEquals(2, inputs.size());
        assertExpressionSite(inputs.get(0), "site0", "int", "4");
        assertExpressionSite(inputs.get(1), "site1", "int", "1");
    }

    @Example
    void doesNotLiftOperatorLiteralOperands() {
        CtExpression<?> expression = actualExpressionFromAssertion(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  static int intCompare(int site0, int site1) { return java.lang.Integer.compare(site0, site1); }\n"
                + "  @org.junit.Test public void t() {\n"
                + "    assertTrue(intCompare(4, 1) > 0);\n"
                + "  }\n"
                + "}\n"
        );

        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(expression);

        Assert.assertEquals(2, inputs.size());
        Assert.assertNotEquals("0", inputs.get(0).toMethodArgument().getValue());
        Assert.assertNotEquals("0", inputs.get(1).toMethodArgument().getValue());
    }

    @Example
    void derivesSitesFromConstructorArgumentsInsideCompositeExpression() {
        CtExpression<?> expression = actualExpressionFromAssertion(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  static final class Pair {\n"
                + "    Pair(int value) {}\n"
                + "    int compareTo(Pair other) { return 0; }\n"
                + "  }\n"
                + "  @org.junit.Test public void t() {\n"
                + "    assertTrue(new Pair(2).compareTo(new Pair(5)) < 0);\n"
                + "  }\n"
                + "}\n"
        );

        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(expression);

        Assert.assertEquals(2, inputs.size());
        assertExpressionSite(inputs.get(0), "site0", "int", "2");
        assertExpressionSite(inputs.get(1), "site1", "int", "5");
    }

    @Example
    void loneInvocationMatchesMethodArgumentDerivationShape() {
        CtExpression<?> expression = actualExpressionFromAssertion(
            "package smoke;\n"
                + "import static org.junit.Assert.assertEquals;\n"
                + "public class SubjectTest {\n"
                + "  static int intCompare(int site0, int site1) { return java.lang.Integer.compare(site0, site1); }\n"
                + "  @org.junit.Test public void t() {\n"
                + "    assertEquals(1, intCompare(4, 1));\n"
                + "  }\n"
                + "}\n"
        );
        CtInvocation<?> invocation = (CtInvocation<?>) expression;
        CtMethod<?> testedMethod = (CtMethod<?>) invocation.getExecutable().getDeclaration();

        List<GeneralizableInput> expressionInputs = GeneralizableInput.deriveFromExpression(expression);
        List<GeneralizableInput> methodInputs = GeneralizableInput.derive(testedMethod, invocation);

        Assert.assertEquals(methodInputs.size(), expressionInputs.size());
        for (int i = 0; i < methodInputs.size(); i++) {
            Assert.assertEquals(methodInputs.get(i).toMethodParameter().getName(), expressionInputs.get(i).toMethodParameter().getName());
            Assert.assertEquals(methodInputs.get(i).toMethodParameter().getType(), expressionInputs.get(i).toMethodParameter().getType());
            Assert.assertEquals(methodInputs.get(i).toMethodArgument().getValue(), expressionInputs.get(i).toMethodArgument().getValue());
        }
    }

    @Example
    void skipsUnsupportedLiteralArguments() {
        CtExpression<?> expression = actualExpressionFromAssertion(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  static int takeObject(Object value) { return 1; }\n"
                + "  @org.junit.Test public void t() {\n"
                + "    assertTrue(takeObject(null) > 0);\n"
                + "  }\n"
                + "}\n"
        );

        Assert.assertTrue(GeneralizableInput.deriveFromExpression(expression).isEmpty());
    }

    private static void assertExpressionSite(GeneralizableInput input, String name, String type, String value) {
        Assert.assertTrue(input.isExpressionSite());
        Assert.assertEquals(GeneralizationRecipe.InputKind.EXPRESSION_SITE, input.getKind());
        Assert.assertEquals(name, input.toMethodParameter().getName());
        Assert.assertEquals(type, input.toMethodParameter().getType());
        Assert.assertEquals(value, input.toMethodArgument().getValue());
        Assert.assertEquals(value, input.getSourceExpression().toString());
    }

    private static CtExpression<?> actualExpressionFromAssertion(String source) {
        CtInvocation<?> assertion = assertionFromSource(source);
        int actualIndex = TestAnalysis.getActualParameterIndex(assertion).get();
        return assertion.getArguments().get(actualIndex);
    }

    private static CtInvocation<?> assertionFromSource(String source) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        return TestAnalysis.findAllAsserts(testMethod).get(0);
    }
}
