package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;

public class TestGeneralizationListenerConcretizationTest {

    private static final String PKG = "teralizer.jpf.targets.";

    @Example
    void leavesCounterAtZeroForPureArithmeticMut() throws IOException {
        TestGeneralizationListener listener = run(
            PKG + "SymbolicReturnTarget",
            PKG + "SymbolicReturnTarget.wrapper(sym)",
            PKG + "SymbolicReturnTarget.wrapper",
            PKG + "Cut.twice"
        );

        Assert.assertEquals(0, listener.getConcretizationEvents());
    }

    @Example
    void countsSymbolicValueEnteringNativeMethod() throws IOException {
        TestGeneralizationListener listener = run(
            PKG + "NativeConcretizationTarget",
            PKG + "NativeConcretizationTarget.wrapper(sym)",
            PKG + "NativeConcretizationTarget.wrapper",
            PKG + "NativeConcretizationTarget.nativeArrayCopy"
        );

        Assert.assertTrue(
            "expected a symbolic primitive entering System.arraycopy to cross a native peer",
            listener.getConcretizationEvents() > 0);
    }

    private static TestGeneralizationListener run(
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN
    ) throws IOException {
        Path workDir = Files.createTempDirectory("jpf-concretization-events");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            targetClassQN,
            symbolicMethod,
            instrumentedMethodQN,
            testedMethodQN);
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        jpf.run();

        if (jpf.foundErrors()) {
            Assert.fail("JPF reported errors: " + jpf.getSearchErrors());
        }
        Assert.assertTrue("JPF VM initialized", jpf.getVM().isInitialized());
        Assert.assertNotNull("tested method invocation captured", listener.getInvocation());
        return listener;
    }
}
