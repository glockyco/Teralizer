package teralizer.processing.filter;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.TestResult;

import java.util.List;

public class NonPassingTestFilter extends AbstractFilter {

    private final DSLContext create;
    private final TestRecord testRecord;
    private final GeneralizationRecord generalizationRecord;

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord) {
        this.create = create;
        this.testRecord = testRecord;
        this.generalizationRecord = null;
    }

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord, GeneralizationRecord generalizationRecord) {
        this.create = create;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    public FilterResult check() {
        // We have to exclude all tests that are in the same file as a failing
        // test. This is because (i) PIT requires all processed tests to pass,
        // and (ii) PIT offers only class-level inclusion/exclusion settings.
        if (this.generalizationRecord == null) {
            List<String> failingTests = this.create
                .select(Tables.TEST.TEST_PACKAGE_NAME
                    .concat(".")
                    .concat(Tables.TEST.TEST_CLASS_NAME)
                    .concat(".")
                    .concat(Tables.TEST.TEST_METHOD_NAME))
                .from(Tables.TEST)
                .join(Tables.TEST_REPORT).on(Tables.TEST.ID.eq(Tables.TEST_REPORT.TEST_ID))
                .where(Tables.TEST.PROJECT_ID.eq(this.testRecord.getProjectId()))
                .and(Tables.TEST.TEST_FILE_PATH.eq(this.testRecord.getTestFilePath()))
                .and(Tables.TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetchInto(String.class);

            if (!failingTests.isEmpty()) {
                String reason = "Failing tests in file " + this.testRecord.getTestFilePath() + ": " + String.join(", ", failingTests);
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
            }
        } else {
            List<String> failingTests = this.create
                .select(Tables.GENERALIZATION.GENERALIZED_PACKAGE_NAME
                    .concat(".")
                    .concat(Tables.GENERALIZATION.GENERALIZED_CLASS_NAME)
                    .concat(".")
                    .concat(Tables.GENERALIZATION.GENERALIZED_METHOD_NAME))
                .from(Tables.TEST)
                .join(Tables.GENERALIZATION).on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
                .join(Tables.TEST_REPORT).on(Tables.GENERALIZATION.ID.eq(Tables.TEST_REPORT.GENERALIZATION_ID))
                .where(Tables.TEST.PROJECT_ID.eq(this.testRecord.getProjectId()))
                .and(Tables.GENERALIZATION.VARIANT.eq(this.generalizationRecord.getVariant()))
                .and(Tables.GENERALIZATION.GENERALIZED_FILE_PATH.eq(this.generalizationRecord.getGeneralizedFilePath()))
                .and(Tables.TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetchInto(String.class);

            if (!failingTests.isEmpty()) {
                String reason = "Failing generalized tests in file " + this.generalizationRecord.getGeneralizedFilePath() + ": " + String.join(", ", failingTests);
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
