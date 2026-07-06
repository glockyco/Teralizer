package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;

public class TestExecutionTaskTest {

    @Example
    void scalesGeneralizedTimeoutByPropertyTriesOverBaselineBudget() {
        Assert.assertEquals(60L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(0, 100, 60, 1600, 3600));
        Assert.assertEquals(60L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(16, 100, 60, 1600, 3600));
        Assert.assertEquals(120L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(17, 100, 60, 1600, 3600));
        Assert.assertEquals(540L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(131, 100, 60, 1600, 3600));
    }

    @Example
    void capsScaledGeneralizedTimeoutAtCeiling() {
        // 1000 properties x 100 tries / 1600 budget -> 63x flat = 75600s uncapped.
        Assert.assertEquals(3600L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(1000, 100, 1200, 1600, 3600));
        // The ceiling never trims below the flat budget a plain suite run is entitled to.
        Assert.assertEquals(1200L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(0, 100, 1200, 1600, 600));
    }
}
