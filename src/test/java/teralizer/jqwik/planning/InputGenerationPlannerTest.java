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
    void createsResidualOnlyPlanBeforeDomainPlannersConsumeClauses() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("int", "x"));
        Model inputModel = new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0));

        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, inputModel);

        Assert.assertEquals(1, plan.getTotalConstraintCount());
        Assert.assertEquals(0, plan.getUsedConstraintCount());
        Assert.assertEquals(1, plan.getResidualClauses().size());
        Assert.assertEquals("(_p_.x > 0)", plan.getResidualPredicate());
        Assert.assertFalse(plan.hasConsumedClauses());
        Assert.assertTrue(plan.hasResidualClauses());
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
        Assert.assertTrue(plan.hasResidualClauses());
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
        Assert.assertTrue(plan.hasResidualClauses());
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
        Assert.assertTrue(plan.hasResidualClauses());
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
}
