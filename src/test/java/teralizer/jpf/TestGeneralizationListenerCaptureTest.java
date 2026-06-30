package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.StringValue;
import teralizer.domain.Value;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * In-process JPF regression tests for {@link TestGeneralizationListener}'s concrete-value capture.
 *
 * <p>A boxed wrapper or {@link String} argument/return is a JPF {@code ElementInfo} on the heap;
 * capture must read it by value into a typed {@link Value} (a {@link PrimitiveValue} holding the host
 * wrapper, or a {@link StringValue}) rather than its object identity. These tests pin value-based
 * capture across the distinct kinds (numeric wrapper, boolean, char, String), and confirm that a
 * primitive {@code boolean} — which JPF supplies from an int slot — is coerced to a typed boolean.
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

        List<Value> inputs = capture.getInputValues();
        assertEquals(1, inputs.size(), "the wrapper declares exactly one argument");
        assertEquals("java.lang.Integer", inputs.get(0).getJavaType(), "argument type");
        assertEquals(
            Integer.valueOf(7), ((PrimitiveValue) inputs.get(0)).getValue(),
            "a boxed Integer argument must be captured by its value, not by JPF object identity"
        );
    }

    @Test
    void capturesBoxedIntegerReturnByValueNotIdentity(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedIdentityTarget", PKG + "Cut.boxedIdentity");

        Value output = capture.getOutput().getReturnValue();
        assertEquals("java.lang.Integer", output.getJavaType(), "return type");
        assertEquals(
            Integer.valueOf(7), ((PrimitiveValue) output).getValue(),
            "a boxed Integer return must be captured by its value, not by JPF object identity"
        );
    }

    @Test
    void capturesBoxedBooleanArgumentAndReturnByValue(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedBooleanTarget", PKG + "Cut.boxedNegate");

        Value input = capture.getInputValues().get(0);
        assertEquals("java.lang.Boolean", input.getJavaType(), "argument type");
        assertEquals(Boolean.TRUE, ((PrimitiveValue) input).getValue(), "boxed Boolean argument");

        Value output = capture.getOutput().getReturnValue();
        assertEquals("java.lang.Boolean", output.getJavaType(), "return type");
        assertEquals(Boolean.FALSE, ((PrimitiveValue) output).getValue(), "negated boxed Boolean return");
    }

    @Test
    void capturesPrimitiveBooleanArgumentAndReturnByValue(@TempDir Path workDir) {
        // Empirical check: a primitive boolean arrives from an int slot, not as a host Boolean;
        // capture must coerce it to a typed boolean value (input true, negated return false).
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "PrimitiveBooleanTarget", PKG + "Cut.negate");

        Value input = capture.getInputValues().get(0);
        assertEquals("boolean", input.getJavaType(), "argument type");
        assertEquals(Boolean.TRUE, ((PrimitiveValue) input).getValue(), "primitive boolean argument");

        Value output = capture.getOutput().getReturnValue();
        assertEquals("boolean", output.getJavaType(), "return type");
        assertEquals(Boolean.FALSE, ((PrimitiveValue) output).getValue(), "negated primitive boolean return");
    }

    @Test
    void capturesBoxedCharacterAsHostCharacter(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "BoxedCharTarget", PKG + "Cut.boxedChar");

        Value input = capture.getInputValues().get(0);
        assertEquals("java.lang.Character", input.getJavaType(), "argument type");
        assertEquals(Character.valueOf('A'), ((PrimitiveValue) input).getValue(), "'A' captured as a host Character");

        Value output = capture.getOutput().getReturnValue();
        assertEquals("java.lang.Character", output.getJavaType(), "return type");
        assertEquals(Character.valueOf('A'), ((PrimitiveValue) output).getValue(), "'A' return captured as a host Character");
    }

    @Test
    void capturesStringReturnByValue(@TempDir Path workDir) {
        JpfListenerHarness.Capture capture =
            runWrapper(workDir, PKG + "StringReturnTarget", PKG + "Cut.describe");

        Value input = capture.getInputValues().get(0);
        assertEquals("java.lang.Integer", input.getJavaType(), "argument type");
        assertEquals(Integer.valueOf(7), ((PrimitiveValue) input).getValue(), "boxed Integer argument");

        Value output = capture.getOutput().getReturnValue();
        assertEquals("java.lang.String", output.getJavaType(), "return type");
        assertEquals("n=7", ((StringValue) output).getValue(), "String return captured by value");
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
