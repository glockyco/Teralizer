package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins Teralizer-side capture of a symbolic {@link String} {@code isEmpty()} branch. SPF models
 * {@code s.isEmpty()} as the sound equality {@code s == ""}, so the captured constraint is
 * {@code value equals ""} on the empty branch and {@code value notequals ""} on the non-empty
 * branch — following the concrete seed (symcrete). Regression guard for the {@code isEmpty} handler
 * and its ingestion through {@code SpfToModelTransformer}.
 */
class StringIsEmptyCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";

    private JpfListenerHarness.Capture run(Path workDir, String wrapper) {
        return JpfListenerHarness.run(
            workDir,
            PKG + "StringIsEmptyTarget",
            PKG + "StringIsEmptyTarget." + wrapper + "(sym)",
            PKG + "StringIsEmptyTarget." + wrapper,
            PKG + "Cut.isEmptyBranch"
        );
    }

    @Test
    void capturesEmptyBranchForEmptySeed(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = run(workDir, "emptyWrapper");
        assertEquals(1, ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "empty seed takes the isEmpty-true branch (returns 1)");
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the isEmpty constraint must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"method\": \"equals\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"value\": \"\""),
            "empty branch must capture value.equals(\"\"), was: " + spec
        );
    }

    @Test
    void capturesNonEmptyBranchForNonEmptySeed(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = run(workDir, "nonEmptyWrapper");
        assertEquals(0, ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "non-empty seed takes the isEmpty-false branch (returns 0)");
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the negated isEmpty constraint must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Not\"")
                && spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"method\": \"equals\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"value\": \"\""),
            "non-empty branch must capture value != \"\", was: " + spec
        );
    }
}
