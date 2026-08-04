package teralizer.processing.diagnostics;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;
import teralizer.util.ConsoleCommandException;

public class TaskDiagnosticClassifierCommandTest {

    @Example
    void deadMinionIsTypedAsMinionDied() throws Exception {
        Assert.assertEquals(
            TaskDiagnosticCodes.MINION_DIED,
            classify("PIT >> INFO : MINION : Error: Could not find or load main class @{argLine}\n"
                + "[ERROR] Failed to execute goal org.pitest:pitest-maven:1.17.0:mutationCoverage\n"));
    }

    @Example
    void unusablePluginIsTypedAsPluginUnusable() throws Exception {
        Assert.assertEquals(
            TaskDiagnosticCodes.PLUGIN_UNUSABLE,
            classify("[ERROR] fail: Cannot construct org.pitest.mutationtest.MutationCoverageReport "
                + "as it does not have a no-args constructor\n"));
    }

    @Example
    void failingUnmutatedSuiteIsTypedAsSuiteNotGreen() throws Exception {
        Assert.assertEquals(
            TaskDiagnosticCodes.SUITE_NOT_GREEN,
            classify("[ERROR] mutationCoverage failed: 3 tests did not pass without mutation\n"));
    }

    @Example
    void invisibleTestsAreTypedAsNoTestsFound() throws Exception {
        Assert.assertEquals(
            TaskDiagnosticCodes.NO_TESTS_FOUND,
            classify("PIT >> INFO : MINION : No tests found in _FooTest_Generalized_bar_1_Test\n"));
    }

    @Example
    void unrecognizedCommandOutputKeepsTheFallback() throws Exception {
        Assert.assertEquals(
            TaskDiagnosticCodes.LISTENER_BUG,
            classify("[ERROR] something nobody has classified yet\n"));
    }

    private static String classify(String capturedOutput) throws Exception {
        Path dir = Files.createTempDirectory("classifier-command-test");
        Path out = Files.write(dir.resolve("out.txt"), capturedOutput.getBytes(StandardCharsets.UTF_8));
        Path err = Files.write(dir.resolve("err.txt"), new byte[0]);
        ConsoleCommandException failure = new ConsoleCommandException(java.util.Arrays.asList("mvn", "pitest:mutationCoverage"), 1, out, err);
        return TaskDiagnosticClassifier
            .classify(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED, failure)
            .reasonCode();
    }
}
