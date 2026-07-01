package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantString;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableString;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ModelToJavaTransformerPredicateTest {

    private static ModelToJavaTransformer transformer() {
        return new ModelToJavaTransformer();
    }

    @Example
    void dropsClauseReferencingOnlyFilteredParameters() {
        // (a > 0) AND (s.equals("x")): a is generated, s is filtered (stays concrete). The string
        // clause references only the filtered s, so it is dropped even though it now renders; only
        // the generalizable clause survives.
        Operation numeric = new Operation(new VariableInteger("a"), Operator.GT, new ConstantInteger(0));
        Operation string = new Operation(new VariableString("s"), Operator.EQUALS, new ConstantString("x"));
        Operation model = new Operation(numeric, Operator.AND, string);
        Set<String> generalizable = Collections.singleton("a");
        Assert.assertEquals("(_p_.a > 0)", transformer().transformPredicate(model, generalizable));
    }

    @Example
    void allFilteredClausesRenderTrue() {
        // Only a clause over a filtered parameter remains -> it is dropped -> the predicate is true.
        Operation string = new Operation(new VariableString("s"), Operator.EQUALS, new ConstantString("x"));
        Assert.assertEquals("true", transformer().transformPredicate(string, Collections.emptySet()));
    }

    @Example
    void keepsAllGeneralizableClauses() {
        // (a > 0) AND (b < 10): both generated -> both kept, joined with &&.
        Operation left = new Operation(new VariableInteger("a"), Operator.GT, new ConstantInteger(0));
        Operation right = new Operation(new VariableInteger("b"), Operator.LT, new ConstantInteger(10));
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
        Operation bad = new Operation(new VariableInteger("a"), Operator.EQUALS, new ConstantInteger(0));
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
        Operation mixed = new Operation(new VariableInteger("a"), Operator.EQUALS, new VariableString("s"));
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
