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
    void unknownSymbolThrowsIllegalArgumentException() {
        try {
            Operator.get("definitely-not-an-operator");
            Assert.fail("Expected IllegalArgumentException for unknown symbol");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("definitely-not-an-operator"));
        }
    }
}
