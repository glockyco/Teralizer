package teralizer.transformer;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class ModelToJavaTransformerPredicateTest {

    private static ModelToJavaTransformer transformer() {
        return new ModelToJavaTransformer();
    }

    @Example
    void dropsClauseReferencingOnlyFilteredParameters() {
        // (a > 0) AND (s.equals("x")): a is generated, s is filtered (stays concrete). The string
        // clause references only the filtered s, so it is dropped even though it now renders; only
        // the generalizable clause survives.
        Operation numeric = new Operation(new Variable("a", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER));
        Invocation string = new Invocation(new Variable("s", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("x", TypeDomain.STRING)));
        Operation model = new Operation(numeric, Operator.AND, string);
        Set<String> generalizable = Collections.singleton("a");
        Assert.assertEquals("(_p_.a > 0)", transformer().transformPredicate(model, generalizable));
    }

    @Example
    void allFilteredClausesRenderTrue() {
        // Only a clause over a filtered parameter remains -> it is dropped -> the predicate is true.
        Invocation string = new Invocation(new Variable("s", TypeDomain.STRING), null, "equals", Collections.singletonList(new Constant("x", TypeDomain.STRING)));
        Assert.assertEquals("true", transformer().transformPredicate(string, Collections.emptySet()));
    }

    @Example
    void keepsAllGeneralizableClauses() {
        // (a > 0) AND (b < 10): both generated -> both kept, joined with &&.
        Operation left = new Operation(new Variable("a", TypeDomain.INTEGER), Operator.GT, new Constant((long) 0, TypeDomain.INTEGER));
        Operation right = new Operation(new Variable("b", TypeDomain.INTEGER), Operator.LT, new Constant((long) 10, TypeDomain.INTEGER));
        Operation model = new Operation(left, Operator.AND, right);
        Set<String> generalizable = new HashSet<>();
        generalizable.add("a");
        generalizable.add("b");
        Assert.assertEquals("(_p_.a > 0) && (_p_.b < 10)", transformer().transformPredicate(model, generalizable));
    }

    @Example
    void nullModelRendersTrue() {
        Assert.assertEquals("true", transformer().transformPredicate(null, Collections.emptySet()));
    }

    @Example
    void refusesToDropClauseThatConstrainsAGeneralizableParameter() {
        // A string operator (EQUALS) on a non-string, still-generated parameter is non-generalizable:
        // dropping it would weaken the path predicate for a symbolized input -> unsound. The typed
        // outcome must surface instead of a silent omission.
        Invocation bad = new Invocation(new Variable("a", TypeDomain.INTEGER), null, "equals", Collections.singletonList(new Constant((long) 0, TypeDomain.INTEGER)));
        try {
            transformer().transformPredicate(bad, Collections.singleton("a"));
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            // expected — do not silently weaken the predicate.
        }
    }

    @Example
    void refusesToDropMixedClauseWithAGeneralizableParameter() {
        // A string operator (EQUALS) on a mixed int/string clause that also constrains the generated
        // 'a': it is non-generalizable yet references a symbolized parameter, so it cannot be dropped.
        Invocation mixed = new Invocation(new Variable("a", TypeDomain.INTEGER), null, "equals", Collections.singletonList(new Variable("s", TypeDomain.STRING)));
        try {
            transformer().transformPredicate(mixed, Collections.singleton("a"));
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            // expected
        }
    }

    @Example
    void refusesToDropClauseWithNoReferencedVariables() {
        // A standalone Error node is non-generalizable and references no variable, so it is
        // not "trivially satisfied by a filtered parameter" — it must not be silently
        // dropped. The tightened guard rethrows rather than treating zero variables as a
        // safe-to-drop clause.
        teralizer.domain.Error error = new teralizer.domain.Error("unsat", "unsupported node");
        try {
            transformer().transformPredicate(error, Collections.emptySet());
            Assert.fail("expected NonGeneralizableExpressionException");
        } catch (NonGeneralizableExpressionException expected) {
            // expected
        }
    }
}
