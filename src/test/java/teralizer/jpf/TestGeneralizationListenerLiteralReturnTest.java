package teralizer.jpf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestGeneralizationListenerLiteralReturnTest {

    private static final String TARGET = "teralizer.jpf.targets.LiteralReturnTarget";
    private static final String CUT = "teralizer.jpf.targets.Cut";

    @Test
    void computedBooleanReturnIsLiteral(@TempDir Path workDir) {
        assertTrue(run(workDir, "wrapper", "literalBoolean").getOutputIsLiteral());
    }

    @Test
    void nonBooleanLiteralReturnIsLiteral(@TempDir Path workDir) {
        assertTrue(run(workDir, "literalIntWrapper", "literalInt").getOutputIsLiteral());
    }

    @Test
    void fieldReadBooleanReturnIsNotLiteral(@TempDir Path workDir) {
        assertFalse(run(workDir, "fieldBooleanWrapper", "fieldBoolean").getOutputIsLiteral());
    }

    @Test
    void arithmeticReturnIsNotLiteral(@TempDir Path workDir) {
        assertFalse(run(workDir, "divideWrapper", "divide").getOutputIsLiteral());
    }

    private static JpfListenerHarness.Capture run(Path workDir, String wrapper, String testedMethod) {
        String wrapperMethod = TARGET + "." + wrapper;
        return JpfListenerHarness.run(
            workDir,
            TARGET,
            wrapperMethod + "(con)",
            wrapperMethod,
            CUT + "." + testedMethod
        );
    }

}
