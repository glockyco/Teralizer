package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins capture of a symbolic {@link String} return as an output oracle. An identity return
 * ({@code return s}) is captured as the parameter itself; a computed return ({@code s.concat("!")})
 * is captured as a concat {@code Operation} rather than crashing the listener transform. Regression
 * guard for {@code SpfToModelTransformer} representing SPF's derived String operators and for the
 * {@code CONCAT} fold arm.
 */
class StringReturnCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";

    private JpfListenerHarness.Capture run(Path workDir, String wrapper, String tested) {
        return JpfListenerHarness.run(
            workDir,
            PKG + "StringEchoTarget",
            PKG + "StringEchoTarget." + wrapper + "(sym)",
            PKG + "StringEchoTarget." + wrapper,
            PKG + "Cut." + tested
        );
    }

    @Test
    void capturesIdentityStringReturnAsParameter(@TempDir Path workDir) {
        String spec = run(workDir, "echoWrapper", "echo").getOutputSpecificationJson();
        assertNotNull(spec, "identity String return must be captured as an output oracle");
        assertTrue(
            spec.contains("\"_type\": \"VariableString\"") && spec.contains("\"name\": \"value\""),
            "identity return must render as the parameter, was: " + spec
        );
    }

    @Test
    void capturesConcatStringReturnAsOperation(@TempDir Path workDir) {
        String spec = run(workDir, "concatWrapper", "concatTail").getOutputSpecificationJson();
        assertNotNull(spec, "computed String return must be captured, not crash the transform");
        assertTrue(
            spec.contains("\"symbol\": \"concat\"") && spec.contains("\"value\": \"!\""),
            "concat return must be a concat Operation over the parameter, was: " + spec
        );
    }
}
