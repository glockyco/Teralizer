package teralizer.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class InvocationTest {

    @Test
    void invocationVisitorVisitsReceiverThenArguments() {
        Invocation invocation = new Invocation(
            new VariableString("s"),
            null,
            "replace",
            Arrays.asList(new ConstantString("a"), new ConstantString("b")));

        List<String> visited = new ArrayList<>();
        invocation.accept(new ModelVisitor() {
            @Override public void preVisit(Invocation node) { visited.add("call:" + node.method); }
            @Override public void preVisit(VariableString variable) { visited.add("var:" + variable.name); }
            @Override public void preVisit(ConstantString constant) { visited.add("const:" + constant.value); }
            @Override public void postVisit(Invocation node) { visited.add("end:" + node.method); }
        });

        assertEquals(Arrays.asList("call:replace", "var:s", "const:a", "const:b", "end:replace"), visited);
    }

    @Test
    void staticAndInstanceInvocationsAreDistinctValues() {
        Invocation instance = new Invocation(new VariableString("s"), null, "trim", Collections.emptyList());
        Invocation sameInstance = new Invocation(new VariableString("s"), null, "trim", Collections.emptyList());
        Invocation staticCall = new Invocation(null, "java.lang.String", "valueOf", Collections.singletonList(new VariableInteger("i")));

        assertEquals(instance, sameInstance);
        assertEquals(instance.hashCode(), sameInstance.hashCode());
        assertNotEquals(instance, staticCall);
    }

    @Test
    void notFoldsItsOperandBeforeItsOwnHook() {
        Not not = new Not(new Invocation(
            new VariableString("s"),
            null,
            "startsWith",
            Collections.singletonList(new ConstantString("x"))));

        String folded = not.fold(new RecordingFolder());

        assertEquals("not(invocation:startsWith(variable:s; string:x))", folded);
    }

    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(ConstantInteger constant) { return "integer:" + constant.value; }
        @Override public String fold(ConstantReal constant) { return "real:" + constant.value; }
        @Override public String fold(ConstantString constant) { return "string:" + constant.value; }
        @Override public String fold(VariableInteger variable) { return "variable:" + variable.name; }
        @Override public String fold(VariableReal variable) { return "variable:" + variable.name; }
        @Override public String fold(VariableString variable) { return "variable:" + variable.name; }
        @Override public String fold(ArrayExpression expression) { return "array:" + expression.name; }
        @Override public String fold(ArrayElementExpression expression, String elementSelector) { return "arrayElement:" + elementSelector; }
        @Override public String fold(SymbolicIntegerFunction function, List<String> args) { return "integerFunction:" + function.name; }
        @Override public String fold(SymbolicRealFunction function, List<String> args) { return "realFunction:" + function.name; }
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
