package teralizer.spoon.analysis;

import com.google.gson.Gson;
import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class GeneralizationRecipeTest {
    @Example
    void usesSchemaVersionThreeAndRejectsOlderPayloads() {
        Assert.assertEquals(3, GeneralizationRecipe.CURRENT_VERSION);
        for (int version : new int[] {1, 2}) {
            String oldJson = "{"
                + "\"version\":" + version + ","
                + "\"schema\":\"teralizer.generalization.recipe\","
                + "\"oracleExpressionPath\":\"#statement[index=0]\","
                + "\"oracleMethodPath\":\"#type[name=smoke.SubjectTest]\","
                + "\"oracleType\":\"boolean\","
                + "\"oracleExpressionType\":\"boolean\","
                + "\"inputSites\":[]"
                + "}";

            try {
                GeneralizationRecipe.fromJson(new Gson(), oldJson);
                Assert.fail("older recipes must be rejected");
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains("Unsupported generalization recipe schema/version"));
            }
        }
    }

    @Example
    void schemaVersionThreePersistsSitesWithoutLegacyIndexes() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesConstructorArgument");
        String json = recipeFor(scenario).toJson(new Gson());

        Assert.assertTrue(json.contains("\"kind\":\"RECEIVER_CTOR_ARG\""));
        Assert.assertTrue(json.contains("\"path\":\""));
        Assert.assertFalse(json, json.contains("method" + "Argument" + "Index"));
        Assert.assertFalse(json, json.contains("constructor" + "Argument" + "Index"));
    }

    @Example
    void roundTripsRecipeJsonWithoutLosingSites() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesConstructorArgument");
        GeneralizationRecipe recipe = recipeFor(scenario);

        GeneralizationRecipe roundTripped = GeneralizationRecipe.fromJson(new Gson(), recipe.toJson(new Gson()));

        Assert.assertEquals(GeneralizationRecipe.CURRENT_VERSION, roundTripped.getVersion());
        Assert.assertEquals(recipe.getOracleExpressionPath(), roundTripped.getOracleExpressionPath());
        Assert.assertEquals(recipe.getOracleType(), roundTripped.getOracleType());
        Assert.assertEquals(3, roundTripped.getInputSites().size());
        Assert.assertEquals(GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG, roundTripped.getInputSites().get(0).getKind());
        Assert.assertEquals("_ctor_receiver_zero_lower", roundTripped.getInputSites().get(0).getName());
        Assert.assertEquals("int", roundTripped.getInputSites().get(0).getType());
        Assert.assertEquals(GeneralizationRecipe.InputKind.RECEIVER_CTOR_ARG, roundTripped.getInputSites().get(1).getKind());
        Assert.assertEquals("_ctor_receiver_one_upper", roundTripped.getInputSites().get(1).getName());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, roundTripped.getInputSites().get(2).getKind());
        Assert.assertEquals("value", roundTripped.getInputSites().get(2).getName());
    }
    @Example
    void roundTripsExpressionOracleType() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesBinaryOperator");
        CtExpression<?> oracleExpression = actualAssertExpression(scenario.testMethod);
        List<GeneralizableInput> inputs = GeneralizableInput.derive(scenario.oracleMethod, scenario.oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(scenario.oracleMethod, oracleExpression, inputs, "boolean");

        GeneralizationRecipe roundTripped = GeneralizationRecipe.fromJson(new Gson(), recipe.toJson(new Gson()));

        Assert.assertEquals("boolean", roundTripped.getOracleExpressionType());
        Assert.assertEquals(recipe.getOracleExpressionPath(), roundTripped.getOracleExpressionPath());
        Assert.assertEquals(recipe.getOracleType(), roundTripped.getOracleType());
    }

    @Example
    void roundTripsNullExpressionOracleType() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesBinaryOperator");
        CtExpression<?> oracleExpression = actualAssertExpression(scenario.testMethod);
        List<GeneralizableInput> inputs = GeneralizableInput.derive(scenario.oracleMethod, scenario.oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(scenario.oracleMethod, oracleExpression, inputs, null);

        GeneralizationRecipe roundTripped = GeneralizationRecipe.fromJson(new Gson(), recipe.toJson(new Gson()));

        Assert.assertNull(roundTripped.getOracleExpressionType());
        Assert.assertEquals(recipe.getOracleExpressionPath(), roundTripped.getOracleExpressionPath());
        Assert.assertEquals(recipe.getOracleType(), roundTripped.getOracleType());
    }

    @Example
    void roundTripsExpressionInputSiteKind() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesBinaryOperator");
        CtExpression<?> oracleExpression = actualAssertExpression(scenario.testMethod);
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(scenario.oracleMethod, oracleExpression, inputs, "boolean");

        String json = recipe.toJson(new Gson());
        GeneralizationRecipe roundTripped = GeneralizationRecipe.fromJson(new Gson(), json);

        Assert.assertTrue(json.contains("\"kind\":\"EXPRESSION_SITE\""));
        Assert.assertEquals(inputs.size(), roundTripped.getInputSites().size());
        for (GeneralizationRecipe.InputSite site : roundTripped.getInputSites()) {
            Assert.assertEquals(GeneralizationRecipe.InputKind.EXPRESSION_SITE, site.getKind());
        }
    }


    @Example
    void resolvesRecipeSitesAgainstTheOriginalModel() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesReceiverConstructor");
        GeneralizationRecipe recipe = recipeFor(scenario);

        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(scenario.testMethod, scenario.model.getRootPackage());

        Assert.assertEquals("size", ((CtInvocation<?>) resolved.getOracleExpression()).getExecutable().getSimpleName());
        Assert.assertEquals("size", resolved.getOracleMethod().getSimpleName());
        Assert.assertEquals(2, resolved.getInputs().size());
        Assert.assertTrue(resolved.getInputs().get(0).isReceiverConstructorArgument());
        Assert.assertEquals("1.0", resolved.getInputs().get(0).getSourceExpression().toString());
        Assert.assertEquals("10.0", resolved.getInputs().get(1).getSourceExpression().toString());
    }
    @Example
    void resolvesBinaryOperatorOracleAsExpression() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesBinaryOperator");
        CtExpression<?> oracleExpression = actualAssertExpression(scenario.testMethod);
        Assert.assertTrue(oracleExpression instanceof CtBinaryOperator);
        List<GeneralizableInput> inputs = GeneralizableInput.derive(scenario.oracleMethod, scenario.oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(scenario.oracleMethod, oracleExpression, inputs, "boolean");

        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(scenario.testMethod, scenario.model.getRootPackage());

        Assert.assertTrue(resolved.getOracleExpression() instanceof CtExpression);
        Assert.assertTrue(resolved.getOracleExpression() instanceof CtBinaryOperator);
    }

    @Example
    void resolvedRewritesExpressionSitesInsideClonedExpressionAndMethod() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesBinaryOperator");
        CtExpression<?> oracleExpression = actualAssertExpression(scenario.testMethod);
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(scenario.oracleMethod, oracleExpression, inputs, "boolean");
        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(scenario.testMethod, scenario.model.getRootPackage());

        CtExpression<?> expressionClone = resolved.getOracleExpression().clone();
        resolved.replaceInputSitesWithParameterReads(
            expressionClone,
            scenario.testMethod.getFactory(),
            input -> input.toMethodParameter().getName()
        );
        String expressionText = expressionClone.toString();
        Assert.assertTrue(expressionText, expressionText.contains("site0"));
        Assert.assertTrue(expressionText, expressionText.contains("site5"));
        Assert.assertFalse(expressionText, expressionText.contains("contains(5)"));

        CtMethod<?> methodClone = scenario.testMethod.clone();
        resolved.replaceInputSitesWithParameterReads(
            methodClone,
            scenario.testMethod.getFactory(),
            input -> "_p_." + input.toMethodParameter().getName()
        );
        CtExpression<?> rewrittenAssertExpression = actualAssertExpression(methodClone);
        String methodText = rewrittenAssertExpression.toString();
        Assert.assertTrue(methodText, methodText.contains("_p_.site0"));
        Assert.assertTrue(methodText, methodText.contains("_p_.site5"));
        Assert.assertFalse(methodText, methodText.contains("contains(6)"));
    }


    @Example
    void rewritesOnlyThePersistedClassQualifiedNameInRecipePaths() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesConstructorArgument");
        GeneralizationRecipe recipe = recipeFor(scenario);

        GeneralizationRecipe rewritten = recipe.rewriteForClone("smoke.SubjectTest", "smoke.SubjectProperty");

        Assert.assertEquals(recipe.getOracleExpressionPath(), rewritten.getOracleExpressionPath());
        Assert.assertEquals(
            "#type[name=smoke.SubjectProperty]",
            GeneralizationRecipe.rewriteCtPathForClone(
                "#type[name=smoke.SubjectTest]",
                "smoke.SubjectTest",
                "smoke.SubjectProperty"
            )
        );
        Assert.assertEquals(recipe.getInputSites().get(0).getPath(), rewritten.getInputSites().get(0).getPath());
    }

    @Example
    void staleRecipePathFailsWithTypedResolutionError() {
        Scenario scenario = scenarioFromSource(SOURCE, "usesConstructorArgument");
        GeneralizationRecipe stale = recipeFor(scenario).withOracleExpressionPath("#type[name=smoke.SubjectTest]#method[name=missing]");

        try {
            stale.resolveAgainst(scenario.testMethod, scenario.model.getRootPackage());
            Assert.fail("stale recipe path should fail during analysis-time validation");
        } catch (GeneralizationRecipe.ResolutionException expected) {
            Assert.assertEquals(GeneralizationRecipe.PathRole.ORACLE_EXPRESSION, expected.getRole());
            Assert.assertTrue(expected.getMessage().contains("missing"));
        }
    }

    private static GeneralizationRecipe recipeFor(Scenario scenario) {
        List<GeneralizableInput> inputs = GeneralizableInput.derive(scenario.oracleMethod, scenario.oracleExpression);
        return GeneralizationRecipe.from(scenario.oracleMethod, scenario.oracleExpression, inputs, scenario.oracleMethod.getType().getQualifiedName());
    }

    private static CtExpression<?> actualAssertExpression(CtMethod<?> testMethod) {
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        int actualIndex = TestAnalysis.getActualParameterIndex(assertion).get();
        return assertion.getArguments().get(actualIndex);
    }

    private static Scenario scenarioFromSource(String source, String methodName) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, "SubjectTest.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName(methodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtInvocation<?> testedCall = assertion.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> !invocation.equals(assertion))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        return new Scenario(model, testMethod, testedCall, (CtMethod<?>) testedCall.getExecutable().getDeclaration());
    }

    private static final class Scenario {
        private final CtModel model;
        private final CtMethod<?> testMethod;
        private final CtInvocation<?> oracleExpression;
        private final CtMethod<?> oracleMethod;

        private Scenario(CtModel model, CtMethod<?> testMethod, CtInvocation<?> oracleExpression, CtMethod<?> oracleMethod) {
            this.model = model;
            this.testMethod = testMethod;
            this.oracleExpression = oracleExpression;
            this.oracleMethod = oracleMethod;
        }
    }

    private static final String SOURCE = ""
        + "package smoke;\n"
        + "import static org.junit.Assert.assertEquals;\n"
        + "import static org.junit.Assert.assertTrue;\n"
        + "public class SubjectTest {\n"
        + "  public static final class Interval {\n"
        + "    private final int lower;\n"
        + "    private final int upper;\n"
        + "    public Interval(int lower, int upper) { this.lower = lower; this.upper = upper; }\n"
        + "    public boolean contains(int value) { return lower <= value && value <= upper; }\n"
        + "    public double size() { return upper - lower; }\n"
        + "  }\n"
        + "  public void usesConstructorArgument() {\n"
        + "    assertEquals(true, new Interval(1, 10).contains(5));\n"
        + "  }\n"
        + "  public void usesReceiverConstructor() {\n"
        + "    assertEquals(9.0, new Interval(1.0, 10.0).size(), 0.0);\n"
        + "  }\n"
        + "  public void usesBinaryOperator() {\n"
        + "    assertTrue(new Interval(1, 10).contains(5) && new Interval(2, 8).contains(6));\n"
        + "  }\n"
        + "}\n";
}
