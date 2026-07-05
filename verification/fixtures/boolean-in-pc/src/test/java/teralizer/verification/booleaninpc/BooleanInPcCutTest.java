package teralizer.verification.booleaninpc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BooleanInPcCutTest {
    @Test
    public void computedBooleanIsLicensedByThePathCondition() {
        assertTrue(new BooleanInPcCut().computedEquality(7, 7));
    }

    @Test
    public void primitivePassThroughBooleanKeepsTheSymbolicOracle() {
        assertTrue(new BooleanInPcCut().passThrough(true));
    }

    @Test
    public void boxedPassThroughBooleanKeepsTheSymbolicOracle() {
        assertEquals(Boolean.TRUE, new BooleanInPcCut().boxedPassThrough(true));
    }
}
