package teralizer.processing;

import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.diagnostics.TaskDiagnosticCodes;

public class PipelinePlanner {
    private final DSLContext create;
    private final ProcessingPipeline pipeline;

    public PipelinePlanner(DSLContext create) {
        this(create, new ProcessingPipeline(create));
    }

    public PipelinePlanner(DSLContext create, ProcessingPipeline pipeline) {
        this.create = create;
        this.pipeline = pipeline;
    }

    public void run(ProjectRecord project) {
        runPhases(project, Arrays.asList(PipelinePhase.values()));
    }

    void runPhases(ProjectRecord project, List<? extends Phase> phases) {
        for (Phase phase : phases) {
            if (!phase.isRequested(project)) {
                continue;
            }
            phase.clear(this.create, project);
            phase.checkPreconditions(this.create, project);
            phase.schedule(this.create, project, this.pipeline::addTask);
            this.pipeline.executeAll();
            this.throwOnStructuralFailures(project, phase);
        }
    }

    private void throwOnStructuralFailures(ProjectRecord project, Phase phase) {
        List<ProcessingStage> failedStages = this.create.selectDistinct(Tables.TASK.STAGE)
            .from(Tables.TASK)
            .where(Tables.TASK.PROJECT_ID.eq(project.getId()))
            .and(Tables.TASK.STAGE.in(phase.stages()))
            .and(Tables.TASK.STATUS.eq(ProcessingStatus.FAILED))
            .and(Tables.TASK.TEST_ID.isNull())
            .and(Tables.TASK.ASSERTION_ID.isNull())
            .and(Tables.TASK.GENERALIZATION_ID.isNull())
            // These reason codes are measured outcomes, not breakage, so they are attrition rather
            // than structural failures that halt the run. SUITE_TIMEOUT and EXECUTION_TIMEOUT are a
            // generalized or an original test suite that timed out. NO_INPUT_SPEC is a project whose
            // assertions are all non-generalizable, so there is
            // nothing to extract. Genuine project-level failures (build breakage, missing outputs)
            // carry no such diagnostic and still halt.
            .andNotExists(this.create.selectOne()
                .from(Tables.TASK_DIAGNOSTIC)
                .where(Tables.TASK_DIAGNOSTIC.TASK_ID.eq(Tables.TASK.ID))
                .and(Tables.TASK_DIAGNOSTIC.REASON_CODE.in(TaskDiagnosticCodes.SUITE_TIMEOUT, TaskDiagnosticCodes.EXECUTION_TIMEOUT, TaskDiagnosticCodes.NO_INPUT_SPEC)))
            .fetchInto(ProcessingStage.class);
        if (failedStages.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
            "structural phase failure after stage(s) " + stageNames(failedStages) + " for project " + project.getId()
        );
    }

    private static String stageNames(List<ProcessingStage> stages) {
        List<String> names = new java.util.ArrayList<>();
        for (ProcessingStage stage : stages) {
            names.add(stage.name());
        }
        return String.join(", ", names);
    }
}
