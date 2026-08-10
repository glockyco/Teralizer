package teralizer.processing.diagnostics;

import java.util.Objects;
import java.util.function.Predicate;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.Generalization;
import org.jooq.generated.tables.GeneralizationLifecycle;
import org.jooq.generated.tables.records.GeneralizationLifecycleRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TaskRecord;
import org.jooq.impl.DSL;
import teralizer.processing.ProcessingStage;

public final class GeneralizationLifecycleWriter {

    private GeneralizationLifecycleWriter() {
    }

    public static void recordGeneratedSourceCreated(DSLContext create, GeneralizationRecord generalizationRecord) {
        GeneralizationLifecycleRecord record = fetchByGeneralizationId(create, generalizationRecord.getId());
        if (record == null) {
            record = create.newRecord(Tables.GENERALIZATION_LIFECYCLE);
            record.setGeneralizationId(generalizationRecord.getId());
            record.setGeneratedProjectCompiled(false);
            record.setGeneratedTestsExecuted(false);
            record.setGeneratedReportCollected(false);
            record.setGeneratedFilterPassed(false);
            record.setGeneratedPitCollected(false);
        }
        record.setGeneratedSourceCreated(true);
        applyRollup(record, null);
        record.store();
    }

    public static void recordProjectStageSucceeded(
        DSLContext create,
        ProcessingStage stage,
        Long projectId,
        String variant
    ) {
        for (GeneralizationLifecycleRecord record : fetchByProjectVariant(create, projectId, variant)) {
            if (!successCanAffect(record, stage)) {
                continue;
            }
            setStageFlag(record, stage, true);
            applyRollup(record, null);
            record.store();
        }
    }

    public static void recordGeneralizationStageSucceeded(
        DSLContext create,
        ProcessingStage stage,
        Long generalizationId
    ) {
        GeneralizationLifecycleRecord record = fetchByGeneralizationId(create, generalizationId);
        if (record == null || !successCanAffect(record, stage)) {
            return;
        }
        setStageFlag(record, stage, true);
        applyRollup(record, null);
        record.store();
    }

    public static void recordFilterOutcome(
        DSLContext create,
        Long generalizationId,
        boolean accepted,
        String reasonCode
    ) {
        GeneralizationLifecycleRecord record = fetchByGeneralizationId(create, generalizationId);
        if (record == null || !successCanAffect(record, ProcessingStage.FILTER_GENERALIZATIONS)) {
            return;
        }
        record.setGeneratedFilterPassed(accepted);
        applyRollup(record, accepted ? null : reasonCode);
        record.store();
    }

    public static void recordStageFailed(DSLContext create, TaskRecord taskRecord, String reasonCode) {
        ProcessingStage stage = taskRecord.getStage();
        if (!isLifecycleStage(stage)) {
            return;
        }
        if (taskRecord.getGeneralizationId() != null) {
            GeneralizationLifecycleRecord record = fetchByGeneralizationId(create, taskRecord.getGeneralizationId());
            if (record != null && failureCanAffect(record, stage)) {
                setStageFlag(record, stage, false);
                applyRollup(record, reasonCode);
                record.store();
            }
            return;
        }
        // Project-scoped failures use this same fanout as the lifecycle flags. Clear each
        // matching generalization here because AbstractTask has no attached record at project
        // scope, and keeping this beside fetchByProjectVariant prevents the two scopes diverging.
        for (GeneralizationLifecycleRecord record : fetchByProjectVariant(create, taskRecord.getProjectId(), taskRecord.getVariant())) {
            if (!failureCanAffect(record, stage)) {
                continue;
            }
            setStageFlag(record, stage, false);
            applyRollup(record, reasonCode);
            record.store();

            GeneralizationRecord generalization = create.selectFrom(Tables.GENERALIZATION)
                .where(Tables.GENERALIZATION.ID.eq(record.getGeneralizationId()))
                .fetchOne();
            if (generalization != null) {
                generalization.setIsIncluded(false);
                generalization.setExclusionInfo(String.format("Excluded by %s: %s", stage, reasonCode));
                generalization.store();
            }
        }
    }

    static Rollup deriveRollup(
        boolean generatedSourceCreated,
        boolean generatedProjectCompiled,
        boolean generatedTestsExecuted,
        boolean generatedReportCollected,
        boolean generatedFilterPassed,
        boolean generatedPitCollected,
        String failureCode
    ) {
        if (!generatedSourceCreated) {
            return Rollup.failure(ProcessingStage.GENERALIZE_TESTS, failureCode);
        }
        if (!generatedProjectCompiled) {
            return Rollup.failure(ProcessingStage.BUILD_PROJECT_GENERALIZED, failureCode);
        }
        if (!generatedTestsExecuted) {
            return Rollup.failure(ProcessingStage.EXECUTE_TESTS_GENERALIZED, failureCode);
        }
        if (!generatedReportCollected) {
            return Rollup.failure(ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED, failureCode);
        }
        if (!generatedFilterPassed) {
            return Rollup.failure(ProcessingStage.FILTER_GENERALIZATIONS, failureCode);
        }
        if (!generatedPitCollected) {
            return Rollup.failure(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED, failureCode);
        }
        return Rollup.usable();
    }

    private static GeneralizationLifecycleRecord fetchByGeneralizationId(DSLContext create, Long generalizationId) {
        if (generalizationId == null) {
            return null;
        }
        return create.selectFrom(Tables.GENERALIZATION_LIFECYCLE)
            .where(Tables.GENERALIZATION_LIFECYCLE.GENERALIZATION_ID.eq(generalizationId))
            .fetchOne();
    }

