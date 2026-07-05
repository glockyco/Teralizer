package teralizer.verification.parsepredicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ParsePredicateCutTest {
    @Test
    public void integerParseableSeedReturnsTrue() {
        assertTrue(new ParsePredicateCut().isIntegerParseable("42"));
    }

    @Test
    public void integerUnparseableSeedReturnsFalse() {
        assertFalse(new ParsePredicateCut().isIntegerParseable("nope"));
    }

    @Test
    public void doubleParseableSeedReturnsTrue() {
        assertTrue(new ParsePredicateCut().isDoubleParseable("3.5"));
    }

    @Test
    public void doubleUnparseableSeedReturnsFalse() {
        assertFalse(new ParsePredicateCut().isDoubleParseable("nope"));
    }

    @Test
    public void parsedValueArmStaysTyped() {
        assertEquals(43, new ParsePredicateCut().parsedPlusOne("42"));
    }
}
