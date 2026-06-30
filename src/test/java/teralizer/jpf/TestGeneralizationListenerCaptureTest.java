package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.MethodArgument;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * In-process JPF regression tests for {@link TestGeneralizationListener}'s concrete-value capture.
 *
 * <p>A boxed wrapper argument or return is a JPF {@code ElementInfo} on the heap; recording it with
 * {@code String.valueOf(...)} yields object identity ({@code java.lang.Integer@<hash>}) instead of
 * the underlying value, which produces uncompilable generated tests and suppresses generalizations
 * (the {@code MissingValueFilter} rejects the unrenderable value). These tests pin the value-based
 * capture. See the I1 finding in {@code docs/plans/2026-06-29-beyond-jarvis-census-findings.md}.
 */
class TestGeneralizationListenerCaptureTest {

    private static final String TARGET = "teralizer.jpf.targets.BoxedIdentityTarget";
    private static final String SYMBOLIC_METHOD = "teralizer.jpf.targets.BoxedIdentityTarget.wrapper(con)";
    private static final String INSTRUMENTED_METHOD = "teralizer.jpf.targets.BoxedIdentityTarget.wrapper";
    private static final String TESTED_METHOD = "teralizer.jpf.targets.Cut.boxedIdentity";

    @Test
    void capturesBoxedIntegerArgumentByValueNotIdentity(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            JpfListenerHarness.run(workDir, TARGET, SYMBOLIC_METHOD, INSTRUMENTED_METHOD, TESTED_METHOD);

        List<MethodArgument> inputs = capture.getInputValues();
        assertEquals(1, inputs.size(), "the wrapper declares exactly one argument");
        assertEquals("java.lang.Integer", inputs.get(0).getType(), "argument type");
        assertEquals(
            "7", inputs.get(0).getValue(),
            "a boxed Integer argument must be captured by its value, not by JPF object identity"
        );
    }

    @Test
    void capturesBoxedIntegerReturnByValueNotIdentity(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            JpfListenerHarness.run(workDir, TARGET, SYMBOLIC_METHOD, INSTRUMENTED_METHOD, TESTED_METHOD);

        MethodArgument output = capture.getOutputValue();
        assertEquals("java.lang.Integer", output.getType(), "return type");
        assertEquals(
            "7", output.getValue(),
            "a boxed Integer return must be captured by its value, not by JPF object identity"
        );
    }
}
