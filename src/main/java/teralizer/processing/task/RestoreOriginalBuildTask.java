package teralizer.processing.task;

import java.util.function.Consumer;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

public class RestoreOriginalBuildTask extends AbstractTask {

    public RestoreOriginalBuildTask(ProjectRecord projectRecord) {
        this.stage = ProcessingStage.RESTORE_ORIGINAL_BUILD;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        GeneralizedSourceRestoreTask.deleteAllGeneralizedSources(this.projectRecord);
        new ProjectBuildTask(ProcessingStage.RESTORE_ORIGINAL_BUILD, this.projectRecord)
            .buildProject(context, reportInfo);
    }
}
