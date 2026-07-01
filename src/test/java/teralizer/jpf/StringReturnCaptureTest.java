package teralizer.jpf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import teralizer.domain.Model;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins capture of a symbolic {@link String} return as an output oracle. An identity return
 * ({@code return s}) is captured as the parameter itself; a computed return ({@code s.concat("!")})
 * is captured as a concat {@code Invocation} rather than crashing the listener transform. Regression
 * guard for {@code SpfToModelTransformer} representing SPF's derived String operators and for the
 * {@code Invocation} fold arm.
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

    private String renderOutput(Path workDir, String wrapper, String tested) {
        String spec = run(workDir, wrapper, tested).getOutputSpecificationJson();
        assertNotNull(spec, "computed String return must be captured");
        Model model = new JsonToModelTransformer().transform(spec);
        return new ModelToJavaTransformer().transform(model);
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
    void capturesConcatStringReturnAsInvocation(@TempDir Path workDir) {
        String spec = run(workDir, "concatWrapper", "concatTail").getOutputSpecificationJson();
        assertNotNull(spec, "computed String return must be captured, not crash the transform");
        assertTrue(
            spec.contains("\"_type\": \"Invocation\"")
                && spec.contains("\"method\": \"concat\"")
                && spec.contains("\"name\": \"value\"")
                && spec.contains("\"value\": \"!\""),
            "concat return must be a concat Invocation over the parameter, was: " + spec
        );
    }

    @Test
    void rendersTrimReturn(@TempDir Path workDir) {
        assertEquals("(_p_.value.trim())", renderOutput(workDir, "trimWrapper", "trimmed"));
    }

    @Test
    void rendersLowerCaseReturn(@TempDir Path workDir) {
        assertEquals("(_p_.value.toLowerCase())", renderOutput(workDir, "lowerWrapper", "lowered"));
    }

    @Test
    void rendersUpperCaseReturn(@TempDir Path workDir) {
        assertEquals("(_p_.value.toUpperCase())", renderOutput(workDir, "upperWrapper", "uppered"));
    }

    @Test
    void rendersReplaceReturn(@TempDir Path workDir) {
        assertEquals("(_p_.value.replace(\"o\", \"a\"))", renderOutput(workDir, "replaceWrapper", "replaced"));
    }
}
