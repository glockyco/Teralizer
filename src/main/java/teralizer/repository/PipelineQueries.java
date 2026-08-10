package teralizer.repository;

import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.impl.DSL;
import teralizer.processing.ProcessingStage;
import teralizer.processing.filter.FilterDecision;

public class PipelineQueries {

    public static List<String> fetchCoveredClasses(DSLContext create, ProcessingStage stage, String variant, Long projectId) {
        return create.select(Tables.JACOCO_COVERAGE_REPORT.COVERED_PACKAGE.concat(".").concat(Tables.JACOCO_COVERAGE_REPORT.COVERED_CLASS))
            .from(Tables.JACOCO_COVERAGE_REPORT)
            .where(Tables.JACOCO_COVERAGE_REPORT.PROJECT_ID.eq(projectId))
            .and(Tables.JACOCO_COVERAGE_REPORT.STAGE.eq(stage))
            .and(Tables.JACOCO_COVERAGE_REPORT.VARIANT.isNotDistinctFrom(variant))
            .and(Tables.JACOCO_COVERAGE_REPORT.INSTRUCTION_COVERED.greaterThan(0))
            .fetchInto(String.class);
    }

    public static Result<Record> fetchIncludedTests(DSLContext create, Long projectId) {
        return create.select()
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();
    }

    public static List<String> fetchIncludedTestClasses(DSLContext create, Long projectId) {
        return create.selectDistinct(Tables.TEST.TEST_CLASS_QUALIFIED_NAME)
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }

    public static Result<Record> fetchIncludedAssertions(DSLContext create, Long projectId) {
        return create.select()
            .from(Tables.TEST)
            .join(Tables.ASSERTION)
            .on(Tables.TEST.ID.eq(Tables.ASSERTION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.ASSERTION.IS_INCLUDED.eq(true))
            .fetch();
    }

    /**
     * Summarizes why a project's assertions were rejected, most frequent first, as
     * {@code filter/reason xN}. An assertion is excluded by whichever filter first rejects it, at
     * whichever stage that filter runs, so a caller that finds nothing left to process must read
     * this to name the stage instead of naming its own.
     */
    public static List<String> fetchAssertionRejectionSummary(DSLContext create, Long projectId) {
        Field<Integer> occurrences = DSL.count();
        return create
            .select(Tables.FILTER_RESULT.FILTER_NAME, Tables.FILTER_RESULT.REASON_CODE, occurrences)
            .from(Tables.FILTER_RESULT)
            .where(Tables.FILTER_RESULT.PROJECT_ID.eq(projectId))
            .and(Tables.FILTER_RESULT.ASSERTION_ID.isNotNull())
            .and(Tables.FILTER_RESULT.DECISION.eq(FilterDecision.REJECT))
            .groupBy(Tables.FILTER_RESULT.FILTER_NAME, Tables.FILTER_RESULT.REASON_CODE)
            .orderBy(occurrences.desc())
            .fetch(r -> r.value1() + "/" + r.value2() + " x" + r.value3());
    }

    public static Result<Record> fetchIncludedGeneralizations(DSLContext create, String variant, Long projectId) {
        return create.select()
            .from(Tables.TEST)
            .join(Tables.ASSERTION).on(Tables.TEST.ID.eq(Tables.ASSERTION.TEST_ID))
            .join(Tables.GENERALIZATION).on(Tables.ASSERTION.ID.eq(Tables.GENERALIZATION.ASSERTION_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .fetch();
    }

    public static List<String> fetchIncludedGeneralizedClasses(DSLContext create, String variant, Long projectId) {
        return create.selectDistinct(Tables.GENERALIZATION.CLASS_QUALIFIED_NAME)
            .from(Tables.TEST)
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }
}
