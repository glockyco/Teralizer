package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

/**
 * A box-unbox round trip preserves the symbolic expression through GETFIELD and narrowing, so the
 * concretization event count marks a boundary crossing rather than a loss, and license logic must
 * not read it as one.
 */
class BoxedUnboxRoundTripCaptureTest {

    private static final String TARGET = "teralizer.jpf.targets.BoxedUnboxRoundTripTarget";
    private static final String LONG_VALUE_OF = "java.lang.Long.valueOf(J)Ljava/lang/Long;";

    @Example
    void intRoundTripKeepsSymbolicOutputAndCountsValueOfBoundary() throws IOException {
        TestGeneralizationListener listener = run("intRoundTripWrapper", "intRoundTrip");

        assertSymbolicRoundTripCaptured(listener);
        assertValueOfBoundaryRecorded(listener);
    }

    @Example
    void intValueRoundTripKeepsSymbolicOutputAndCountsValueOfBoundary() throws IOException {
        TestGeneralizationListener listener = run("intValueRoundTripWrapper", "intValueRoundTrip");

        assertSymbolicRoundTripCaptured(listener);
        assertValueOfBoundaryRecorded(listener);
    }

    @Example
    void longRoundTripKeepsSymbolicOutputAndCountsValueOfBoundary() throws IOException {
        TestGeneralizationListener listener = run("longRoundTripWrapper", "longRoundTrip");

        assertSymbolicRoundTripCaptured(listener);
        assertValueOfBoundaryRecorded(listener);
    }

    private static TestGeneralizationListener run(String wrapper, String testedMethod) throws IOException {
        Path workDir = Files.createTempDirectory("boxed-unbox-round-trip");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            TARGET,
            TARGET + "." + wrapper + "(sym)",
            TARGET + "." + wrapper,
            TARGET + "." + testedMethod,
            false
        );
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        jpf.run();

        if (jpf.foundErrors()) {
            Assert.fail("JPF reported errors for " + testedMethod + ": " + jpf.getSearchErrors());
        }
        Assert.assertTrue("JPF VM initialized", jpf.getVM().isInitialized());
        return listener;
    }

    private static void assertSymbolicRoundTripCaptured(TestGeneralizationListener listener) {
        CapturedInvocation invocation = listener.getInvocation();
        Assert.assertNotNull("tested method invocation captured", invocation);
        Assert.assertEquals(
            "round-tripped primitive should keep the symbolic wrapper parameter",
            new Variable("value", TypeDomain.INTEGER),
            invocation.getModelOutput()
        );
    }

    private static void assertValueOfBoundaryRecorded(TestGeneralizationListener listener) {
        Map<String, Integer> concretizedMethods = listener.getConcretizedMethods();
        Assert.assertTrue(
            "expected Long.valueOf to be recorded as a native boundary",
            concretizedMethods.containsKey(LONG_VALUE_OF)
        );
        Assert.assertTrue(
            "expected at least one concretization boundary event",
            listener.getConcretizationEvents() >= 1
        );
    }
}
