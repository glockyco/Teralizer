package teralizer.jqwik.planning;

import teralizer.domain.Constant;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;

import java.util.Collections;
import java.util.List;

public class NumericDomainPlannerClauseTest {
    @Example
    void reportsConsumedClauseIdForAtomicIntegerBound() {
        // a < 5
        Operation model = new Operation(new Variable("a", TypeDomain.INTEGER), Operator.LT, new Constant(5L, TypeDomain.INTEGER));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "a"));

        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("a", "int"));
        PlanningContext context = new PlanningContext(parameters, clauses);

        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);

        Assert.assertEquals(
            "the single a<5 clause must be reported consumed",
            Collections.singleton(0),
            plan.getConsumedClauseIds()
        );
    }

    @Example
    void reportsConsumedClauseIdForDoubleConstantBound() {
        // x >= 1.5
        Operation model = new Operation(new Variable("x", TypeDomain.REAL), Operator.GE, new Constant(1.5d, TypeDomain.REAL));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("double", "x"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("x", "double"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void reportsConsumedClauseIdForCharEquality() {
        // c == 'A' (char modeled as an INTEGER variable, 'A' as Constant(65))
        Operation model = new Operation(new Variable("c", TypeDomain.INTEGER), Operator.EQ, new Constant(65L, TypeDomain.INTEGER));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("char", "c"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("c", "char"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void variableBoundIsConsumedByHigherIndexedParameterOnly() {
        // b > a : bounds b (higher index), not a
        Operation model = new Operation(new Variable("b", TypeDomain.INTEGER), Operator.GT, new Variable("a", TypeDomain.INTEGER));
        List<MethodParameter> parameters = java.util.Arrays.asList(new MethodParameter("int", "a"), new MethodParameter("int", "b"));
        java.util.Map<String, String> types = new java.util.HashMap<>();
        types.put("a", "int");
        types.put("b", "int");
        List<ConstraintClause> clauses = ConstraintClauses.from(model, types);
        PlanningContext context = new PlanningContext(parameters, clauses);
        Assert.assertEquals("b (higher index) consumes b>a", Collections.singleton(0), new NumericDomainPlanner().plan(parameters.get(1), context).getConsumedClauseIds());
        Assert.assertTrue("a (lower index) does not consume b>a", new NumericDomainPlanner().plan(parameters.get(0), context).getConsumedClauseIds().isEmpty());
    }

    @Example
    void leavesUnsupportedShapeResidual() {
        // a % 2 == 0 : neither an atomic bound nor affine -> unconsumed
        Operation model = new Operation(
            new Operation(new Variable("a", TypeDomain.INTEGER), Operator.MOD, new Constant(2L, TypeDomain.INTEGER)),
            Operator.EQ, new Constant(0L, TypeDomain.INTEGER));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "a"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("a", "int"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertTrue(plan.getConsumedClauseIds().isEmpty());
    }

    @Example
    void numericInterpreterSkipsBooleanDomainParameters() {
        // boolean b modeled as an INTEGER variable; b == 1 must NOT become an integer equality
        Operation model = new Operation(new Variable("b", TypeDomain.INTEGER), Operator.EQ, new Constant(1L, TypeDomain.INTEGER));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("boolean", "b"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("b", "boolean"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        NumericClauseInterpretation interpretation = context.getInterpretation("b");
        Assert.assertNull("boolean param gets no numeric constraints", interpretation.getConstraints());
        Assert.assertTrue("boolean param consumes no clause numerically", interpretation.getConsumedClauseIds().isEmpty());
    }
}
