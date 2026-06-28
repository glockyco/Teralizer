package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodParameter;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.ConstantReal;
import teralizer.domain.VariableReal;

import java.util.Collections;
import java.util.List;

public class NumericDomainPlannerClauseTest {
    @Example
    void reportsConsumedClauseIdForAtomicIntegerBound() {
        // a < 5
        Operation model = new Operation(new VariableInteger("a"), Operator.LT, new ConstantInteger(5));
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
        Operation model = new Operation(new VariableReal("x"), Operator.GE, new ConstantReal(1.5));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("double", "x"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("x", "double"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void reportsConsumedClauseIdForCharEquality() {
        // c == 'A' (char modeled as VariableInteger, 'A' as ConstantInteger(65))
        Operation model = new Operation(new VariableInteger("c"), Operator.EQ, new ConstantInteger(65));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("char", "c"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("c", "char"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertEquals(Collections.singleton(0), plan.getConsumedClauseIds());
    }

    @Example
    void variableBoundIsConsumedByHigherIndexedParameterOnly() {
        // b > a : bounds b (higher index), not a
        Operation model = new Operation(new VariableInteger("b"), Operator.GT, new VariableInteger("a"));
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
            new Operation(new VariableInteger("a"), Operator.MOD, new ConstantInteger(2)),
            Operator.EQ, new ConstantInteger(0));
        List<MethodParameter> parameters = Collections.singletonList(new MethodParameter("int", "a"));
        List<ConstraintClause> clauses = ConstraintClauses.from(model, Collections.singletonMap("a", "int"));
        PlanningContext context = new PlanningContext(parameters, clauses);
        ParameterGenerationPlan plan = new NumericDomainPlanner().plan(parameters.get(0), context);
        Assert.assertTrue(plan.getConsumedClauseIds().isEmpty());
    }
}
