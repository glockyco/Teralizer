package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins how {@link TestGeneralizationListener} classifies a tested method's output as a normal
 * return versus a thrown exception, and how an unreachable tested call becomes a typed outcome
 * rather than a silent failure.
 *
 * <p>{@code captureInvocation} selects the output branch from the <em>exit instruction</em>
 * ({@code JVMReturnInstruction} vs {@code ATHROW}), not from {@code pendingThrownException}. So a
 * method that throws and catches internally, then returns, exits on a return instruction and must
 * be recorded by its return value. When the tested method never executes, the listener captures no
 * invocation and the run classifies as {@code TARGET_NOT_ENTERED}.
 */
class TestGeneralizationListenerOutcomeTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void recordsReturnValueWhenExceptionIsCaughtInternally(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "CatchesInternallyTarget",
            PKG + "CatchesInternallyTarget.wrapper(con)",
            PKG + "CatchesInternallyTarget.wrapper",
            PKG + "Cut.catchesInternally"
        );

        assertEquals("int", capture.getOutputValue().getType(), "the output is a normal return, not the handled exception");
        assertEquals("5", capture.getOutputValue().getValue(), "the returned value");
    }

    @Test
    void unreachableTargetIsClassifiedAsTargetNotEntered(@TempDir Path workDir) {
        // The tested call sits in a branch the concrete path never takes (the isAscii dead-else
        // shape): the production listener + classifier must report TARGET_NOT_ENTERED, not the old
        // "Failed to collect input/output specification for unknown reason".
        ExtractionOutcome outcome = JpfListenerHarness.runOutcome(
            workDir,
            PKG + "UnreachableWrapperTarget",
            PKG + "UnreachableWrapperTarget.wrapper(con)",
            PKG + "UnreachableWrapperTarget.wrapper",
            PKG + "Cut.twice"
        );

        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_ENTERED, outcome.getKind());
    }
}
