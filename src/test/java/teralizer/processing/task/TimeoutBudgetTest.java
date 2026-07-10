package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class TimeoutBudgetTest {

    @Example
    void junitStagesUseOriginalInitialOrGeneralizedBudget() {
        assertBudget(60,
            ProcessingStage.EXECUTE_TESTS_ORIGINAL,
            ProcessingStage.EXECUTE_TESTS_INITIAL);
        assertBudget(1800, ProcessingStage.EXECUTE_TESTS_GENERALIZED);
    }

    @Example
    void pitestStagesUsePitestBudget() {
        assertBudget(1800,
            ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
            ProcessingStage.COLLECT_PIT_DATA_INITIAL,
            ProcessingStage.COLLECT_PIT_DATA_GENERALIZED);
    }

    @Example
    void nonTimedStageHasNoTimeoutBudget() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> TimeoutBudget.forStage(ProcessingStage.BUILD_PROJECT_ORIGINAL));
    }

    private static void assertBudget(int expectedSeconds, ProcessingStage... stages) {
        for (ProcessingStage stage : stages) {
            Assert.assertEquals(stage.name(), expectedSeconds, TimeoutBudget.forStage(stage));
        }
    }
}
