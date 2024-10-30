package teralizer.repository;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.GeneralizationVariant;

import java.util.List;

public class SQLiteRepository {

    public static Result<TestRecord> fetchIncludedTests(DSLContext create, Integer projectId) {
        return create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();
    }

    public static List<String> fetchIncludedTestClasses(DSLContext create, Integer projectId) {
        return create.selectDistinct(Tables.TEST.TEST_PACKAGE_NAME.concat('.').concat(Tables.TEST.TEST_CLASS_NAME))
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }

    public static Result<Record> fetchIncludedGeneralizations(DSLContext create, GeneralizationVariant variant, Integer projectId) {
        return create.select()
            .from(Tables.TEST)
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .fetch();
    }

    public static List<String> fetchIncludedGeneralizedClasses(DSLContext create, GeneralizationVariant variant, Integer projectId) {
        return create.selectDistinct(Tables.GENERALIZATION.GENERALIZED_PACKAGE_NAME.concat('.').concat(Tables.GENERALIZATION.GENERALIZED_CLASS_NAME))
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
