package teralizer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotTest {

    @Test
    void visitorVisitsPreThenOperandThenPost() {
        Not not = new Not(new Variable("s", TypeDomain.STRING));

        List<String> visited = new ArrayList<>();
        not.accept(new ModelVisitor() {
            @Override public void preVisit(Not node) { visited.add("pre:not"); }
            @Override public void preVisit(Variable variable) { visited.add("var:" + variable.name); }
            @Override public void postVisit(Not node) { visited.add("post:not"); }
        });

        assertEquals(Arrays.asList("pre:not", "var:s", "post:not"), visited);
    }

    @Test
    void foldsOperandBeforeOwnHook() {
        Not not = new Not(new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "startsWith",
            Collections.singletonList(new Constant("x", TypeDomain.STRING))));

        String folded = not.fold(new RecordingFolder());

        assertEquals("not(invocation:startsWith(variable:s; string:x))", folded);
    }

    @Test
    void toStringWrapsOperandInNot() {
        assertEquals("!(s)", new Not(new Variable("s", TypeDomain.STRING)).toString());
        assertEquals("!(5)", new Not(new Constant(5L, TypeDomain.INTEGER)).toString());
    }

    @Test
    void equalsAndHashCodeByOperand() {
        Not a = new Not(new Variable("s", TypeDomain.STRING));
        Not b = new Not(new Variable("s", TypeDomain.STRING));
        Not c = new Not(new Variable("s", TypeDomain.INTEGER));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, new Variable("s", TypeDomain.STRING));
    }

    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(Constant constant) {
            return constant.domain == TypeDomain.STRING
                ? "string:" + constant.value
                : "constant:" + constant.value;
        }
        @Override public String fold(Variable variable) { return "variable:" + variable.name; }
        @Override public String fold(ArrayExpression expression) { return "array:" + expression.name; }
        @Override public String fold(ArrayElementExpression expression, String elementSelector) {
            return "arrayElement:" + elementSelector;
        }
        @Override public String fold(Invocation invocation, String receiver, List<String> args) {
            return "invocation:" + invocation.method + "(" + receiver + "; " + String.join(", ", args) + ")";
        }
        @Override public String fold(Not not, String operand) { return "not(" + operand + ")"; }
        @Override public String fold(Operation operation, String left, String right) {
            return "operation:" + operation.op;
        }
        @Override public String fold(Operator operator) { return "operator:" + operator; }
        @Override public String fold(Error error) { return "error"; }
        @Override public String fold(ExceptionModel exceptionModel) { return "exception"; }
    }
}
