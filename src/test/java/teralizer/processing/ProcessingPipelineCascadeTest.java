package teralizer.processing;

import java.util.function.Consumer;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.processing.task.Task;

public class ProcessingPipelineCascadeTest {

    @Example
    void variant_scoped_failure_drops_only_same_variant() {
        Task failedVariantA = task(42L, "A");
        Task queuedVariantA = task(42L, "A");
        Task queuedVariantB = task(42L, "B");

        Assert.assertTrue(ProcessingPipeline.shouldDrop(failedVariantA, queuedVariantA));
        Assert.assertFalse(ProcessingPipeline.shouldDrop(failedVariantA, queuedVariantB));
    }

    @Example
    void shared_failure_drops_all_variants() {
        Task sharedFailure = task(42L, null);
        Task queuedVariantA = task(42L, "A");
        Task queuedVariantB = task(42L, "B");

        Assert.assertTrue(ProcessingPipeline.shouldDrop(sharedFailure, queuedVariantA));
        Assert.assertTrue(ProcessingPipeline.shouldDrop(sharedFailure, queuedVariantB));
    }

    private static Task task(Long projectId, String variant) {
        return new CascadeTask(projectId, variant);
    }

    private static final class CascadeTask implements Task {
        private final Long projectId;
        private final String variant;

        private CascadeTask(Long projectId, String variant) {
            this.projectId = projectId;
            this.variant = variant;
        }

        @Override
        public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
        }

        @Override
        public ProcessingStage getStage() {
            return ProcessingStage.EXECUTE_JPF;
        }

        @Override
        public String getVariant() {
            return this.variant;
        }

        @Override
        public Long getProjectId() {
            return this.projectId;
        }

        @Override
        public Long getTestId() {
            return null;
        }

        @Override
        public Long getAssertionId() {
            return null;
        }

        @Override
        public Long getGeneralizationId() {
            return null;
        }
    }
}
