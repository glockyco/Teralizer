package teralizer.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TypedLeafTest {

    @Test
    void variableVisitsFoldsAndComparesByNameAndDomain() {
        Variable variable = new Variable("x", TypeDomain.REAL);

        List<String> visits = new ArrayList<>();
        variable.accept(new ModelVisitor() {
            @Override public void preVisit(Variable node) { visits.add("pre:" + node.name + ":" + node.domain); }
            @Override public void postVisit(Variable node) { visits.add("post:" + node.name + ":" + node.domain); }
        });

        assertEquals("variable:x:REAL", variable.fold(new RecordingFolder()));
        assertEquals("x", variable.toString());
        assertEquals(new Variable("x", TypeDomain.REAL), variable);
        assertEquals(new Variable("x", TypeDomain.REAL).hashCode(), variable.hashCode());
        assertNotEquals(new Variable("x", TypeDomain.INTEGER), variable);
        assertEquals(Arrays.asList("pre:x:REAL", "post:x:REAL"), visits);
    }

    @Test
    void constantsFoldCompareByValueAndDomainAndRenderRawValues() {
        Constant integer = new Constant(7L, TypeDomain.INTEGER);
        Constant real = new Constant(1.5d, TypeDomain.REAL);
        Constant string = new Constant("s", TypeDomain.STRING);

        assertEquals("constant:7:INTEGER", integer.fold(new RecordingFolder()));
        assertEquals("constant:1.5:REAL", real.fold(new RecordingFolder()));
        assertEquals("constant:s:STRING", string.fold(new RecordingFolder()));
        assertEquals("7", integer.toString());
        assertEquals("1.5", real.toString());
        assertEquals("s", string.toString());
        assertEquals(new Constant(7L, TypeDomain.INTEGER), integer);
        assertEquals(new Constant(7L, TypeDomain.INTEGER).hashCode(), integer.hashCode());
        assertNotEquals(new Constant(7L, TypeDomain.REAL), integer);
        assertNotEquals(new Constant(7.0d, TypeDomain.REAL), integer);
    }

    private static final class RecordingFolder extends ModelFolder<String> {
        @Override public String fold(Constant constant) { return "constant:" + constant.value + ":" + constant.domain; }
        @Override public String fold(Variable variable) { return "variable:" + variable.name + ":" + variable.domain; }
        @Override public String fold(ArrayExpression expression) { return "array"; }
        @Override public String fold(ArrayElementExpression expression, String elementSelector) { return "array-element"; }
        @Override public String fold(Invocation invocation, String receiver, List<String> args) { return "invocation"; }
        @Override public String fold(Not not, String operand) { return "not"; }
        @Override public String fold(Operation operation, String left, String right) { return "operation"; }
        @Override public String fold(Operator operator) { return "operator"; }
        @Override public String fold(Error error) { return "error"; }
        @Override public String fold(ExceptionModel exceptionModel) { return "exception"; }
    }
}
