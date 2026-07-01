package teralizer.processing.task;

import java.util.function.Consumer;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

public interface Task {

    void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception;

    ProcessingStage getStage();
    String getVariant();

    Long getProjectId();
    Long getTestId();
    Long getAssertionId();
    Long getGeneralizationId();
}
