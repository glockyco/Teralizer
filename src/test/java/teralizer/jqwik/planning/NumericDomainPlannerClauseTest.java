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
}
