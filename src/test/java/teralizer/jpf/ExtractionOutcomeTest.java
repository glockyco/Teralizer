package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the total, typed classification of a spec-extraction run's observable state: every run maps
 * to exactly one {@link ExtractionOutcome.Kind}, so the pipeline never records an untyped
 * "unknown reason" failure (the production gap the unreachable-assertion case exposed).
 */
class ExtractionOutcomeTest {

    @Test
    void oracleCallWithoutTargetEntryClassifiesAsTargetNotEntered() {
        ExtractionOutcome outcome = ExtractionOutcome.fromState(false, false, true);
        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_ENTERED, outcome.getKind());
        assertFalse(outcome.getDetail().isEmpty(), "an actionable reason, never empty/unknown");
    }

    @Test
    void wrapperExitWithoutTargetEntryStillExtractsForCompositeOracle() {
        ExtractionOutcome outcome = ExtractionOutcome.fromState(false, true, false);
        assertEquals(ExtractionOutcome.Kind.EXTRACTED, outcome.getKind());
        assertTrue(outcome.getDetail().contains("not entered"), "target entry remains observable");
    }

    @Test
    void enteredButNotExitedClassifiesAsTargetNotExited() {
        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_EXITED,
            ExtractionOutcome.fromState(true, false, true).getKind());
    }

    @Test
    void enteredAndExitedClassifiesAsExtracted() {
        assertEquals(ExtractionOutcome.Kind.EXTRACTED,
            ExtractionOutcome.fromState(true, true, true).getKind());
    }
}
