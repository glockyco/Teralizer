package teralizer.processing.task;

import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.util.function.Consumer;

public interface Task {

    void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception;

    ProcessingStage getStage();
    GeneralizationVariant getVariant();

    Integer getProjectId();
    Integer getTestId();
    Integer getAssertionId();
    Integer getGeneralizationId();
}
