package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantString;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableString;
import teralizer.transformer.NonGeneralizableExpressionException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConstraintClausesDropTest {

    @Example
    void dropsNonGeneralizableClauseReferencingOnlyFilteredParameters() {
        // (a > 0) AND (s.equals("x")): a is generated, s is filtered out (stays concrete).
        // The string clause is sound to drop; only the generalizable clause survives.
        Operation numeric = new Operation(new VariableInteger("a"), Operator.GT, new ConstantInteger(0));
        Operation string = new Operation(new VariableString("s"), Operator.EQUALS, new ConstantString("x"));
        Model model = new Operation(numeric, Operator.AND, string);

        Map<String, String> types = new HashMap<>();
        types.put("a", "int");
        types.put("s", "String");
        Set<String> generalizable = new HashSet<>(Collections.singletonList("a"));

        List<ConstraintClause> clauses = ConstraintClauses.from(model, types, generalizable);

        Assert.assertEquals(1, clauses.size());
        Assert.assertEquals("(_p_.a > 0)", clauses.get(0).getJavaExpression());
    }

    @Example
    void keepsAllGeneralizableClauses() {
        Operation left = new Operation(new VariableInteger("a"), Operator.GT, new ConstantInteger(0));
        Operation right = new Operation(new VariableInteger("b"), Operator.LT, new ConstantInteger(10));
        Model model = new Operation(left, Operator.AND, right);

        Map<String, String> types = new HashMap<>();
        types.put("a", "int");
        types.put("b", "int");
        Set<String> generalizable = new HashSet<>();
        generalizable.add("a");
        generalizable.add("b");

        List<ConstraintClause> clauses = ConstraintClauses.from(model, types, generalizable);

        Assert.assertEquals(2, clauses.size());
    }

    @Example
    void refusesToDropClauseConstrainingAGeneralizableParameter() {
        // Unsupported operator on a still-generated parameter: must not be dropped.
        Operation bad = new Operation(new VariableInteger("a"), Operator.EQUALS, new ConstantInteger(0));

        Map<String, String> types = new HashMap<>();
        types.put("a", "int");
        Set<String> generalizable = new HashSet<>(Collections.singletonList("a"));

        try {
            ConstraintClauses.from(bad, types, generalizable);
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            // sound: do not silently weaken the predicate
        }
    }

    @Example
    void returnsNoClausesForNullModel() {
        Map<String, String> types = new HashMap<>();
        Assert.assertTrue(ConstraintClauses.from(null, types, Collections.emptySet()).isEmpty());
    }
}
