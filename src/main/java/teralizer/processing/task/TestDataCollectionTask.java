package teralizer.processing.task;

import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.TestSuiteXmlParser;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.generated.tables.records.TestReportRecord;
import org.xml.sax.SAXException;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestResult;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestDataCollectionTask extends AbstractTask {

    public TestDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public TestDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, projectRecord, testRecord, null);
    }

    public TestDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, GeneralizationRecord generalizationRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            this.executeTask(context);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<TestRecord> testRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.projectRecord.getId()))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();

        if (this.stage == ProcessingStage.TEST_DATA_COLLECTION_FILTERED) {
            for (TestRecord testRecord : testRecords) {
                scheduleTask.accept(new TestDataCollectionTask(this.stage, this.projectRecord, testRecord));
            }
        } else if (this.stage == ProcessingStage.TEST_DATA_COLLECTION_GENERALIZED) {
            for (TestRecord testRecord : testRecords) {
                // Not ideal with the n+1 querying, but we have much bigger fish to fry than that.
                Result<GeneralizationRecord> generalizationRecords = create.selectFrom(Tables.GENERALIZATION)
                    .where(Tables.GENERALIZATION.TEST_ID.eq(testRecord.getId()))
                    .fetch();

                for (GeneralizationRecord generalizationRecord : generalizationRecords) {
                    scheduleTask.accept(new TestDataCollectionTask(this.stage, this.projectRecord, testRecord, generalizationRecord));
                }
            }
        } else {
            throw new RuntimeException("Cannot collect test data. Unsupported processing stage " + this.stage + ".");
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        TestReportRecord testReportRecord;
        if (this.stage == ProcessingStage.TEST_DATA_COLLECTION_FILTERED) {
            testReportRecord = createTestReportRecord(create, this.projectRecord, this.testRecord);
        } else if (this.stage == ProcessingStage.TEST_DATA_COLLECTION_GENERALIZED) {
            testReportRecord = createTestReportRecord(create, this.projectRecord, this.testRecord, this.generalizationRecord);
        } else {
            throw new RuntimeException("Cannot collect test data. Unsupported processing stage " + this.stage + ".");
        }
        testReportRecord.store();
    }

    private static TestReportRecord createTestReportRecord(DSLContext create, ProjectRecord projectRecord, TestRecord testRecord) throws Exception {
        String testClassQualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();
        Path reportPath = projectRecord.getTestReportsPath().resolve("TEST-" + testClassQualifiedName + ".xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect test data. Report file '" + reportPath + "' does not exist.");
        }

        return createTestReportRecord(create, reportPath, readTestCaseReport(reportPath, testMethodQualifiedName), testRecord.getId(), null);
    }

    private static TestReportRecord createTestReportRecord(DSLContext create, ProjectRecord projectRecord, TestRecord testRecord, GeneralizationRecord generalizationRecord) throws Exception {
        String testClassQualifiedName = generalizationRecord.getGeneralizedClassPackage() + "." + generalizationRecord.getGeneralizedClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();
        Path reportPath = projectRecord.getTestReportsPath().resolve("TEST-" + testClassQualifiedName + ".xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect test data. Report file '" + reportPath + "' does not exist.");
        }

        return createTestReportRecord(create, reportPath, readTestCaseReport(reportPath, testMethodQualifiedName), null, generalizationRecord.getId());
    }

    private static TestReportRecord createTestReportRecord(DSLContext create, Path reportPath, ReportTestCase testCaseReport, Integer testId, Integer generalizationId) {
        TestReportRecord testReportRecord = create.newRecord(Tables.TEST_REPORT);
        testReportRecord.setTestId(testId);
        testReportRecord.setGeneralizationId(generalizationId);
        testReportRecord.setResult(getTestReportResult(reportPath, testCaseReport));
        testReportRecord.setRuntime(testCaseReport.getTime());
        testReportRecord.setFailureMessage(testCaseReport.getFailureMessage());
        testReportRecord.setFailureType(testCaseReport.getFailureType());
        testReportRecord.setFailureErrorLine(testCaseReport.getFailureErrorLine());
        testReportRecord.setFailureDetail(testCaseReport.getFailureDetail());
        testReportRecord.setReportPath(reportPath.toString());
        return testReportRecord;
    }

    private static ReportTestCase readTestCaseReport(Path reportPath, String testMethodQualifiedName) throws ParserConfigurationException, SAXException, IOException {
        // In the terminology used by the test reports:
        // - a "test _suite_ report" contains the results for all test methods of one test class,
        // - a "test _case_ report" contains the results for all runs of one test method.
        // Since each test record in our DB represents one test method, and we run each test
        // method exactly once, we should get exactly one test case report for each test record.

        TestSuiteXmlParser testSuiteReportParser = new TestSuiteXmlParser(new NullConsoleLogger());
        List<ReportTestSuite> testSuiteReports = testSuiteReportParser.parse(reportPath.toString());

        List<ReportTestCase> testCaseReports = testSuiteReports.stream()
            .flatMap(r -> r.getTestCases().stream())
            .filter(r -> r.getFullName().equals(testMethodQualifiedName) || (r.getFullName()).equals(testMethodQualifiedName + "()"))
            .collect(Collectors.toList());

        if (testCaseReports.isEmpty()) {
            throw new RuntimeException("Failed to collect test data. No reports matching '" + testMethodQualifiedName + "' were found in report '" + reportPath + "'.");
        } else if (testCaseReports.size() > 1) {
            // With our current process, we should only ever get a single test case report for each test.
            // If there are multiple reports, this indicates that either (i) the current processing logic
            // is incorrectly implemented somehow or (ii) our assumptions about the report data are wrong.
            throw new RuntimeException("Failed to collect test data. Multiple reports matching '" + testMethodQualifiedName + "' were found in report '" + reportPath + "'.");
        }

        return testCaseReports.get(0);
    }

    private static TestResult getTestReportResult(Path reportPath, ReportTestCase testCaseReport) {
        if (testCaseReport.isSuccessful()) {
            return TestResult.PASSED;
        } else if (testCaseReport.hasFailure()) {
            return TestResult.FAILED;
        } else if (testCaseReport.hasSkipped()) {
            return TestResult.SKIPPED;
        } else if (testCaseReport.hasError()) {
            return TestResult.ERROR;
        }
        throw new RuntimeException("Failed to collect test data. Unable to determine test result for test case " + testCaseReport.getFullName() + " in report '" + reportPath + "'.");
    }
}
