package teralizer.domain;

import net.jqwik.api.Example;
import org.junit.Assert;

public class OperatorTest {

    @Example
    void knownCompactSymbolResolvesToCorrectConstant() {
        Assert.assertSame(Operator.EQ, Operator.get("=="));
    }

    @Example
    void knownSpacedSymbolResolvesToSameConstant() {
        // " == " is also registered in the static block for EQ
        Assert.assertSame(Operator.EQ, Operator.get(" == "));
    }

    @Example
    void stringMethodSymbolsAreNotOperators() {
        for (String symbol : new String[] {"equals", "notequals", "contains", "concat", "trim", "replace"}) {
            try {
                Operator.get(symbol);
                Assert.fail("Expected string method symbol to be outside Operator: " + symbol);
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains(symbol));
            }
        }
    }

    @Example
    void mathFunctionSymbolsAreNotOperators() {
        for (String symbol : new String[] {"sqrt", "pow", "exp", "log", "sin", "cos", "tan", "asin", "acos", "atan", "atan2"}) {
            try {
                Operator.get(symbol);
                Assert.fail("Expected math function symbol to be outside Operator: " + symbol);
            } catch (IllegalArgumentException expected) {
                Assert.assertTrue(expected.getMessage().contains(symbol));
            }
        }
    }

    @Example
    void unknownSymbolThrowsIllegalArgumentException() {
        try {
            Operator.get("definitely-not-an-operator");
            Assert.fail("Expected IllegalArgumentException for unknown symbol");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("definitely-not-an-operator"));
        }
    }
}
