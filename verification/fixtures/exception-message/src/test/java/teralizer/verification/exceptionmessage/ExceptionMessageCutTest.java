package teralizer.verification.exceptionmessage;

import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class ExceptionMessageCutTest {
    @Test
    public void concatenatedMessageAfterThrowGuardKeepsThrownOracle() {
        assertThrows(
            ExceptionMessageCut.MissingLabelException.class,
            () -> new ExceptionMessageCut().throwWithConcatenatedMessage(7)
        );
    }
}
