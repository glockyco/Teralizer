package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Not;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

import java.util.Collections;
import java.util.List;

public class StringDomainPlannerTest {

    private static final MethodParameter S = new MethodParameter("java.lang.String", "s");

    private static ParameterGenerationPlan plan(Model model) {
        List<MethodParameter> parameters = Collections.singletonList(S);
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("s", "java.lang.String"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        return new StringDomainPlanner().plan(S, context);
    }

    private static Invocation call(String method, String literal) {
        return new Invocation(new Variable("s", TypeDomain.STRING), null, method, Collections.singletonList(new Constant(literal, TypeDomain.STRING)));
    }

    @Example
    void equalityCollapsesToArbitrariesOf() {
        ParameterGenerationPlan plan = plan(call("equals", "foo"));
        Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"foo\")", plan.getRecipe().emit());
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void startsWithBuildsPrefixMap() {
        ParameterGenerationPlan plan = plan(call("startsWith", "ab"));
        String emit = plan.getRecipe().emit();
        Assert.assertTrue(emit, emit.contains(".map(_x_ -> \"ab\" + _x_)"));
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void containsBuildsInfixMap() {
        ParameterGenerationPlan plan = plan(call("contains", "z"));
        String emit = plan.getRecipe().emit();
        Assert.assertTrue(emit, emit.contains("_x_ + \"z\""));
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void negationIsLeftToResidualFilter() {
        // s != "x" is not construction-satisfiable, so the recipe is the bounded non-null base and
        // the clause is left unconsumed for the unconditional residual filter.
        ParameterGenerationPlan plan = plan(new Not(call("equals", "x")));
        Assert.assertEquals("return net.jqwik.api.Arbitraries.strings().ascii().ofMaxLength(16)", plan.getRecipe().emit());
        Assert.assertTrue(plan.getConsumedClauseIds().isEmpty());
    }

    @Example
    void equalityWithCoexistingFragmentConsumesOnlyEquality() {
        // s.equals("foo") AND s.startsWith("ab"): Arbitraries.of("foo") structurally enforces only the
        // equality, so only that clause is consumed; the startsWith is left to the residual filter,
        // which stays correct even if the two ever combined unsatisfiably.
        Model model = new Operation(
            call("equals", "foo"),
            Operator.AND,
            call("startsWith", "ab"));
        ParameterGenerationPlan plan = plan(model);
        Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"foo\")", plan.getRecipe().emit());
        Assert.assertEquals("only the equality clause is structurally enforced", Collections.singleton(0), plan.getConsumedClauseIds());
    }
}
