package teralizer.verification.floatcompare;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FloatCompareCutTest {
    @Test
    public void floatExceedsTakesTrueSeedBranch() {
        assertTrue(FloatCompareCut.floatExceeds(2.0f, 1.0f));
    }

    @Test
    public void floatExceedsTakesFalseSeedBranch() {
        assertFalse(FloatCompareCut.floatExceeds(1.0f, 2.0f));
    }
}
