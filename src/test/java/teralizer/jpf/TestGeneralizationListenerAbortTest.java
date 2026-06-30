package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that a real extraction-capability limit aborts the run with a typed {@link ExtractionAborted}
 * carrying the right {@link ExtractionAborted.Reason}, instead of an untyped {@code RuntimeException}.
 *
 * <p>A symbolic run of a branching tested method ({@link teralizer.jpf.targets.Cut#triangular(int)})
 * accumulates a path condition along the concrete path; under a one-character path-condition ceiling,
 * the listener's PC-size guard must fire. This is the abort that is triggerable end-to-end without
 * contriving JPF internals — a real fixture under a real (if tight) configured ceiling. The other
 * three reasons are timing- or model-gap-dependent; their carrier contract is pinned by
 * {@link ExtractionAbortedTest}, and all four hooks throw through the identical typed path.
 */
class TestGeneralizationListenerAbortTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void pathConditionOverflowAbortsWithTypedReason(@TempDir Path workDir) {
        ExtractionAborted aborted = assertThrows(ExtractionAborted.class, () ->
            JpfListenerHarness.runOutcome(
                workDir,
                PKG + "RecursiveSumTarget",
                PKG + "RecursiveSumTarget.wrapper(sym)",
                PKG + "RecursiveSumTarget.wrapper",
                PKG + "Cut.triangular",
                60.0,  // generous time budget: only the path-condition ceiling should trip
                1L     // any non-empty path condition exceeds one character, so the trip is deterministic
            ));

        assertEquals(ExtractionAborted.Reason.PATH_CONDITION_TOO_LARGE, aborted.getReason());
        assertTrue(aborted.getMessage().contains("PATH_CONDITION_TOO_LARGE"),
            "the reason token must reach task.info for downstream classification");
    }
}
