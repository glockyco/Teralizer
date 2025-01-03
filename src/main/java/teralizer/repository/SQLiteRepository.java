package teralizer.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import teralizer.processing.GeneralizationVariant;

import java.util.List;

public class SQLiteRepository {

    public static List<String> fetchCoveredClasses(DSLContext create, GeneralizationVariant variant, Integer projectId) {
        return create.select(Tables.JACOCO_COVERAGE_REPORT.COVERED_PACKAGE.concat(".").concat(Tables.JACOCO_COVERAGE_REPORT.COVERED_CLASS))
            .from(Tables.JACOCO_COVERAGE_REPORT)
            .where(Tables.JACOCO_COVERAGE_REPORT.PROJECT_ID.eq(projectId))
            .and(Tables.JACOCO_COVERAGE_REPORT.VARIANT.isNotDistinctFrom(variant))
            .and(Tables.JACOCO_COVERAGE_REPORT.INSTRUCTION_COVERED.greaterThan(0))
            .fetchInto(String.class);
    }

    public static Result<Record> fetchIncludedTests(DSLContext create, Integer projectId) {
        return create.select()
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();
    }

    public static List<String> fetchIncludedTestClasses(DSLContext create, Integer projectId) {
        return create.selectDistinct(Tables.TEST.TEST_CLASS_QUALIFIED_NAME)
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }

    public static Result<Record> fetchIncludedAssertions(DSLContext create, Integer projectId) {
        return create.select()
            .from(Tables.TEST)
            .join(Tables.ASSERTION)
            .on(Tables.TEST.ID.eq(Tables.ASSERTION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .and(Tables.ASSERTION.IS_INCLUDED.eq(true))
            .fetch();
    }

    public static Result<Record> fetchIncludedGeneralizations(DSLContext create, GeneralizationVariant variant, Integer projectId) {
        return create.select()
            .from(Tables.TEST)
            .join(Tables.ASSERTION).on(Tables.TEST.ID.eq(Tables.ASSERTION.TEST_ID))
            .join(Tables.GENERALIZATION).on(Tables.ASSERTION.ID.eq(Tables.GENERALIZATION.ASSERTION_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .and(Tables.ASSERTION.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .fetch();
    }

    public static List<String> fetchIncludedGeneralizedClasses(DSLContext create, GeneralizationVariant variant, Integer projectId) {
        return create.selectDistinct(Tables.GENERALIZATION.CLASS_QUALIFIED_NAME)
            .from(Tables.TEST)
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }
}
