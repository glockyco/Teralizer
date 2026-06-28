package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;

import java.util.Collections;
import java.util.List;

public class BooleanDomainPlannerTest {

    @Example
    void bEqualsOneDerivesTrue() {
        // b == 1
        Operation model = new Operation(new VariableInteger("b"), Operator.EQ, new ConstantInteger(1));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new BooleanDomainPlanner().plan(parameters.get(0), context);

        Assert.assertTrue(
            "b == 1 must produce just(true)",
            plan.getRecipe().emit().contains("Arbitraries.just(true)"));
        Assert.assertEquals(
            "b == 1 clause must be reported consumed",
            Collections.singleton(0),
            plan.getConsumedClauseIds());
    }

    @Example
    void bEqualsZeroDerivesFalse() {
        // b == 0
        Operation model = new Operation(new VariableInteger("b"), Operator.EQ, new ConstantInteger(0));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new BooleanDomainPlanner().plan(parameters.get(0), context);

        Assert.assertTrue(
            "b == 0 must produce just(false)",
            plan.getRecipe().emit().contains("Arbitraries.just(false)"));
        Assert.assertEquals(
            "b == 0 clause must be reported consumed",
            Collections.singleton(0),
            plan.getConsumedClauseIds());
    }

    @Example
    void bNotEqualsZeroDerivesTrue() {
        // b != 0
        Operation model = new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(0));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new BooleanDomainPlanner().plan(parameters.get(0), context);

        Assert.assertTrue(
            "b != 0 must produce just(true)",
            plan.getRecipe().emit().contains("Arbitraries.just(true)"));
        Assert.assertEquals(
            "b != 0 clause must be reported consumed",
            Collections.singleton(0),
            plan.getConsumedClauseIds());
    }

    @Example
    void bNotEqualsOneDerivesFalse() {
        // b != 1
        Operation model = new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(1));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new BooleanDomainPlanner().plan(parameters.get(0), context);
        Assert.assertTrue("b != 1 must produce just(false)", plan.getRecipe().emit().contains("Arbitraries.just(false)"));
        Assert.assertEquals("b != 1 clause must be reported consumed", Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void unconstrainedGeneratesOfTrueFalse() {
        // no constraint on b
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(null, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new BooleanDomainPlanner().plan(parameters.get(0), context);

        Assert.assertEquals(
            "unconstrained boolean must produce of(true, false)",
            "return net.jqwik.api.Arbitraries.of(true, false)",
            plan.getRecipe().emit());
        Assert.assertTrue(
            "unconstrained boolean must consume no clauses",
            plan.getConsumedClauseIds().isEmpty());
    }
}
