package teralizer.jqwik.planning;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;
import teralizer.transformer.NonGeneralizableExpressionException;

public class ConstraintClausesDropTest {

    @Example
    void dropsClauseReferencingOnlyFilteredParameters() {
        // (a > 0) AND (s.equals("x")): a is generated, s is filtered out (stays concrete). The
        // string clause references only the filtered s, so it is dropped even though it renders;
        // only the generalizable clause survives.
        Operation numeric = new Operation(new Variable("a", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER));
        Invocation string = new Invocation(new Variable("s", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("x", TypeDomain.STRING)));
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
        Operation left = new Operation(new Variable("a", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER));
        Operation right = new Operation(new Variable("b", TypeDomain.INTEGER), Operator.LT, new Constant((long) 10, TypeDomain.INTEGER));
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
        Invocation bad = new Invocation(new Variable("a", TypeDomain.INTEGER), null, "equals", Collections.singletonList(new Constant((long) 0, TypeDomain.INTEGER)));

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
