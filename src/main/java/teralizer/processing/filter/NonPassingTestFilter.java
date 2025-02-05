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
                .select(Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME)
                .from(Tables.JUNIT_TEST_REPORT)
                .where(Tables.JUNIT_TEST_REPORT.PROJECT_ID.eq(this.testRecord.getProjectId()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_PACKAGE_NAME.eq(this.testRecord.getTestPackageName()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_CLASS_NAME.eq(this.testRecord.getTestClassName()))
                .and(Tables.JUNIT_TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetchInto(String.class);

            if (!failingTests.isEmpty()) {
                String reason = "Failing tests in test class " + this.testRecord.getTestClassQualifiedName() + ": " + String.join(", ", failingTests);
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
            }
        } else {
            List<String> failingTests = this.create
                .select(Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME)
                .from(Tables.JUNIT_TEST_REPORT)
                .where(Tables.JUNIT_TEST_REPORT.PROJECT_ID.eq(this.generalizationRecord.getProjectId()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_PACKAGE_NAME.eq(this.generalizationRecord.getPackageName()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_CLASS_NAME.eq(this.generalizationRecord.getClassName()))
                .and(Tables.JUNIT_TEST_REPORT.VARIANT.eq(this.generalizationRecord.getVariant()))
                .and(Tables.JUNIT_TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetchInto(String.class);

            if (!failingTests.isEmpty()) {
                String reason = "Failing generalized tests in test class " + this.generalizationRecord.getClassQualifiedName() + ": " + String.join(", ", failingTests);
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
