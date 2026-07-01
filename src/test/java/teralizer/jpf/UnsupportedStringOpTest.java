package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A method under test that reaches a String operation {@code SymbolicStringHandler} does not
 * implement ({@code compareTo}) must surface as a typed {@code UNSUPPORTED_TERM} exclusion, not an
 * untyped crash that would fail the whole run.
 */
class UnsupportedStringOpTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void compareToYieldsUnsupportedTermOutcome(@TempDir Path workDir) {
        ExtractionOutcome outcome = JpfListenerHarness.runOutcome(
            workDir,
            PKG + "CompareToTarget",
            PKG + "CompareToTarget.wrapper(sym)",
            PKG + "CompareToTarget.wrapper",
            PKG + "Cut.compareToBranch");

        assertEquals(ExtractionOutcome.Kind.UNSUPPORTED_TERM, outcome.getKind());
    }
}
