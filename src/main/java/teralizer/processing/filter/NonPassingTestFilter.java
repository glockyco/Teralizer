package teralizer.processing.filter;

import java.util.List;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.TestResult;

public class NonPassingTestFilter extends AbstractFilter {

    private final DSLContext create;
    private final TestRecord testRecord;
    private final GeneralizationRecord generalizationRecord;

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord) {
        this(create, testRecord, null);
    }

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord, GeneralizationRecord generalizationRecord) {
        this.create = create;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    public FilterResult check() {
        // PIT selects a test class, not one test method, and it stops when any test in that class
        // fails before mutation. Both branches below therefore look at the complete class.
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
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason, FilterReasonCodes.TEST_NOT_PASSING);
            }
        } else {
            List<String> failingTests = this.create
                .select(Tables.JUNIT_TEST_REPORT.TEST_CASE_NAME)
                .from(Tables.JUNIT_TEST_REPORT)
                .where(Tables.JUNIT_TEST_REPORT.PROJECT_ID.eq(this.generalizationRecord.getProjectId()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_PACKAGE_NAME.eq(this.generalizationRecord.getPackageName()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_CLASS_NAME.eq(this.generalizationRecord.getClassName()))
                .and(Tables.JUNIT_TEST_REPORT.VARIANT.eq(this.generalizationRecord.getVariant()))
                .and(Tables.JUNIT_TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetchInto(String.class);

            if (!failingTests.isEmpty()) {
                String reason = "Failing generalized tests in test class " + this.generalizationRecord.getClassQualifiedName() + ": " + String.join(", ", failingTests);
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason, FilterReasonCodes.TEST_NOT_PASSING);
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
