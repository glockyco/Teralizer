package teralizer.processing.task;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.ProjectRecord;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class CleanupTaskTest {

    @Example
    void freshStartSchedulesProjectCleanup() {
        List<Task> tasks = scheduledBootstrapTasks(true);

        Assert.assertTrue(hasStage(tasks, ProcessingStage.CLEANUP_PROJECT));
    }

    @Example
    void attachSkipsProjectCleanup() {
        List<Task> tasks = scheduledBootstrapTasks(false);

        Assert.assertFalse(hasStage(tasks, ProcessingStage.CLEANUP_PROJECT));
        Assert.assertTrue(hasStage(tasks, ProcessingStage.ADD_DEPENDENCIES));
        Assert.assertTrue(hasStage(tasks, ProcessingStage.BUILD_PROJECT_ORIGINAL));
    }

    private static List<Task> scheduledBootstrapTasks(boolean freshStart) {
        List<Task> tasks = new ArrayList<>();
        ProjectRecord project = new ProjectRecord();
        project.setId(1L);
        project.setDataPath(Paths.get("data/test-project"));
        ProjectSetupTask task = new ProjectSetupTask(ProcessingStage.SETUP_PROJECT, project, freshStart);
        task.scheduleBootstrapTasks(tasks::add);
        return tasks;
    }

    private static boolean hasStage(List<Task> tasks, ProcessingStage stage) {
        for (Task task : tasks) {
            if (task.getStage() == stage) {
                return true;
            }
        }
        return false;
    }
}