    private static Result<GeneralizationLifecycleRecord> fetchByProjectVariant(
        DSLContext create,
        Long projectId,
        String variant
    ) {
        GeneralizationLifecycle lifecycle = Tables.GENERALIZATION_LIFECYCLE;
        Generalization generalization = Tables.GENERALIZATION;
        if (projectId == null || variant == null) {
            return create.newResult(lifecycle);
        }
        return create.selectFrom(lifecycle)
            .where(lifecycle.GENERALIZATION_ID.in(
                DSL.select(generalization.ID)
                    .from(generalization)
                    .where(generalization.PROJECT_ID.eq(projectId))
                    .and(generalization.VARIANT.eq(variant))
            ))
            .fetch();
    }

    private static void applyRollup(GeneralizationLifecycleRecord record, String failureCode) {
        Rollup rollup = deriveRollup(
            truth(record.getGeneratedSourceCreated()),
            truth(record.getGeneratedProjectCompiled()),
            truth(record.getGeneratedTestsExecuted()),
            truth(record.getGeneratedReportCollected()),
            truth(record.getGeneratedFilterPassed()),
            truth(record.getGeneratedPitCollected()),
            failureCode
        );
        String retainedCode = retainedFailureCode(record, rollup);
        record.setFinalUsable(rollup.isFinalUsable());
        record.setFinalFailureStage(rollup.getFinalFailureStage());
        record.setFinalFailureCode(retainedCode);
    }

    /**
     * The failure stage is derived from the stage flags, so every later event recomputes it. An
     * event that carries no code of its own -- a success at an unrelated stage, or a failure the
     * classifier could not name -- must not erase the code an earlier event recorded for the same
     * stage. Without this, attribution depends on event order: rejections whose reports were
     * collected again lost their reason code while the authoritative {@code filter_result} row kept
     * it.
     */
    private static String retainedFailureCode(GeneralizationLifecycleRecord record, Rollup rollup) {
        return retainedFailureCode(record.getFinalFailureStage(), record.getFinalFailureCode(), rollup);
    }

    static String retainedFailureCode(String storedStage, String storedCode, Rollup rollup) {
        String derived = rollup.getFinalFailureCode();
        if (derived != null) {
            return derived;
        }
        if (storedCode == null) {
            return null;
        }
        return Objects.equals(storedStage, rollup.getFinalFailureStage()) ? storedCode : null;
    }

    private static boolean successCanAffect(GeneralizationLifecycleRecord record, ProcessingStage stage) {
        return stagePredicate(stage).test(record);
    }

    private static boolean failureCanAffect(GeneralizationLifecycleRecord record, ProcessingStage stage) {
        return stagePredicate(stage).test(record);
    }

    private static Predicate<GeneralizationLifecycleRecord> stagePredicate(ProcessingStage stage) {
        switch (stage) {
            case BUILD_PROJECT_GENERALIZED:
                return record -> truth(record.getGeneratedSourceCreated());
            case EXECUTE_TESTS_GENERALIZED:
                return record -> truth(record.getGeneratedSourceCreated())
                    && truth(record.getGeneratedProjectCompiled());
            case COLLECT_JUNIT_REPORTS_GENERALIZED:
                return record -> truth(record.getGeneratedSourceCreated())
                    && truth(record.getGeneratedProjectCompiled())
                    && truth(record.getGeneratedTestsExecuted());
            case FILTER_GENERALIZATIONS:
                return record -> truth(record.getGeneratedSourceCreated())
                    && truth(record.getGeneratedProjectCompiled())
                    && truth(record.getGeneratedTestsExecuted())
                    && truth(record.getGeneratedReportCollected());
            case COLLECT_PIT_DATA_GENERALIZED:
                return record -> truth(record.getGeneratedSourceCreated())
                    && truth(record.getGeneratedProjectCompiled())
                    && truth(record.getGeneratedTestsExecuted())
                    && truth(record.getGeneratedReportCollected())
                    && truth(record.getGeneratedFilterPassed());
            default:
                return record -> false;
        }
    }

    private static void setStageFlag(GeneralizationLifecycleRecord record, ProcessingStage stage, boolean value) {
        switch (stage) {
            case BUILD_PROJECT_GENERALIZED:
                record.setGeneratedProjectCompiled(value);
                break;
            case EXECUTE_TESTS_GENERALIZED:
                record.setGeneratedTestsExecuted(value);
                break;
            case COLLECT_JUNIT_REPORTS_GENERALIZED:
                record.setGeneratedReportCollected(value);
                break;
            case FILTER_GENERALIZATIONS:
                record.setGeneratedFilterPassed(value);
                break;
            case COLLECT_PIT_DATA_GENERALIZED:
                record.setGeneratedPitCollected(value);
                break;
            default:
                break;
        }
    }

    private static boolean isLifecycleStage(ProcessingStage stage) {
        return stage == ProcessingStage.BUILD_PROJECT_GENERALIZED
            || stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED
            || stage == ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED
            || stage == ProcessingStage.FILTER_GENERALIZATIONS
            || stage == ProcessingStage.COLLECT_PIT_DATA_GENERALIZED;
    }

    private static boolean truth(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    static final class Rollup {
        private final boolean finalUsable;
        private final String finalFailureStage;
        private final String finalFailureCode;

        private Rollup(boolean finalUsable, String finalFailureStage, String finalFailureCode) {
            this.finalUsable = finalUsable;
            this.finalFailureStage = finalFailureStage;
            this.finalFailureCode = finalFailureCode;
        }

        static Rollup usable() {
            return new Rollup(true, null, null);
        }

        static Rollup failure(ProcessingStage stage, String failureCode) {
            return new Rollup(false, stage.name(), failureCode);
        }

        boolean isFinalUsable() {
            return this.finalUsable;
        }

        String getFinalFailureStage() {
            return this.finalFailureStage;
        }

        String getFinalFailureCode() {
            return this.finalFailureCode;
        }
    }
}
