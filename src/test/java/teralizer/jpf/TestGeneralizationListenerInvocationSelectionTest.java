package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;

/**
 * Pins which invocation of the tested method {@link TestGeneralizationListener} captures when the
 * wrapper reaches it more than once: the outermost frame under recursion, and the first wrapper
 * invocation under a loop (the reachable {@code isAscii} shape), after which the search terminates.
 *
 * <p>Identity is the tested call's stack position pinned at first entry, matched at the exit of that
 * same frame, captured exactly once — so a looped wrapper records its first tested call, not its last.
 */
class TestGeneralizationListenerInvocationSelectionTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void capturesOutermostFrameUnderRecursion(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "RecursiveSumTarget",
            PKG + "RecursiveSumTarget.wrapper(con)",
            PKG + "RecursiveSumTarget.wrapper",
            PKG + "Cut.triangular"
        );

        assertEquals(Integer.valueOf(6), ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "triangular(3)=6 is the outermost return, not an inner frame's (3/1/0)");
    }

    @Test
    void capturesFirstWrapperInvocationUnderALoop(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "LoopedWrapperTarget",
            PKG + "LoopedWrapperTarget.wrapper(con)",
            PKG + "LoopedWrapperTarget.wrapper",
            PKG + "Cut.twice"
        );

        assertEquals(Integer.valueOf(14), ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "twice(7)=14 from the first wrapper invocation; the search terminates before twice(8)/twice(9)");
    }
}
