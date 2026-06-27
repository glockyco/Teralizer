package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConstraintClausesTest {
    @Example
    void flattensTopLevelConjunctionsIntoStableClauses() {
        Model model = new Operation(
            new Operation(
                new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0)),
                Operator.AND,
                new Operation(new VariableInteger("y"), Operator.LT, new ConstantInteger(10))
            ),
            Operator.AND,
            new Operation(new VariableInteger("z"), Operator.EQ, new ConstantInteger(3))
        );
        Map<String, String> parameterTypes = new HashMap<>();
        parameterTypes.put("x", "int");
        parameterTypes.put("y", "int");
        parameterTypes.put("z", "int");

        List<ConstraintClause> clauses = ConstraintClauses.from(model, parameterTypes);

        Assert.assertEquals(3, clauses.size());
        Assert.assertEquals(0, clauses.get(0).getId());
        Assert.assertEquals(new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0)), clauses.get(0).getExpression());
        Assert.assertEquals("(_p_.x > 0)", clauses.get(0).getJavaExpression());
        Assert.assertEquals(1, clauses.get(1).getId());
        Assert.assertEquals("(_p_.y < 10)", clauses.get(1).getJavaExpression());
        Assert.assertEquals(2, clauses.get(2).getId());
        Assert.assertEquals("(_p_.z == 3)", clauses.get(2).getJavaExpression());
    }

    @Example
    void preservesNonConjunctionAsSingleClause() {
        Model model = new Operation(new VariableInteger("x"), Operator.GT, new ConstantInteger(0));
        Map<String, String> parameterTypes = new HashMap<>();
        parameterTypes.put("x", "int");

        List<ConstraintClause> clauses = ConstraintClauses.from(model, parameterTypes);

        Assert.assertEquals(1, clauses.size());
        Assert.assertEquals(0, clauses.get(0).getId());
        Assert.assertEquals("(_p_.x > 0)", clauses.get(0).getJavaExpression());
    }

    @Example
    void returnsNoClausesForNullModel() {
        Assert.assertTrue(ConstraintClauses.from(null, new HashMap<>()).isEmpty());
    }
}
