package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;
import teralizer.util.Configuration;

public class AbstractTaskBuildFileTest {

    @Example
    void originalReductionStagesUseNativeBuildFile() {
        assertBuildFile(Configuration.MAVEN_CUSTOM_BUILD_FILE,
            ProcessingStage.EXECUTE_TESTS_ORIGINAL,
            ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL,
            ProcessingStage.COLLECT_PIT_DATA_ORIGINAL);
    }

    @Example
    void initialReductionStagesUseGeneralizedBuildFileDespiteNullVariant() {
        assertBuildFile(Configuration.MAVEN_GENERALIZED_BUILD_FILE,
            ProcessingStage.EXECUTE_TESTS_INITIAL,
            ProcessingStage.COLLECT_JACOCO_DATA_INITIAL,
            ProcessingStage.COLLECT_PIT_DATA_INITIAL);
    }

    @Example
    void generalizedReductionStagesUseGeneralizedBuildFile() {
        assertBuildFile(Configuration.MAVEN_GENERALIZED_BUILD_FILE,
            ProcessingStage.EXECUTE_TESTS_GENERALIZED,
            ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED,
            ProcessingStage.COLLECT_PIT_DATA_GENERALIZED);
    }

    @Example
    void nonReductionStageHasNoBuildFileMapping() {
        Assert.assertThrows(IllegalArgumentException.class,
            () -> AbstractTask.mavenBuildFileFor(ProcessingStage.BUILD_PROJECT_ORIGINAL));
    }

    private static void assertBuildFile(String expectedBuildFile, ProcessingStage... stages) {
        for (ProcessingStage stage : stages) {
            Assert.assertEquals(stage.name(), expectedBuildFile, AbstractTask.mavenBuildFileFor(stage));
        }
    }
}
