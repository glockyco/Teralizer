package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;

class DivergenceRiskCaptureTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "DivergenceRiskTarget";

    @Example
    void straightLineMessageConstructionAfterSymbolicGuardIsNotRisk() throws IOException {
        TestGeneralizationListener listener = run(TARGET, "straightLineMessageWrapper", "straightLineMessage");

        Assert.assertTrue("message construction should cross a native boundary",
            listener.getConcretizationEvents() > 0);
        Assert.assertFalse("application ATHROW after straight-line message construction is safe",
            listener.getPostConcretizationDivergenceRisk());
    }

    @Example
    void concreteApplicationBranchAfterConcretizationIsRisk() throws IOException {
        TestGeneralizationListener listener = run(TARGET, "concreteBranchWrapper", "concreteBranchAfterMessage");

        Assert.assertTrue("message construction should cross a native boundary",
            listener.getConcretizationEvents() > 0);
        Assert.assertTrue("concrete application branch after a native boundary can diverge silently",
            listener.getPostConcretizationDivergenceRisk());
    }

    @Example
    void nativeOriginCapturedThrowAfterSymbolicNativeBoundaryIsRisk() throws IOException {
        TestGeneralizationListener listener = run(TARGET, "nativeThrowWrapper", "nativeThrow");

        Assert.assertTrue("System.arraycopy should receive a symbolic length at the native boundary",
            listener.getConcretizationEvents() > 0);
        Assert.assertTrue("exception raised from the native boundary has no path-condition proof",
            listener.getPostConcretizationDivergenceRisk());
    }

    @Example
    void symbolicApplicationBranchAfterConcretizationIsNotRisk() throws IOException {
        TestGeneralizationListener listener = run(TARGET, "symbolicBranchWrapper", "symbolicBranchAfterMessage");

        Assert.assertTrue("message construction should cross a native boundary",
            listener.getConcretizationEvents() > 0);
        Assert.assertFalse("symbolic post-boundary branch leaves path-condition evidence",
            listener.getPostConcretizationDivergenceRisk());
    }

    @Example
    void noConcretizationEventsIsNotRisk() throws IOException {
        TestGeneralizationListener listener = run(PKG + "SymbolicReturnTarget", "wrapper", "Cut.twice");

        Assert.assertEquals("pure arithmetic target should not cross a native boundary", 0,
            listener.getConcretizationEvents());
        Assert.assertFalse("no native boundary means no post-concretization divergence",
            listener.getPostConcretizationDivergenceRisk());
    }

    private static TestGeneralizationListener run(String targetClassQN, String wrapper, String testedMethod)
        throws IOException {
        Path workDir = Files.createTempDirectory("divergence-risk-capture");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            targetClassQN,
            targetClassQN + "." + wrapper + "(sym)",
            targetClassQN + "." + wrapper,
            qualifiedTestedMethod(targetClassQN, testedMethod)
        );
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        jpf.run();
        if (jpf.foundErrors()) {
            Assert.fail("JPF reported errors: " + jpf.getSearchErrors());
        }
        Assert.assertTrue("JPF VM initialized", jpf.getVM().isInitialized());
        Assert.assertNotNull("listener should capture the invocation", listener.getInvocation());
        return listener;
    }

    private static String qualifiedTestedMethod(String targetClassQN, String testedMethod) {
        if (testedMethod.indexOf('.') >= 0) {
            return PKG + testedMethod;
        }
        return targetClassQN + "." + testedMethod;
    }
}
