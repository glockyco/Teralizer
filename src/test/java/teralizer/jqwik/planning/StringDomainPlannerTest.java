package teralizer.jqwik.planning;

import java.util.Collections;
import java.util.List;
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

    private static Invocation parseCall(String method) {
        return new Invocation(null, "ParsePredicates", method, Collections.singletonList(new Variable("s", TypeDomain.STRING)));
    }

    private static ParameterGenerationPlan planWithClause(Model expression, String javaExpression) {
        List<MethodParameter> parameters = Collections.singletonList(S);
        PlanningContext context = new PlanningContext(parameters, Collections.singletonList(new ConstraintClause(0, expression, javaExpression)));
        return new StringDomainPlanner().plan(S, context);
    }

    @Example
    void equalityCollapsesToArbitrariesOf() {
        ParameterGenerationPlan plan = plan(call("equals", "foo"));
        Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"foo\")", plan.getRecipe().emit());
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }


    @Example
    void variableEqualityBindsHigherIndexedStringToLowerIndexedStringOnly() {
        Model equality = new Invocation(
            new Variable("str1", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Variable("str2", TypeDomain.STRING)));
        List<MethodParameter> parameters = java.util.Arrays.asList(
            new MethodParameter("java.lang.String", "str1"),
            new MethodParameter("java.lang.String", "str2"));
        java.util.Map<String, String> types = new java.util.HashMap<>();
        types.put("str1", "java.lang.String");
        types.put("str2", "java.lang.String");
        PlanningContext context = new PlanningContext(parameters, ConstraintClauses.from(equality, types));

        ParameterGenerationPlan higherIndexedPlan = new StringDomainPlanner().plan(parameters.get(1), context);
        Assert.assertEquals("return net.jqwik.api.Arbitraries.just(str1)", higherIndexedPlan.getRecipe().emit());
        Assert.assertEquals(Collections.singleton(0), higherIndexedPlan.getConsumedClauseIds());

        ParameterGenerationPlan lowerIndexedPlan = new StringDomainPlanner().plan(parameters.get(0), context);
        Assert.assertEquals("return net.jqwik.api.Arbitraries.strings().ascii().ofMaxLength(16)", lowerIndexedPlan.getRecipe().emit());
        Assert.assertTrue(lowerIndexedPlan.getConsumedClauseIds().isEmpty());
        Assert.assertFalse(lowerIndexedPlan.getRecipe().emit().contains("Arbitraries.just(str2)"));

        Model literalEquality = new Invocation(
            new Variable("str1", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("x", TypeDomain.STRING)));
        PlanningContext literalContext = new PlanningContext(parameters, ConstraintClauses.from(literalEquality, types));
        ParameterGenerationPlan literalPlan = new StringDomainPlanner().plan(parameters.get(0), literalContext);
        Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"x\")", literalPlan.getRecipe().emit());
        Assert.assertEquals(Collections.singleton(0), literalPlan.getConsumedClauseIds());
    }
    @Example
    void isEmptyCollapsesToEmptyStringAndConsumesClause() {
        ParameterGenerationPlan plan = plan(new Invocation(new Variable("s", TypeDomain.STRING), null, "isEmpty", Collections.emptyList()));
        Assert.assertEquals("return net.jqwik.api.Arbitraries.of(\"\")", plan.getRecipe().emit());
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
    void positiveParsePredicateClausesUseNumericStringArbitrariesAndConsumeClause() {
        Assert.assertEquals(
            "return net.jqwik.api.Arbitraries.integers().map(String::valueOf)",
            planWithClause(parseCall("isInteger"), "ParsePredicates.isInteger(_p_.s)").getRecipe().emit());
        Assert.assertEquals(
            Collections.singleton(0),
            planWithClause(parseCall("isInteger"), "ParsePredicates.isInteger(_p_.s)").getConsumedClauseIds());

        Assert.assertEquals(
            "return net.jqwik.api.Arbitraries.longs().map(String::valueOf)",
            planWithClause(parseCall("isLong"), "ParsePredicates.isLong(_p_.s)").getRecipe().emit());
        Assert.assertEquals(
            "return net.jqwik.api.Arbitraries.floats().map(String::valueOf)",
            planWithClause(parseCall("isFloat"), "ParsePredicates.isFloat(_p_.s)").getRecipe().emit());
        Assert.assertEquals(
            "return net.jqwik.api.Arbitraries.doubles().map(String::valueOf)",
            planWithClause(parseCall("isDouble"), "ParsePredicates.isDouble(_p_.s)").getRecipe().emit());
    }

    @Example
    void negativeParsePredicateClausesKeepDefaultAsciiAndRemainResidual() {
        ParameterGenerationPlan plan = planWithClause(
            new Not(parseCall("isDouble")),
            "(!ParsePredicates.isDouble(_p_.s))");

        Assert.assertEquals("return net.jqwik.api.Arbitraries.strings().ascii().ofMaxLength(16)", plan.getRecipe().emit());
        Assert.assertTrue(plan.getConsumedClauseIds().isEmpty());
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
