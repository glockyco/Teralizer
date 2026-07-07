package teralizer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeedSpecConsistencyTest {

    @Test
    void violatesOnlyWhenSeedProvablyContradictsPredicate() {
        assertEquals(
            SeedSpecConsistency.Verdict.VIOLATED,
            evaluate(
                new Operation(lengthEqualsZero("str1"), Operator.AND, lengthEqualsZero("str2")),
                seed("str1", "Peter", "str2", "Peady")
            )
        );
        assertEquals(
            SeedSpecConsistency.Verdict.SATISFIED,
            evaluate(
                new Operation(length("str1"), Operator.GT, integer(0)),
                seed("str1", "Peter")
            )
        );
        assertEquals(
            SeedSpecConsistency.Verdict.UNKNOWN,
            evaluate(
                lengthEqualsZero("missing"),
                seed("str1", "Peter")
            )
        );
        assertEquals(
            SeedSpecConsistency.Verdict.UNKNOWN,
            evaluate(
                new Operation(unsupportedInvocation("str1"), Operator.EQ, string("Peter")),
                seed("str1", "Peter")
            )
        );
    }

    private static SeedSpecConsistency.Verdict evaluate(Model inputPredicate, Map<String, Value> seed) {
        return SeedSpecConsistency.evaluate(inputPredicate, seed, parameters());
    }

    private static List<MethodParameter> parameters() {
        return Arrays.asList(
            new MethodParameter("java.lang.String", "str1"),
            new MethodParameter("java.lang.String", "str2"),
            new MethodParameter("java.lang.String", "missing")
        );
    }

    private static Operation lengthEqualsZero(String name) {
        return new Operation(length(name), Operator.EQ, integer(0));
    }

    private static Invocation length(String name) {
        return new Invocation(new Variable(name, TypeDomain.STRING), null, "length", Collections.emptyList());
    }

    private static Invocation unsupportedInvocation(String name) {
        return new Invocation(new Variable(name, TypeDomain.STRING), null, "trim", Collections.emptyList());
    }

    private static Constant integer(int value) {
        return new Constant(value, TypeDomain.INTEGER);
    }

    private static Constant string(String value) {
        return new Constant(value, TypeDomain.STRING);
    }

    private static Map<String, Value> seed(String firstName, String firstValue) {
        Map<String, Value> seed = new HashMap<>();
        seed.put(firstName, new StringValue(firstValue));
        return seed;
    }

    private static Map<String, Value> seed(
        String firstName,
        String firstValue,
        String secondName,
        String secondValue
    ) {
        Map<String, Value> seed = seed(firstName, firstValue);
        seed.put(secondName, new StringValue(secondValue));
        return seed;
    }
}
