package teralizer.jqwik.planning;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class ConstraintClausesTest {
    @Example
    void flattensTopLevelConjunctionsIntoStableClauses() {
        Model model = new Operation(
            new Operation(
                new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER)),
                Operator.AND,
                new Operation(new Variable("y", TypeDomain.INTEGER), Operator.LT, new Constant((long) 10, TypeDomain.INTEGER))
            ),
            Operator.AND,
            new Operation(new Variable("z", TypeDomain.INTEGER), Operator.EQ, new Constant((long) 3, TypeDomain.INTEGER))
        );
        Map<String, String> parameterTypes = new HashMap<>();
        parameterTypes.put("x", "int");
        parameterTypes.put("y", "int");
        parameterTypes.put("z", "int");

        List<ConstraintClause> clauses = ConstraintClauses.from(model, parameterTypes);

        Assert.assertEquals(3, clauses.size());
        Assert.assertEquals(0, clauses.get(0).getId());
        Assert.assertEquals(new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER)), clauses.get(0).getExpression());
        Assert.assertEquals("(_p_.x > 0)", clauses.get(0).getJavaExpression());
        Assert.assertEquals(1, clauses.get(1).getId());
        Assert.assertEquals("(_p_.y < 10)", clauses.get(1).getJavaExpression());
        Assert.assertEquals(2, clauses.get(2).getId());
        Assert.assertEquals("(_p_.z == 3)", clauses.get(2).getJavaExpression());
    }

    @Example
    void preservesNonConjunctionAsSingleClause() {
        Model model = new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER));
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
