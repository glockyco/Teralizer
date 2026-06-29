package teralizer.processing.filter;

import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.TestResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class NonPassingTestFilter extends AbstractFilter {

    public static final String TOO_MANY_FILTER_MISSES = "net.jqwik.api.TooManyFilterMissesException";
    public static final String LIMITED_TOO_MANY_FILTER_MISSES = "LIMITED_TOO_MANY_FILTER_MISSES";
    public static final String FILTER_EXHAUSTED_SEED_ONLY = "FILTER_EXHAUSTED_SEED_ONLY";
    public static final String FILTER_EXHAUSTED_VALUE_LOG_MISSING = "FILTER_EXHAUSTED_VALUE_LOG_MISSING";

    private final DSLContext create;
    private final TestRecord testRecord;
    private final GeneralizationRecord generalizationRecord;
    private final Path dataPath;

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord) {
        this(create, testRecord, null, null);
    }

    public NonPassingTestFilter(DSLContext create, TestRecord testRecord, GeneralizationRecord generalizationRecord) {
        this(create, testRecord, generalizationRecord, null);
    }

    public NonPassingTestFilter(
        DSLContext create,
        TestRecord testRecord,
        GeneralizationRecord generalizationRecord,
        Path dataPath
    ) {
        this.create = create;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
        this.dataPath = dataPath;
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
            Result<Record2<String, String>> failingReports = this.create
                .select(Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME, Tables.JUNIT_TEST_REPORT.FAILURE_TYPE)
                .from(Tables.JUNIT_TEST_REPORT)
                .where(Tables.JUNIT_TEST_REPORT.PROJECT_ID.eq(this.generalizationRecord.getProjectId()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_PACKAGE_NAME.eq(this.generalizationRecord.getPackageName()))
                .and(Tables.JUNIT_TEST_REPORT.TEST_CLASS_NAME.eq(this.generalizationRecord.getClassName()))
                .and(Tables.JUNIT_TEST_REPORT.VARIANT.eq(this.generalizationRecord.getVariant()))
                .and(Tables.JUNIT_TEST_REPORT.RESULT.ne(TestResult.PASSED))
                .fetch();

            if (!failingReports.isEmpty()) {
                if (hasOnlyTooManyFilterMisses(failingReports)) {
                    return classifyTooManyFilterMisses();
                }

                String reason = "Failing generalized tests in test class " + this.generalizationRecord.getClassQualifiedName() + ": " + String.join(", ", failingTestMethodNames(failingReports));
                return new FilterResult(this.getName(), FilterDecision.REJECT, reason);
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }

    private static boolean hasOnlyTooManyFilterMisses(Result<Record2<String, String>> failingReports) {
        for (Record2<String, String> failingReport : failingReports) {
            if (!TOO_MANY_FILTER_MISSES.equals(failingReport.value2())) {
                return false;
            }
        }
        return true;
    }

    private static List<String> failingTestMethodNames(Result<Record2<String, String>> failingReports) {
        List<String> failingTests = new ArrayList<>();
        for (Record2<String, String> failingReport : failingReports) {
            failingTests.add(failingReport.value1());
        }
        return failingTests;
    }

    private FilterResult classifyTooManyFilterMisses() {
        Path valueLogPath = this.getJunitJqwikValueLogPath();
        if (valueLogPath == null) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, FILTER_EXHAUSTED_VALUE_LOG_MISSING, null);
        }

        JqwikValueLogEvidence evidence = JqwikValueLogEvidence.read(valueLogPath);
        if (!evidence.isReadable()) {
            return new FilterResult(this.getName(), FilterDecision.REJECT, FILTER_EXHAUSTED_VALUE_LOG_MISSING, null);
        }

        Integer distinctNewTuples = evidence.getDistinctNewTuples();
        if (distinctNewTuples != null && distinctNewTuples > 0) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT, LIMITED_TOO_MANY_FILTER_MISSES, distinctNewTuples);
        }
        return new FilterResult(this.getName(), FilterDecision.REJECT, FILTER_EXHAUSTED_SEED_ONLY, 0);
    }

    private Path getJunitJqwikValueLogPath() {
        if (this.dataPath == null) {
            return null;
        }

        return this.dataPath.resolve("project-id-" + this.generalizationRecord.getProjectId())
            .resolve("jqwik-data")
            .resolve(this.generalizationRecord.getId() + "." + this.generalizationRecord.getVariant() + ".junit.tsv");
    }
}
