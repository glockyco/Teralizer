package teralizer.processing.diagnostics;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskDiagnosticRecord;
import org.jooq.generated.tables.records.TaskRecord;
import teralizer.processing.ProcessingStage;

public final class TaskDiagnosticWriter {

    private TaskDiagnosticWriter() {
    }

    public static String recordFailure(DSLContext create, TaskRecord taskRecord, Throwable failure) {
        ProcessingStage stage = taskRecord.getStage();
        if (!isDiagnosticStage(stage)) {
            return null;
        }
        TaskDiagnosticClassifier.Diagnostic diagnostic = TaskDiagnosticClassifier.classify(stage, failure);
        TaskDiagnosticRecord record = create.newRecord(Tables.TASK_DIAGNOSTIC);
        record.setTaskId(taskRecord.getId());
        record.setProjectId(taskRecord.getProjectId());
        record.setTestId(taskRecord.getTestId());
        record.setAssertionId(taskRecord.getAssertionId());
        record.setGeneralizationId(taskRecord.getGeneralizationId());
        record.setStage(stage.name());
        record.setReasonCode(diagnostic.reasonCode());
        if (diagnostic.detailJson() != null) {
            record.setDetailJson(JSONB.valueOf(diagnostic.detailJson()));
        }
        record.store();
        return diagnostic.reasonCode();
    }

    private static boolean isDiagnosticStage(ProcessingStage stage) {
        return stage == ProcessingStage.ADD_JPF_INSTRUMENTATION
            || stage == ProcessingStage.EXECUTE_JPF
            || stage == ProcessingStage.ANALYZE_JPF
            || stage == ProcessingStage.BUILD_PROJECT_INSTRUMENTED
            || stage == ProcessingStage.BUILD_PROJECT_GENERALIZED
            || stage == ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL
            || stage == ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED
            || stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED;
    }
}
