package teralizer.verification.tryfailcatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class TryFailCatchCutTest {
    @Test
    public void bareTryFailCatchCapturesThrownOracle() {
        try {
            new TryFailCatchCut().rejectNonnegative(1);
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void tryFailCatchWithMessageAssertionKeepsThrownOracle() {
        try {
            new TryFailCatchCut().rejectWithMessage(2);
            fail();
        } catch (IllegalStateException e) {
            assertEquals("constant message", e.getMessage());
        }
    }
}
