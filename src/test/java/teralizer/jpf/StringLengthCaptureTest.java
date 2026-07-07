package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;

class StringLengthCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";

    private JpfListenerHarness.Capture run(Path workDir, String wrapper) {
        return JpfListenerHarness.run(
            workDir,
            PKG + "StringLengthTarget",
            PKG + "StringLengthTarget." + wrapper + "(sym)",
            PKG + "StringLengthTarget." + wrapper,
            PKG + "Cut.lengthZeroBranch"
        );
    }

    @Test
    void capturesLengthZeroBranchForEmptySeed(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = run(workDir, "emptyWrapper");
        assertEquals(1, ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "empty seed takes the length-zero-true branch (returns 1)");
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the length constraint must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Operation\"")
                && spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"method\": \"length\"")
                && spec.contains("\"symbol\": \"==\"")
                && spec.contains("\"value\": 0")
                && spec.contains("\"domain\": \"INTEGER\""),
            "empty branch must capture value.length() == 0, was: " + spec
        );
    }

    @Test
    void capturesLengthNonZeroBranchForNonEmptySeed(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = run(workDir, "nonEmptyWrapper");
        assertEquals(0, ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "non-empty seed takes the length-zero-false branch (returns 0)");
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the negated length constraint must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Operation\"")
                && spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"method\": \"length\"")
                && spec.contains("\"symbol\": \"!=\"")
                && spec.contains("\"value\": 0")
                && spec.contains("\"domain\": \"INTEGER\""),
            "non-empty branch must capture value.length() != 0, was: " + spec
        );
    }
}
