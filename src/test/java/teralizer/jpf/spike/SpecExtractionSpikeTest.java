package teralizer.jpf.spike;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.jpf.JpfListenerHarness;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Spike validating the spec-extraction redesign against real SPF via the in-process
 * {@link JpfListenerHarness}: an observer-only listener records state, and a pure post-run step
 * classifies it into a typed {@link ExtractionOutcome}. Covers the shapes that motivate the plan,
 * each faithful to what instrumentation actually emits (one tested call per uniquely-named wrapper):
 * symbolic capture, recursion (outermost frame by stack position), a looped wrapper (first
 * invocation only), and the unreachable assertion (the {@code isAscii} dead-{@code else} shape) as a
 * typed outcome rather than a silent failure.
 */
class SpecExtractionSpikeTest {

    private static final String PKG = "teralizer.jpf.targets.";

    private static SpikeObserverListener runObserving(
        Path workDir, String target, String symbolicMethod, String instrumented, String tested
    ) {
        Config config = JpfListenerHarness.buildConfig(
            workDir, PKG + target, PKG + symbolicMethod, PKG + instrumented, PKG + tested
        );
        JPF jpf = new JPF(config);
        SpikeObserverListener listener = new SpikeObserverListener(config);
        jpf.addListener(listener);
        jpf.run();
        assertFalse(jpf.foundErrors(), "JPF reported errors for " + symbolicMethod);
        return listener;
    }

    @Test
    void extractedCapturesSymbolicReturnOfAnEnteredTarget(@TempDir Path workDir) {
        ExtractionOutcome outcome = ExtractionOutcome.classify(runObserving(workDir,
            "SymbolicReturnTarget", "SymbolicReturnTarget.wrapper(sym)",
            "SymbolicReturnTarget.wrapper", "Cut.twice"));

        assertEquals(ExtractionOutcome.Kind.EXTRACTED, outcome.kind);
        assertEquals("10", outcome.concreteOut, "concrete return of twice(5)");
        assertNotNull(outcome.symbolicOut, "symbolic return attribute captured post-run, not written to disk");
    }

    @Test
    void frameIdentitySelectsOutermostUnderRecursion(@TempDir Path workDir) {
        ExtractionOutcome outcome = ExtractionOutcome.classify(runObserving(workDir,
            "RecursiveSumTarget", "RecursiveSumTarget.wrapper(con)",
            "RecursiveSumTarget.wrapper", "Cut.triangular"));

        assertEquals(ExtractionOutcome.Kind.EXTRACTED, outcome.kind);
        assertEquals("6", outcome.concreteOut, "outermost triangular(3)=6 by stack position, not an inner return (3/1/0)");
    }

    @Test
    void capturesFirstWrapperInvocationInALoopThenTerminates(@TempDir Path workDir) {
        ExtractionOutcome outcome = ExtractionOutcome.classify(runObserving(workDir,
            "LoopedWrapperTarget", "LoopedWrapperTarget.wrapper(con)",
            "LoopedWrapperTarget.wrapper", "Cut.twice"));

        assertEquals(ExtractionOutcome.Kind.EXTRACTED, outcome.kind);
        assertEquals("14", outcome.concreteOut, "first iteration twice(7)=14; search terminates before twice(8)/twice(9)");
    }

    @Test
    void unreachableTargetClassifiesAsTargetNotEntered(@TempDir Path workDir) {
        ExtractionOutcome outcome = ExtractionOutcome.classify(runObserving(workDir,
            "UnreachableWrapperTarget", "UnreachableWrapperTarget.wrapper(con)",
            "UnreachableWrapperTarget.wrapper", "Cut.twice"));

        assertEquals(ExtractionOutcome.Kind.TARGET_NOT_ENTERED, outcome.kind,
            "tested method in a dead branch must be a typed outcome, not a silent failure");
        assertNotNull(outcome.detail);
    }
}
