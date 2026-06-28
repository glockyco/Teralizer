package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class InputGenerationPlannerTest {
    @Example
    void normalizesSupportedTypeDomains() {
        Assert.assertEquals(TypeDomain.INTEGER, TypeDomain.from("int"));
        Assert.assertEquals(TypeDomain.INTEGER, TypeDomain.from("java.lang.Long"));
        Assert.assertEquals(TypeDomain.REAL, TypeDomain.from("double"));
        Assert.assertEquals(TypeDomain.REAL, TypeDomain.from("java.lang.Float"));
        Assert.assertEquals(TypeDomain.CHAR, TypeDomain.from("char"));
        Assert.assertEquals(TypeDomain.BOOLEAN, TypeDomain.from("java.lang.Boolean"));
        Assert.assertEquals(TypeDomain.STRING, TypeDomain.from("java.lang.String"));
        Assert.assertEquals(TypeDomain.ARRAY, TypeDomain.from("int[]"));
        Assert.assertEquals(TypeDomain.OBJECT, TypeDomain.from("org.example.Interval"));
    }

    @Example
    void createsPlanWithConsumedClauseForAtomicBound() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("int", "x"));
        Model inputModel = new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, inputModel);

        Assert.assertEquals(1, plan.getTotalConstraintCount());
        Assert.assertEquals(1, plan.getUsedConstraintCount());
        Assert.assertTrue(plan.hasConsumedClauses());
        Assert.assertFalse(plan.hasResidualClauses());
        Assert.assertEquals("true", plan.getResidualPredicate());
        Assert.assertEquals("(_p_.x > 0)", plan.getFullPredicate());
        Assert.assertTrue(plan.hasClauses());
        Assert.assertEquals(1, plan.getParameterPlans().size());
        Assert.assertEquals(TypeDomain.INTEGER, plan.getParameterPlans().get(0).getDomain());
        Assert.assertEquals("x", plan.getParameterPlans().get(0).getParameter().getName());
    }

    @Example
    void integerRecipeUsesPreviousParameterBounds() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("int", "a"),
            new MethodParameter("int", "b")
        );
        Model inputModel = new Operation(new VariableInteger("b"), Operator.GT, new VariableInteger("a"));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body.contains("bLowerBounds = java.util.Arrays.asList(bDefaultMin, (int) (a+1))"));
        Assert.assertTrue(body.contains("Arbitraries.integers().between(bMin, bMax)"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void realRecipeUsesPreviousParameterBounds() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("double", "a"),
            new MethodParameter("double", "b")
        );
        Model inputModel = new Operation(new VariableReal("b"), Operator.GT, new VariableReal("a"));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body.contains("bLowerBounds = java.util.Arrays.asList(bDefaultMin, (double) (a))"));
        Assert.assertTrue(body.contains("bLowerBoundIncluded = java.util.Arrays.asList(true, false)"));
        Assert.assertTrue(body.contains("Arbitraries.doubles().ofScale(bScale).between"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void firstConcreteValueIsPreservedInNumericRecipe() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("int", "x"));
        Model inputModel = new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(
            parameters,
            Collections.singletonMap("x", new MethodArgument("int", "7")),
            inputModel
        );
        String body = plan.getParameterPlans().get(0).getRecipe().emit();

        Assert.assertTrue(body.contains("new FirstValueArbitrary<Integer>((int) ("));
    }

    @Example
    void charRecipeUsesConstantBounds() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("char", "c"));
        Model inputModel = new Operation(new VariableInteger("c"), Operator.GT, new ConstantInteger(64));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(0).getRecipe().emit();

        Assert.assertTrue(body.contains("cLowerBounds = java.util.Arrays.asList(cDefaultMin, (char) (65.0))"));
        Assert.assertTrue(body.contains("Arbitraries.chars().range(cMin, cMax)"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void integerRecipeUsesAffineUpperBoundFromPreviousParameter() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("int", "a"),
            new MethodParameter("int", "b")
        );
        Model inputModel = new Operation(
            new Operation(new VariableInteger("a"), Operator.PLUS, new VariableInteger("b")),
            Operator.LT,
            new ConstantInteger(10)
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body, body.contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (int) (9 - a))"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void integerRecipeUsesAffineUpperBoundFromSubtraction() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("int", "a"),
            new MethodParameter("int", "b")
        );
        Model inputModel = new Operation(
            new Operation(new VariableInteger("a"), Operator.MINUS, new VariableInteger("b")),
            Operator.GT,
            new ConstantInteger(3)
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body, body.contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (int) (a - 4))"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void integerRecipeUsesAffineEqualityFromPreviousParameter() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("int", "a"),
            new MethodParameter("int", "b")
        );
        Model inputModel = new Operation(
            new VariableInteger("b"),
            Operator.EQ,
            new Operation(new VariableInteger("a"), Operator.PLUS, new ConstantInteger(1))
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body.contains("Arbitraries.just((int) (a + 1))"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void affineBoundIsCountedAsConsumedForTelemetry() {
        // b == a + 1: the numeric planner consumes this affine clause by construction.
        // The old VariableConstraintExtractor did not recognize affine bounds and would
        // have counted 0 used; the plan correctly counts 1 consumed clause.
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("int", "a"),
            new MethodParameter("int", "b")
        );
        Model inputModel = new Operation(
            new VariableInteger("b"),
            Operator.EQ,
            new Operation(new VariableInteger("a"), Operator.PLUS, new ConstantInteger(1))
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);

        Assert.assertEquals(1, plan.getTotalConstraintCount());
        Assert.assertEquals(1, plan.getUsedConstraintCount());
    }

    @Example
    void integerRecipeLeavesOverflowSensitiveAffineBoundResidualOnly() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("long", "a"),
            new MethodParameter("long", "b")
        );
        Model inputModel = new Operation(
            new Operation(new VariableInteger("a"), Operator.PLUS, new VariableInteger("b")),
            Operator.LT,
            new ConstantInteger(Long.MIN_VALUE)
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertFalse(body.contains("-9223372036854775808 - a - 1"));
        Assert.assertTrue("overflow-sensitive clause must stay residual", plan.hasResidualClauses());
        Assert.assertFalse("overflow-sensitive clause must not be consumed", plan.hasConsumedClauses());
    }

    @Example
    void realRecipeUsesAffineUpperBoundFromPreviousParameter() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("double", "a"),
            new MethodParameter("double", "b")
        );
        Model inputModel = new Operation(
            new Operation(new VariableReal("a"), Operator.PLUS, new VariableReal("b")),
            Operator.LE,
            new teralizer.domain.ConstantReal(10.0)
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(1).getRecipe().emit();

        Assert.assertTrue(body.contains("bUpperBounds = java.util.Arrays.asList(bDefaultMax, (double) (10.0 - a))"));
        Assert.assertTrue(body.contains("bUpperBoundIncluded = java.util.Arrays.asList(true, true)"));
        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void realRecipeGuardsNonFiniteBoundsBeforeComputingScale() {
        List<MethodParameter> parameters = Arrays.asList(
            new MethodParameter("double", "x"),
            new MethodParameter("double", "y"),
            new MethodParameter("double", "eps")
        );
        // (y - x) < eps gives eps the runtime lower bound (y - x), which can overflow to Infinity.
        Model inputModel = new Operation(
            new Operation(new VariableReal("y"), Operator.MINUS, new VariableReal("x")),
            Operator.LT,
            new VariableReal("eps")
        );

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, Collections.emptyMap(), inputModel);
        String body = plan.getParameterPlans().get(2).getRecipe().emit();

        Assert.assertTrue(body, body.contains("(double) (y - x)"));
        Assert.assertTrue(body, body.contains("java.lang.Double.isInfinite(epsMin)"));
        Assert.assertTrue(body, body.contains("java.lang.Double.isInfinite(epsMax)"));
        Assert.assertTrue(body, body.contains("java.lang.Double.isNaN(epsMin)"));
    }

    @Example
    void createsEmptyPlanForMissingInputModel() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, null);

        Assert.assertEquals(0, plan.getTotalConstraintCount());
        Assert.assertEquals(0, plan.getUsedConstraintCount());
        Assert.assertFalse(plan.hasResidualClauses());
        Assert.assertEquals("true", plan.getResidualPredicate());
        Assert.assertEquals(TypeDomain.REAL, plan.getParameterPlans().get(0).getDomain());
    }

    @Example
    void parameterConsumesClauseAndPlanAggregatesItForTelemetry() {
        // x < 5 : the numeric parameter plan consumes the clause; the plan aggregates it
        // to plan level for generation-coverage telemetry. The filter stays unconditional
        // (uses all clauses via getFullPredicate), so soundness is not affected.
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "x"));
        Operation inputModel = new Operation(new VariableInteger("x"), Operator.LT, new ConstantInteger(5));
        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, inputModel);
        Assert.assertTrue("parameter plan reports the consumed clause", plan.getParameterPlans().get(0).getConsumedClauseIds().contains(0));
        Assert.assertTrue("plan aggregates consumed clause to plan level", plan.getConsumedClauseIds().contains(0));
        Assert.assertTrue("plan reports used constraint count", plan.getUsedConstraintCount() == 1);
        Assert.assertTrue("plan reports consumed clauses", plan.hasConsumedClauses());
        Assert.assertFalse("consumed clause is not residual", plan.getResidualClauseIds().contains(0));
    }
}
