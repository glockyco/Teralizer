package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Expression;

/** Pins sound ASCII {@code Character.isWhitespace(char)} capture in constraint collection. */
class CharWhitespaceCaptureTest {

    private static final String TARGET = "teralizer.jpf.targets.CharWhitespaceTarget";

    @Example
    void whitespaceReturnSeedCapturesTrueAsciiIntervalWithoutConcretization() throws IOException {
        TestGeneralizationListener listener = run("whitespaceReturnWrapper", "isWhitespace");

        assertAsciiInterval(listener, 9, 13);
    }

    @Example
    void nonWhitespaceReturnSeedCapturesFalseAsciiIntervalWithoutConcretization() throws IOException {
        TestGeneralizationListener listener = run("nonWhitespaceReturnWrapper", "isWhitespace");

        assertAsciiInterval(listener, 33, 127);
    }

    @Example
    void branchConsumerCapturesWhitespaceAsciiIntervalWithoutConcretization() throws IOException {
        TestGeneralizationListener listener = run("branchWrapper", "whitespaceBranch");

        assertAsciiInterval(listener, 28, 32);
    }

    @Example
    void nonAsciiSeedFallsThroughToNativePeerConcretization() throws IOException {
        TestGeneralizationListener listener = run("nonAsciiWrapper", "isWhitespace");

        Assert.assertTrue(
            "non-ASCII Character.isWhitespace should stay on the native path",
            listener.getConcretizationEvents() >= 1
        );
    }

    private static TestGeneralizationListener run(String wrapper, String testedMethod) throws IOException {
        Path workDir = Files.createTempDirectory("char-whitespace-capture");
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

    private static void assertAsciiInterval(TestGeneralizationListener listener, int lower, int upper) {
        Assert.assertEquals("ASCII predicate handling should not cross a native peer", 0,
            listener.getConcretizationEvents());
        CapturedInvocation invocation = listener.getInvocation();
        Assert.assertNotNull("tested method invocation captured", invocation);
        Expression modelInput = invocation.getModelInput();
        Assert.assertNotNull("ASCII predicate should capture a path condition", modelInput);
        String rendered = modelInput.toString();
        Assert.assertTrue("path condition should name the char parameter, was: " + rendered,
            rendered.contains("c"));
        Assert.assertTrue("path condition should include lower bound " + lower + ", was: " + rendered,
            rendered.contains("c >= " + lower));
        Assert.assertTrue("path condition should include upper bound " + upper + ", was: " + rendered,
            rendered.contains("c <= " + upper));
    }
}
