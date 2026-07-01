package teralizer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvocationTest {

    @Test
    void invocationVisitorVisitsReceiverThenArguments() {
        Invocation invocation = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "replace",
            Arrays.asList(new Constant("a", TypeDomain.STRING), new Constant("b", TypeDomain.STRING)));

        List<String> visited = new ArrayList<>();
        invocation.accept(new ModelVisitor() {
            @Override public void preVisit(Invocation node) { visited.add("call:" + node.method); }
            @Override public void preVisit(Variable variable) { visited.add("var:" + variable.name); }
            @Override public void preVisit(Constant constant) { visited.add("const:" + constant.value); }
            @Override public void postVisit(Invocation node) { visited.add("end:" + node.method); }
        });

        assertEquals(Arrays.asList("call:replace", "var:s", "const:a", "const:b", "end:replace"), visited);
    }

    @Test
    void staticAndInstanceInvocationsAreDistinctValues() {
        Invocation instance = new Invocation(new Variable("s", TypeDomain.STRING), null, "trim", Collections.emptyList());
        Invocation sameInstance = new Invocation(new Variable("s", TypeDomain.STRING), null, "trim", Collections.emptyList());
        Invocation staticCall = new Invocation(null, "java.lang.String", "valueOf", Collections.singletonList(new Variable("i", TypeDomain.INTEGER)));

        assertEquals(instance, sameInstance);
        assertEquals(instance.hashCode(), sameInstance.hashCode());
        assertNotEquals(instance, staticCall);
    }

    @Test
    void notFoldsItsOperandBeforeItsOwnHook() {
        Not not = new Not(new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "startsWith",
            Collections.singletonList(new Constant("x", TypeDomain.STRING))));

        String folded = not.fold(new RecordingFolder());

        assertEquals("not(invocation:startsWith(variable:s; string:x))", folded);
    }

    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(Constant constant) { return constant.domain == TypeDomain.STRING ? "string:" + constant.value : "constant:" + constant.value; }
        @Override public String fold(Variable variable) { return "variable:" + variable.name; }
        @Override public String fold(ArrayExpression expression) { return "array:" + expression.name; }
        @Override public String fold(ArrayElementExpression expression, String elementSelector) { return "arrayElement:" + elementSelector; }
        @Override public String fold(Invocation invocation, String receiver, List<String> args) {
            return "invocation:" + invocation.method + "(" + receiver + "; " + String.join(", ", args) + ")";
        }
        @Override public String fold(Not not, String operand) { return "not(" + operand + ")"; }
        @Override public String fold(Operation operation, String left, String right) { return "operation:" + operation.op; }
        @Override public String fold(Operator operator) { return "operator:" + operator; }
        @Override public String fold(Error error) { return "error"; }
        @Override public String fold(ExceptionModel exceptionModel) { return "exception"; }
    }
}
