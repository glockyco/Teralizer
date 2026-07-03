package teralizer.jpf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.Value;

/** Pins symbolic-output capture for boxed primitive returns. */
class BoxedPrimitiveReturnCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "BoxedComputedReturnTarget";

    @Example
    void capturesAllocatedBoxedLongComputedReturnAsSymbolic() throws IOException {
        JpfListenerHarness.Capture capture = run("allocatedLongWrapper", "Cut.boxedLongPlusOneAllocated");

        assertPrimitiveOutput(capture, "java.lang.Long", Long.valueOf(6L));
        assertSymbolicOutput(capture, "Long computed return");
    }

    @Example
    void capturesCacheRangeBoxedIntegerComputedReturnAsSymbolic() throws IOException {
        JpfListenerHarness.Capture capture = run("integerCacheWrapper", "Cut.boxedIntegerPlusOne");

        assertPrimitiveOutput(capture, "java.lang.Integer", Integer.valueOf(7));
        assertSymbolicOutput(capture, "Integer cache-range computed return");
    }

    @Example
    void capturesAllocatedBoxedBooleanIdentityReturnAsSymbolic() throws IOException {
        JpfListenerHarness.Capture capture =
            run("allocatedBooleanIdentityWrapper", "Cut.boxedBooleanIdentityAllocated");

        assertPrimitiveOutput(capture, "java.lang.Boolean", Boolean.TRUE);
        assertSymbolicOutput(capture, "Boolean identity return");
    }

    @Example
    void capturesOutsideCacheBoxedIntegerComputedReturnAsSymbolic() throws IOException {
        JpfListenerHarness.Capture capture = run("integerOutsideCacheWrapper", "Cut.boxedIntegerPlusOne");

        assertPrimitiveOutput(capture, "java.lang.Integer", Integer.valueOf(128));
        assertSymbolicOutput(capture, "Integer outside-cache computed return");
    }

    @Example
    void concreteBoxedReturnStaysNonSymbolic() throws IOException {
        JpfListenerHarness.Capture capture = run("concreteLongWrapper", "Cut.boxedConcreteLong");

        assertPrimitiveOutput(capture, "java.lang.Long", Long.valueOf(42L));
        assertNullModel(capture, "a genuinely concrete boxed return must not get an output model");
    }

    private static JpfListenerHarness.Capture run(String wrapper, String testedMethod) throws IOException {
        Path workDir = Files.createTempDirectory("boxed-return-capture");
        return JpfListenerHarness.run(
            workDir,
            TARGET,
            TARGET + "." + wrapper + "(sym)",
            TARGET + "." + wrapper,
            PKG + testedMethod,
            false
        );
    }

    private static void assertPrimitiveOutput(JpfListenerHarness.Capture capture, String javaType, Object value) {
        Value output = capture.getOutput().getReturnValue();
        Assert.assertEquals("concrete return type", javaType, output.getJavaType());
        Assert.assertEquals("concrete return value", value, ((PrimitiveValue) output).getValue());
    }

    private static void assertSymbolicOutput(JpfListenerHarness.Capture capture, String scenario) {
        String outputSpecification = capture.getOutputSpecificationJson();
        Assert.assertNotNull(scenario + " should capture an output model", outputSpecification);
        Assert.assertTrue(
            scenario + " should be expressed over the symbolic wrapper parameter, was: " + outputSpecification,
            outputSpecification.contains("\"_type\": \"Variable\"")
                && outputSpecification.contains("\"name\": \"value\"")
        );
    }

    private static void assertNullModel(JpfListenerHarness.Capture capture, String message) {
        Assert.assertEquals(message, "null", capture.getOutputSpecificationJson().trim());
    }
}
