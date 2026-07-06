package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;

public class TestExecutionTaskTest {

    @Example
    void scalesGeneralizedTimeoutByPropertyTriesOverBaselineBudget() {
        Assert.assertEquals(60L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(0, 100, 60, 1600));
        Assert.assertEquals(60L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(16, 100, 60, 1600));
        Assert.assertEquals(120L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(17, 100, 60, 1600));
        Assert.assertEquals(540L, TestExecutionTask.scaledGeneralizedTimeoutSeconds(131, 100, 60, 1600));
    }
}
