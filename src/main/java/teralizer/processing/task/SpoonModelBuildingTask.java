package teralizer.processing.task;

import java.util.function.Consumer;
import org.jooq.generated.tables.records.ProjectRecord;
import spoon.Launcher;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.spoon.SpoonFactory;

public class SpoonModelBuildingTask extends AbstractTask {

    public SpoonModelBuildingTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public SpoonModelBuildingTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.variant = variant;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Launcher spoonLauncher = SpoonFactory.createLauncher(this.projectRecord.getMainSourcePath(), this.projectRecord.getTestSourcePath(), this.projectRecord.getClasspath());
        context.put(this.projectRecord.getId(), TaskContext.SPOON_LAUNCHER, spoonLauncher);
    }
}
