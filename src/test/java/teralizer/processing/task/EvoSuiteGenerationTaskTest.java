package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.TestFramework;

public class EvoSuiteGenerationTaskTest {

    @Example
    void emitsJUnit4TestsForJUnit3Projects() {
        Assert.assertEquals("JUNIT4", EvoSuiteGenerationTask.testFormatFor(TestFramework.JUNIT_3));
    }

    @Example
    void preservesJUnit4GenerationFormat() {
        Assert.assertEquals("JUNIT4", EvoSuiteGenerationTask.testFormatFor(TestFramework.JUNIT_4));
    }

    @Example
    void emitsJUnit5TestsForJUnit5Projects() {
        Assert.assertEquals("JUNIT5", EvoSuiteGenerationTask.testFormatFor(TestFramework.JUNIT_5));
    }

    @Example
    void rejectsUnknownFrameworks() {
        Assert.assertThrows(RuntimeException.class, () -> EvoSuiteGenerationTask.testFormatFor(TestFramework.UNKNOWN));
    }
}
