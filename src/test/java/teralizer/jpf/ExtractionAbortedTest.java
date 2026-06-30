package teralizer.jpf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the typed-abort carrier contract that the exclusion classifiers depend on: every
 * {@link ExtractionAborted} keeps its {@link ExtractionAborted.Reason}, and embeds the reason name in
 * its message so the reason survives into the persisted {@code task.info} stack trace (where the
 * {@code mv_exclusions_jpf} view and the JARVIS run script key on it). Iterating the enum keeps the
 * contract honest for any reason added later.
 */
class ExtractionAbortedTest {

    @Test
    void everyReasonRoundTripsAndIsTokenisedInTheMessage() {
        for (ExtractionAborted.Reason reason : ExtractionAborted.Reason.values()) {
            ExtractionAborted aborted = new ExtractionAborted(reason, "context detail");

            assertSame(reason, aborted.getReason());
            assertTrue(aborted.getMessage().contains(reason.name()),
                "the reason token must be in the message for downstream classification: " + aborted.getMessage());
            assertTrue(aborted.getMessage().contains("context detail"),
                "the human-readable detail must be preserved alongside the token");
        }
    }
}
