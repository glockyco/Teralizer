package teralizer.jpf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.PrimitiveValue;

/** Pins constraint collection path-condition capture for symbolic float comparisons. */
class FloatCompareCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "FloatCompareTarget";

    @Example
    void trueSeedCapturesGreaterThanComparator() throws IOException {
        JpfListenerHarness.Capture capture = run("trueSeedWrapper");

        assertComparator(capture, ">", "true seed 2.0f > 1.0f must capture the greater-than partition");
        assertBooleanReturn(capture, true, "true seed returns the a > b branch");
    }

    @Example
    void falseSeedCapturesLessThanComparator() throws IOException {
        JpfListenerHarness.Capture capture = run("falseSeedWrapper");

        assertComparator(capture, "<", "false seed 1.0f < 2.0f must capture the less-than partition");
        assertBooleanReturn(capture, false, "false seed returns the a > b false branch");
    }

    private static JpfListenerHarness.Capture run(String wrapper) throws IOException {
        Path workDir = Files.createTempDirectory("float-compare-capture");
        return JpfListenerHarness.run(
            workDir,
            TARGET,
            TARGET + "." + wrapper + "(sym#sym)",
            TARGET + "." + wrapper,
            TARGET + ".floatExceeds",
            false
        );
    }

    private static void assertBooleanReturn(
        JpfListenerHarness.Capture capture,
        boolean expected,
        String message
    ) {
        PrimitiveValue value = (PrimitiveValue) capture.getOutput().getReturnValue();
        Assert.assertEquals("boolean", value.getJavaType());
        Assert.assertEquals(message, Boolean.valueOf(expected), value.getValue());
    }

    private static void assertComparator(
        JpfListenerHarness.Capture capture,
        String expectedSymbol,
        String message
    ) {
        String spec = capture.getInputSpecificationJson();
        Assert.assertNotNull("the float compare path condition must be captured", spec);
        Assert.assertTrue(
            message + ", was: " + spec,
            spec.contains("\"_type\": \"Operation\"")
                && spec.contains("\"name\": \"a\"")
                && spec.contains("\"name\": \"b\"")
                && spec.contains("\"symbol\": \"" + expectedSymbol + "\"")
        );
    }
}
