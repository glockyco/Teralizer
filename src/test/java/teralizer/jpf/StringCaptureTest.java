package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;

/**
 * Pins sound capture of a symbolic String path condition. A MUT that branches on
 * {@code s.equals("foo")} must have its constraint follow the concrete seed's branch (symcrete), so
 * the captured output and comparator are mutually consistent and the seed satisfies its own
 * captured predicate. Regression guard for the symcrete choice-selection fix in jpf-symbc's
 * {@code SymbolicStringHandler}; without it the false branch is captured regardless of the seed.
 */
class StringCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void capturesTrueBranchWhenSeedMatches(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "StringEqualsTarget",
            PKG + "StringEqualsTarget.wrapper(sym)",
            PKG + "StringEqualsTarget.wrapper",
            PKG + "Cut.equalsBranch"
        );

        assertEquals(
            1,
            ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "seed \"foo\" takes the equals-true branch (returns 1)"
        );
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the String path condition must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"method\": \"equals\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"value\": \"foo\""),
            "the captured constraint must be value.equals(\"foo\") on the true branch, was: " + spec
        );
    }

    @Test
    void capturesFalseBranchWhenSeedDiffers(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture = JpfListenerHarness.run(
            workDir,
            PKG + "StringEqualsFalseTarget",
            PKG + "StringEqualsFalseTarget.wrapper(sym)",
            PKG + "StringEqualsFalseTarget.wrapper",
            PKG + "Cut.equalsBranch"
        );

        assertEquals(
            0,
            ((PrimitiveValue) capture.getOutput().getReturnValue()).getValue(),
            "seed \"bar\" takes the equals-false branch (returns 0)"
        );
        String spec = capture.getInputSpecificationJson();
        assertNotNull(spec, "the String path condition must be captured");
        assertTrue(
            spec.contains("\"_type\": \"Not\"")
                && spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"method\": \"equals\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"value\": \"foo\""),
            "the captured constraint must be the negation value != \"foo\", satisfied by \"bar\", was: " + spec
        );
    }
}
