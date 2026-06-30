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
 * <p>A boxed wrapper or {@link String} argument/return is a JPF {@code ElementInfo} on the heap;
 * recording it with {@code String.valueOf(...)} yields object identity ({@code java.lang.Integer@<hash>})
 * instead of the underlying value, which produces uncompilable generated tests and suppresses
 * generalizations (the {@code MissingValueFilter} rejects the unrenderable value). These tests pin
 * value-based capture across the distinct rendering branches (numeric wrapper, boolean, char code
 * point, and String via {@code asString()}). See the I1 finding in
 * {@code docs/plans/2026-06-29-beyond-jarvis-census-findings.md}.
 *
 * <p>Each scenario's instrumented method is the static {@code wrapper(value)} on its target class;
 * {@link #runWrapper(Path, String, String)} encodes that convention.
 */
class TestGeneralizationListenerCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Test
    void capturesBoxedIntegerArgumentByValueNotIdentity(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedIdentityTarget", PKG + "Cut.boxedIdentity");

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
            runWrapper(workDir, PKG + "BoxedIdentityTarget", PKG + "Cut.boxedIdentity");

        MethodArgument output = capture.getOutputValue();
        assertEquals("java.lang.Integer", output.getType(), "return type");
        assertEquals(
            "7", output.getValue(),
            "a boxed Integer return must be captured by its value, not by JPF object identity"
        );
    }

    @Test
    void capturesBoxedBooleanArgumentAndReturnByValue(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedBooleanTarget", PKG + "Cut.boxedNegate");

        assertEquals("java.lang.Boolean", capture.getInputValues().get(0).getType(), "argument type");
        assertEquals("true", capture.getInputValues().get(0).getValue(), "boxed Boolean argument");
        assertEquals("java.lang.Boolean", capture.getOutputValue().getType(), "return type");
        assertEquals("false", capture.getOutputValue().getValue(), "negated boxed Boolean return");
    }

    @Test
    void capturesBoxedCharacterAsIntegerCodePoint(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedCharTarget", PKG + "Cut.boxedChar");

        assertEquals("java.lang.Character", capture.getInputValues().get(0).getType(), "argument type");
        assertEquals("65", capture.getInputValues().get(0).getValue(), "'A' captured as its code point");
        assertEquals("java.lang.Character", capture.getOutputValue().getType(), "return type");
        assertEquals("65", capture.getOutputValue().getValue(), "'A' return captured as its code point");
    }

    @Test
    void capturesStringReturnByValue(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "StringReturnTarget", PKG + "Cut.describe");

        assertEquals("java.lang.Integer", capture.getInputValues().get(0).getType(), "argument type");
        assertEquals("7", capture.getInputValues().get(0).getValue(), "boxed Integer argument");
        assertEquals("java.lang.String", capture.getOutputValue().getType(), "return type");
        assertEquals("n=7", capture.getOutputValue().getValue(), "String return captured by value");
    }

    /**
     * Run the listener over a target whose instrumented method is the static {@code wrapper(value)}
     * with a single concrete parameter, invoking {@code testedMethodQN}.
     */
    private static JpfListenerHarness.Capture runWrapper(Path workDir, String targetClassQN, String testedMethodQN) {
        return JpfListenerHarness.run(
            workDir,
            targetClassQN,
            targetClassQN + ".wrapper(con)",
            targetClassQN + ".wrapper",
            testedMethodQN
        );
    }
}
