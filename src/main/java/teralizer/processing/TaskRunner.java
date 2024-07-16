package teralizer.processing;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskRecord;
import teralizer.processing.task.Task;
import teralizer.processing.task.TaskCallable;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TaskRunner {

    private final DSLContext create;

    public TaskRunner(DSLContext create) {
        this.create = create;
    }

    public <T> T runTask(ProcessingStage stage, TaskCallable<T> callable) {
        // There is a 1:n relationship between tasks and processing stages.
        // For example, the project build task needs to be executed during multiple processing stages.
        // As a result, stage information cannot be stored in the tasks themselves but needs to be set when running a task.

        Task task = callable.getTask();

        TaskRecord taskRecord = this.create.newRecord(Tables.TASK);
        taskRecord.setStep(stage.getStep());
        taskRecord.setStage(stage);
        taskRecord.setStatus(ProcessingStatus.IN_PROGRESS);

        taskRecord.setProjectId(task.getProjectId());
        taskRecord.setTestId(task.getTestId());
        taskRecord.setGeneralizationId(task.getGeneralizationId());

        taskRecord.store();

        T result;

        try {
            // We are only tracking task execution time here.
            // Total processing time of a project / test is tracked elsewhere,
            // so we don't have to include the runtime of the DB communication here.
            long startTime = System.currentTimeMillis();
            result = callable.call();
            long endTime = System.currentTimeMillis();

            // Depending on the task, the project / test / generalization ID might
            // only be available after the task's execution, so we have to set them
            // again AFTER executing the task.
            taskRecord.setProjectId(task.getProjectId());
            taskRecord.setTestId(task.getTestId());
            taskRecord.setGeneralizationId(task.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.SUCCEEDED);
            taskRecord.setRuntime((endTime - startTime) / 1000.0f);

            taskRecord.store();

            return result;
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);

            taskRecord.setProjectId(task.getProjectId());
            taskRecord.setTestId(task.getTestId());
            taskRecord.setGeneralizationId(task.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.FAILED);
            taskRecord.setError(stringWriter.toString());

            taskRecord.store();

            // Rethrow the exception after writing it to the DB to interrupt further execution.
            throw new RuntimeException(e);
        }
    }
}
