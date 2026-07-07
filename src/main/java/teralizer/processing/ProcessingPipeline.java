package teralizer.processing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.processing.diagnostics.TaskDiagnosticWriter;
import teralizer.processing.task.Task;

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
        taskRecord.setAssertionId(currentTask.getAssertionId());
        taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

        taskRecord.store();

        long startTime = -1;
        // Linkage/JVM errors still need a terminal DB status; rethrow after persisting so the
        // project run fails normally.
        Error unrecoverableError = null;
        try {
            LOGGER.atDebug().log("Executing task {}.", currentTask);

            // We are only tracking task execution time here.
            // Total processing time of a project / test is tracked elsewhere,
            // so we don't have to include the runtime of the DB communication here.
            startTime = System.currentTimeMillis();
            currentTask.execute(this.context, (String s) -> {
                String oldInfo = taskRecord.getInfo();
                String newInfo = oldInfo == null ? s : oldInfo + "\n" + s;
                taskRecord.setInfo(newInfo);
            }, this::addTask);
            long endTime = System.currentTimeMillis();

            // Depending on the task, the project / test / assertion / generalization
            // ID might only be available after the task's execution, so we have to
            // set them again AFTER executing the task.
            taskRecord.setProjectId(currentTask.getProjectId());
            taskRecord.setTestId(currentTask.getTestId());
            taskRecord.setAssertionId(currentTask.getAssertionId());
            taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.SUCCEEDED);
            taskRecord.setRuntime((endTime - startTime) / 1000.0f);

            LOGGER.atDebug().log("Task {} successfully executed.", currentTask);
        } catch (Throwable e) {
            long endTime = System.currentTimeMillis();

            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);

            taskRecord.setProjectId(currentTask.getProjectId());
            taskRecord.setTestId(currentTask.getTestId());
            taskRecord.setAssertionId(currentTask.getAssertionId());
            taskRecord.setGeneralizationId(currentTask.getGeneralizationId());

            taskRecord.setStatus(ProcessingStatus.FAILED);
            taskRecord.setRuntime(startTime == -1 ? null : ((endTime - startTime) / 1000.0f));
            String oldInfo = taskRecord.getInfo();
            String newInfo = oldInfo == null ? stringWriter.toString() : String.join("\n\n", stringWriter.toString(), oldInfo);
            taskRecord.setInfo(newInfo);

            String diagnosticReasonCode = TaskDiagnosticWriter.recordFailure(this.create, taskRecord, e);
            GeneralizationLifecycleWriter.recordStageFailed(this.create, taskRecord, diagnosticReasonCode);
            LOGGER.atError().log(e.getMessage(), e);

            this.queuedTasks.removeIf(queuedTask -> {
                if (ProcessingPipeline.shouldDrop(currentTask, queuedTask)) {
                    LOGGER.atDebug().log("Task {} dropped from queue.", queuedTask);
                    return true;
                }
                return false;
            });

            if (e instanceof Error) {
                unrecoverableError = (Error) e;
            }
        }

        // When executing a test-, assertion- or generalization-level `CleanupTask`,
        // the corresponding `TaskRecord` is deleted. Forcing an insert in such a case ensures that
        // the task is persisted in the DB again at one level higher (project-, test-, or assertion-level).
        if (!this.create.fetchExists(this.create.select().from(Tables.TASK).where(Tables.TASK.ID.eq(taskRecord.getId())))) {
            taskRecord.changed(true);
            taskRecord.insert();
        } else {
            taskRecord.update();
        }
        if (unrecoverableError != null) {
            throw unrecoverableError;
        }
    }

    static boolean shouldDrop(Task failed, Task queued) {
        boolean sameProject = failed.getProjectId() == null || failed.getProjectId().equals(queued.getProjectId());
        boolean sameTest = failed.getTestId() == null || failed.getTestId().equals(queued.getTestId());
        boolean sameAssertion = failed.getAssertionId() == null || failed.getAssertionId().equals(queued.getAssertionId());
        boolean sameGeneralization = failed.getGeneralizationId() == null || failed.getGeneralizationId().equals(queued.getGeneralizationId());
        // A shared (variant-null) failure cascades to every variant, since per-variant work
        // depends on shared stages. A variant-scoped failure drops only that same variant.
        boolean sameVariant = failed.getVariant() == null || failed.getVariant().equals(queued.getVariant());
        return sameProject && sameTest && sameAssertion && sameGeneralization && sameVariant;
    }
}
