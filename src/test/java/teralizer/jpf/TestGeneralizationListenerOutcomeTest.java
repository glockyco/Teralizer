package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins how {@link TestGeneralizationListener} classifies a tested method's output as a normal
 * return versus a thrown exception.
 *
 * <p>{@code writeSpecificationFiles} selects the output branch from the <em>exit instruction</em>
 * ({@code JVMReturnInstruction} vs {@code ATHROW}), not from {@code pendingThrownException}. So a
 * method that throws and catches internally, then returns, exits on a return instruction and must
 * be recorded by its return value. This test documents that behavior and guards it against
 * regression (e.g. a future change that consulted a stale pending-exception field instead).
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
}
