package teralizer.jpf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the total, typed classification of a spec-extraction run's observable state: every run maps
 * to exactly one {@link ExtractionOutcome.Kind}, so the pipeline never records an untyped
 * "unknown reason" failure (the production gap the unreachable-assertion case exposed).
 */
class ExtractionOutcomeTest {

    @Test
    void targetNeverEnteredClassifiesAsTargetNotEntered() {
        ExtractionOutcome outcome = ExtractionOutcome.fromState(false, false);
        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_ENTERED, outcome.getKind());
        assertFalse(outcome.getDetail().isEmpty(), "an actionable reason, never empty/unknown");
    }

    @Test
    void enteredButNotExitedClassifiesAsTargetNotExited() {
        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_EXITED,
            ExtractionOutcome.fromState(true, false).getKind());
    }

    @Test
    void enteredAndExitedClassifiesAsExtracted() {
        assertEquals(ExtractionOutcome.Kind.EXTRACTED,
            ExtractionOutcome.fromState(true, true).getKind());
    }

    @Test
    void exitedWithoutEnteredIsRejectedAsCorruptState() {
        // An exit without an entry is impossible; silently returning TARGET_NOT_ENTERED would mask a
        // listener bug, so the classifier must fail fast rather than fabricate a plausible outcome.
        assertThrows(IllegalArgumentException.class, () -> ExtractionOutcome.fromState(false, true));
    }
}
