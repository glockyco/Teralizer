package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class TestExecutionJacocoSkipTest {

    @Example
    void originalExecutionSkipsJacocoWhenOriginalPitestDisabled() {
        Assert.assertTrue("EXECUTE_TESTS_ORIGINAL with pitestOriginalEnabled=false should skip jacoco",
            TestExecutionTask.jacocoSkipped(ProcessingStage.EXECUTE_TESTS_ORIGINAL, false));
    }

    @Example
    void originalExecutionInstrumentsJacocoWhenOriginalPitestEnabled() {
        Assert.assertFalse("EXECUTE_TESTS_ORIGINAL with pitestOriginalEnabled=true should instrument jacoco",
            TestExecutionTask.jacocoSkipped(ProcessingStage.EXECUTE_TESTS_ORIGINAL, true));
    }

    @Example
    void initialExecutionAlwaysInstrumentsJacoco() {
        Assert.assertFalse("EXECUTE_TESTS_INITIAL with pitestOriginalEnabled=false must instrument jacoco",
            TestExecutionTask.jacocoSkipped(ProcessingStage.EXECUTE_TESTS_INITIAL, false));
        Assert.assertFalse("EXECUTE_TESTS_INITIAL with pitestOriginalEnabled=true must instrument jacoco",
            TestExecutionTask.jacocoSkipped(ProcessingStage.EXECUTE_TESTS_INITIAL, true));
    }

    @Example
    void generalizedExecutionAlwaysInstrumentsJacoco() {
        Assert.assertFalse("EXECUTE_TESTS_GENERALIZED with pitestOriginalEnabled=false must instrument jacoco",
            TestExecutionTask.jacocoSkipped(ProcessingStage.EXECUTE_TESTS_GENERALIZED, false));
    }
}
