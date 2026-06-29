package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class NonPassingTestFilterLimitedTest {

    private static final long PROJECT_ID = 101L;
    private static final long GENERALIZATION_ID = 202L;
    private static final String VARIANT = "IMPROVED";
    private static final String PACKAGE_NAME = "com.acme";
    private static final String CLASS_NAME = "GeneratedProperties";
    private static final String CLASS_QUALIFIED_NAME = PACKAGE_NAME + "." + CLASS_NAME;
    private static final String TEST_METHOD_NAME = "generatedProperty";

    @Example
    void tooManyFilterMissesWithNewTupleIsAcceptedAsLimited() throws Exception {
        Path dataPath = Files.createTempDirectory("limited-filter");
        writeValueLog(dataPath, Arrays.asList("ch=\\u0000", "ch=\\u0001"));

        FilterResult result = filter(dataPath, failingReport(TEST_METHOD_NAME, NonPassingTestFilter.TOO_MANY_FILTER_MISSES)).check();

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
        Assert.assertEquals(NonPassingTestFilter.LIMITED_TOO_MANY_FILTER_MISSES, result.getReason());
        Assert.assertEquals(Integer.valueOf(1), result.getDistinctNewTuples());
    }

    @Example
    void tooManyFilterMissesWithSeedOnlyIsRejected() throws Exception {
        Path dataPath = Files.createTempDirectory("limited-filter");
        writeValueLog(dataPath, Arrays.asList("ch=\\u0000"));

        FilterResult result = filter(dataPath, failingReport(TEST_METHOD_NAME, NonPassingTestFilter.TOO_MANY_FILTER_MISSES)).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(NonPassingTestFilter.FILTER_EXHAUSTED_SEED_ONLY, result.getReason());
        Assert.assertEquals(Integer.valueOf(0), result.getDistinctNewTuples());
    }

    @Example
    void tooManyFilterMissesWithMissingLogIsRejected() throws Exception {
        Path dataPath = Files.createTempDirectory("limited-filter");

        FilterResult result = filter(dataPath, failingReport(TEST_METHOD_NAME, NonPassingTestFilter.TOO_MANY_FILTER_MISSES)).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(NonPassingTestFilter.FILTER_EXHAUSTED_VALUE_LOG_MISSING, result.getReason());
        Assert.assertNull(result.getDistinctNewTuples());
    }

    @Example
    void assertionFailureRemainsRejected() throws Exception {
        Path dataPath = Files.createTempDirectory("limited-filter");
        writeValueLog(dataPath, Arrays.asList("ch=\\u0000", "ch=\\u0001"));

        FilterResult result = filter(dataPath, failingReport(TEST_METHOD_NAME, "java.lang.AssertionError")).check();

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals("Failing generalized tests in test class " + CLASS_QUALIFIED_NAME + ": " + TEST_METHOD_NAME, result.getReason());
        Assert.assertNull(result.getDistinctNewTuples());
    }

    private static NonPassingTestFilter filter(Path dataPath, FailingReport failingReport) {
        DSLContext create = dslReturning(failingReport);
        return new NonPassingTestFilter(create, testRecord(), generalizationRecord(), dataPath);
    }

    private static DSLContext dslReturning(FailingReport failingReport) {
        DSLContext rows = DSL.using(SQLDialect.POSTGRES);
        Result<Record2<String, String>> result = rows.newResult(
            Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME,
            Tables.JUNIT_TEST_REPORT.FAILURE_TYPE
        );
        Record2<String, String> record = rows.newRecord(
            Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME,
            Tables.JUNIT_TEST_REPORT.FAILURE_TYPE
        );
        record.set(Tables.JUNIT_TEST_REPORT.TEST_METHOD_NAME, failingReport.methodName);
        record.set(Tables.JUNIT_TEST_REPORT.FAILURE_TYPE, failingReport.failureType);
        result.add(record);

        MockDataProvider provider = context -> new MockResult[] { new MockResult(result.size(), result) };
        return DSL.using(new MockConnection(provider), SQLDialect.POSTGRES);
    }

    private static FailingReport failingReport(String methodName, String failureType) {
        return new FailingReport(methodName, failureType);
    }

    private static TestRecord testRecord() {
        TestRecord record = new TestRecord();
        record.setProjectId(PROJECT_ID);
        record.setTestPackageName(PACKAGE_NAME);
        record.setTestClassName(CLASS_NAME);
        record.setTestClassQualifiedName(CLASS_QUALIFIED_NAME);
        return record;
    }

    private static GeneralizationRecord generalizationRecord() {
        GeneralizationRecord record = new GeneralizationRecord();
        record.setId(GENERALIZATION_ID);
        record.setProjectId(PROJECT_ID);
        record.setVariant(VARIANT);
        record.setPackageName(PACKAGE_NAME);
        record.setClassName(CLASS_NAME);
        record.setClassQualifiedName(CLASS_QUALIFIED_NAME);
        return record;
    }

    private static void writeValueLog(Path dataPath, List<String> rows) throws Exception {
        Path path = valueLogPath(dataPath);
        Files.createDirectories(path.getParent());
        Files.write(path, rows, StandardCharsets.UTF_8);
    }

    private static Path valueLogPath(Path dataPath) {
        return dataPath.resolve("project-id-" + PROJECT_ID)
            .resolve("jqwik-data")
            .resolve(GENERALIZATION_ID + "." + VARIANT + ".junit.tsv");
    }

    private static final class FailingReport {
        private final String methodName;
        private final String failureType;

        private FailingReport(String methodName, String failureType) {
            this.methodName = methodName;
            this.failureType = failureType;
        }
    }
}
