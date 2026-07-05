package teralizer.jpf;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPFConfigException;
import gov.nasa.jpf.symbc.SymbolicInstructionFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;

/** Pins fail-fast validation for SPF constraint collection flags that corrupt path-exact capture. */
class SymbolicInstructionFactoryConstraintCollectionConfigTest {

    private static final String PKG = "teralizer.jpf.targets.";
    private static final String TARGET = PKG + "SymbolicReturnTarget";

    @Example
    void constraintCollectionRejectsChoiceOptimization() throws IOException {
        assertConstraintCollectionRejects("symbolic.optimizechoices");
    }

    @Example
    void constraintCollectionRejectsSymbolicArrays() throws IOException {
        assertConstraintCollectionRejects("symbolic.arrays");
    }

    private static void assertConstraintCollectionRejects(String conflictingFlag) throws IOException {
        Config config = constraintCollectionConfig(conflictingFlag);

        try {
            new SymbolicInstructionFactory(config);
            Assert.fail("constraint collection mode should reject " + conflictingFlag + "=true");
        } catch (JPFConfigException e) {
            assertConfigExceptionMessage(conflictingFlag, e.getMessage());
        }
    }

    private static Config constraintCollectionConfig(String conflictingFlag) throws IOException {
        Path workDir = Files.createTempDirectory("constraint-collection-config-conflict");
        Config config = JpfListenerHarness.buildConfig(
            workDir,
            TARGET,
            TARGET + ".wrapper(sym)",
            TARGET + ".wrapper",
            PKG + "Cut.twice",
            false
        );
        config.setProperty(conflictingFlag, "true");
        return config;
    }

    private static void assertConfigExceptionMessage(String conflictingFlag, String message) {
        Assert.assertTrue(
            "message should name " + conflictingFlag + ": " + message,
            message.contains(conflictingFlag)
        );
        Assert.assertTrue(
            "message should explain that constraint collection mode requires the flag off: " + message,
            message.contains("constraint collection mode requires it off")
        );
    }
}
