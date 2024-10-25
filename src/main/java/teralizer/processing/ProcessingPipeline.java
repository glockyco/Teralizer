package teralizer.processing;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.task.Task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

public class ProcessingPipeline {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingPipeline.class);

    private final DSLContext create;

    private final Set<Task> allTasks = new HashSet<>();
    private final Queue<Task> queuedTasks = new PriorityQueue<>(new TaskPriorityComparator());
    private final TaskContext context = new TaskContext();

    public ProcessingPipeline(DSLContext create) {
        this.create = create;
    }

    public void addTask(Task task) {
        if (this.allTasks.add(task)) {
            LOGGER.atDebug().log("Adding task {} to queue.", task);
            this.queuedTasks.offer(task);
        } else {
            LOGGER.atDebug().log("Skipping addTask operation for task {}. Task is already in queue.", task);
        }
    }

    public TaskContext getContext() {
        return this.context;
    }

    public void executeAll() {
        while (this.hasNext()) {
            this.executeNext();
        }
    }

    public boolean hasNext() {
        return !this.queuedTasks.isEmpty();
    }

    public void executeNext() {
        assert this.hasNext();
        Task currentTask = this.queuedTasks.poll();
        assert currentTask != null;
        ProcessingStage stage = currentTask.getStage();

        TaskRecord taskRecord = this.create.newRecord(Tables.TASK);
        taskRecord.setStep(stage.getStep());
        taskRecord.setStage(stage);
        taskRecord.setVariant(currentTask.getVariant());
        taskRecord.setStatus(ProcessingStatus.IN_PROGRESS);

        taskRecord.setProjectId(currentTask.getProjectId());
        taskRecord.setTestId(currentTask.getTestId());
        taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

        // The DB file might not have been created or might haven been removed by a `CleanupTask`.
        // The `ProcessingPipeline` should still continue as usual in such a case to:
        // (i)  execute tasks that do NOT require a database connection, and
        // (ii) log errors that occur for tasks that DO require a database connection.
        // The `ProcessingPipeline` should, however, NOT recreate a missing database
        // to ensure that the cleaned up state is preserved after a `CleanupTask`.
        if (TestGeneralizationRunner.DB_PATH.toFile().exists()) {
            taskRecord.store();
        }

        try {
            LOGGER.atDebug().log("Executing task {}.", currentTask);

            // We are only tracking task execution time here.
            // Total processing time of a project / test is tracked elsewhere,
            // so we don't have to include the runtime of the DB communication here.
            long startTime = System.currentTimeMillis();
            currentTask.execute(this.context, (String s) -> {
                String oldInfo = taskRecord.getInfo();
                String newInfo = oldInfo == null ? s : oldInfo + "\n\n" + s;
                taskRecord.setInfo(newInfo);
            }, this::addTask);
            long endTime = System.currentTimeMillis();

            // Depending on the task, the project / test / generalization ID might
            // only be available after the task's execution, so we have to set them
            // again AFTER executing the task.
            taskRecord.setProjectId(currentTask.getProjectId());
            taskRecord.setTestId(currentTask.getTestId());
            taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.SUCCEEDED);
            taskRecord.setRuntime((endTime - startTime) / 1000.0f);

            LOGGER.atDebug().log("Task {} successfully executed.", currentTask);
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);

            taskRecord.setProjectId(currentTask.getProjectId());
            taskRecord.setTestId(currentTask.getTestId());
            taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.FAILED);
            String oldInfo = taskRecord.getInfo();
            String newInfo = oldInfo == null ? stringWriter.toString() : String.join("\n\n", stringWriter.toString(), oldInfo);
            taskRecord.setInfo(newInfo);

            LOGGER.atError().log(e.getMessage(), e);

            this.queuedTasks.removeIf(queuedTask -> {
                // When a task fails, any scheduled tasks that depend on it should be removed from the queue.
                boolean hasMatchingProjectId = currentTask.getProjectId() == null || currentTask.getProjectId().equals(queuedTask.getProjectId());
                boolean hasMatchingTestId = currentTask.getTestId() == null || currentTask.getTestId().equals(queuedTask.getTestId());
                boolean hasMatchingGeneralizationId = currentTask.getGeneralizationId() == null || currentTask.getGeneralizationId().equals(queuedTask.getGeneralizationId());

                if (hasMatchingProjectId && hasMatchingTestId && hasMatchingGeneralizationId) {
                    LOGGER.atDebug().log("Task {} dropped from queue.", queuedTask);
                    return true;
                }

                return false;
            });
        }

        if (TestGeneralizationRunner.DB_PATH.toFile().exists()) {
            // When executing a test- or generalization-level `CleanupTask`, the corresponding `TaskRecord` is
            // deleted. Forcing an insert in such a case ensures that the task is persisted in the DB again at one
            // level higher (project- or test-level).
            if (!this.create.fetchExists(this.create.select().from(Tables.TASK).where(Tables.TASK.ID.eq(taskRecord.getId())))) {
                taskRecord.changed(true);
                taskRecord.insert();
            } else {
                taskRecord.update();
            }
        }
    }
}
