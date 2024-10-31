package teralizer.processing.task;

import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.TestSuiteXmlParser;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.JunitTestReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.xml.sax.SAXException;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestResult;
import teralizer.repository.SQLiteRepository;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JunitDataCollectionTask extends AbstractTask {

    public JunitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public JunitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, null, projectRecord, testRecord, null);
    }

    public JunitDataCollectionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null);
    }

    public JunitDataCollectionTask(
        ProcessingStage stage,
        GeneralizationVariant variant,
        ProjectRecord projectRecord,
        TestRecord testRecord,
        GeneralizationRecord generalizationRecord
    ) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        switch (this.stage) {
            case COLLECT_JUNIT_TESTS:
                List<TestRecord> testRecords = this.collectTests(create);
                create.batchInsert(testRecords).execute();
                break;
            case COLLECT_JUNIT_REPORTS_INITIAL:
                if (this.testRecord == null) {
                    this.scheduleTasks(create, scheduleTask);
                    return;
                } else {
                    List<JunitTestReportRecord> testReportRecords = this.collectTestReportData(create);
                    create.batchInsert(testReportRecords).execute();
                }
                break;
            case COLLECT_JUNIT_REPORTS_GENERALIZED:
                if (this.testRecord == null) {
                    this.scheduleTasks(create, scheduleTask);
                    return;
                } else {
                    List<JunitTestReportRecord> testReportRecords = this.collectGeneralizationReportData(create);
                    create.batchInsert(testReportRecords).execute();
                }
                break;
            default:
                throw new RuntimeException("Cannot collect test data. Unsupported processing stage " + this.stage + ".");
        }
    }

    private void scheduleTasks(DSLContext create, Consumer<Task> scheduleTask) {
        if (this.stage == ProcessingStage.COLLECT_JUNIT_REPORTS_INITIAL) {
            Result<TestRecord> testRecords = SQLiteRepository.fetchIncludedTests(create, this.getProjectId());
            for (TestRecord testRecord : testRecords) {
                scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.projectRecord, testRecord));
            }
        } else if (this.stage == ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED) {
            Result<Record> records = SQLiteRepository.fetchIncludedGeneralizations(create, this.variant, this.getProjectId());
            for (Record record : records) {
                TestRecord testRecord = record.into(TestRecord.class);
                GeneralizationRecord generalizationRecord = record.into(GeneralizationRecord.class);
                scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.variant, this.projectRecord, testRecord, generalizationRecord));
            }
        } else {
            throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
    }

    private List<TestRecord> collectTests(DSLContext create) throws IOException {
        try (Stream<Path> paths = Files.walk(this.projectRecord.getTestReportsPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .flatMap(testReportPath -> this.parseTestCaseReports(testReportPath, null, null).stream())
                .map(testCaseReport -> this.buildTestRecord(create, testCaseReport))
                .collect(Collectors.toList());
        }
    }

    private List<JunitTestReportRecord> collectTestReportData(DSLContext create) {
        String testClassQualifiedName = this.testRecord.getTestPackageName() + "." + this.testRecord.getTestClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + this.testRecord.getTestMethodName();
        Path testReportPath = this.projectRecord.getTestReportsPath().resolve("TEST-" + testClassQualifiedName + ".xml");
        return this.parseTestCaseReports(testReportPath, testClassQualifiedName, testMethodQualifiedName).stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, testReportPath, testCaseReport))
            .collect(Collectors.toList());
    }

    private List<JunitTestReportRecord> collectGeneralizationReportData(DSLContext create) {
        String testClassQualifiedName = this.generalizationRecord.getPackageName() + "." + this.generalizationRecord.getClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + this.testRecord.getTestMethodName();
        Path testReportPath = this.projectRecord.getTestReportsPath().resolve("TEST-" + testClassQualifiedName + ".xml");
        return this.parseTestCaseReports(testReportPath, testClassQualifiedName, testMethodQualifiedName).stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, testReportPath, testCaseReport))
            .collect(Collectors.toList());
    }

    private List<ReportTestCase> parseTestCaseReports(Path testReportPath, String testClassQualifiedName, String testMethodQualifiedName) {
        try {
            TestSuiteXmlParser testSuiteReportParser = new TestSuiteXmlParser(new NullConsoleLogger());
            List<ReportTestSuite> testSuiteReports = testSuiteReportParser.parse(testReportPath.toString());
            return testSuiteReports.stream()
                .flatMap(testSuiteReport -> testSuiteReport.getTestCases().stream())
                .peek(testCaseReport -> {
                    String fullNameOld = testCaseReport.getFullName();
                    String fullNameNew = fullNameOld.replaceFirst("\\(\\)$", "");
                    testCaseReport.setFullName(fullNameNew);
                })
                .filter(testCaseReport -> {
                    return testClassQualifiedName == null
                        || testMethodQualifiedName == null
                        || testCaseReport.getFullName().equals(testMethodQualifiedName);
                })
                .collect(Collectors.toList());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private TestRecord buildTestRecord(DSLContext create, ReportTestCase testCaseReport) {
        String testMethodQualifiedName = testCaseReport.getFullName();
        int lastDot = testMethodQualifiedName.lastIndexOf('.');
        int penultimateDot = testMethodQualifiedName.substring(0, lastDot).lastIndexOf('.');

        String testPackageName = testMethodQualifiedName.substring(0, penultimateDot);
        String testClassName = testMethodQualifiedName.substring(penultimateDot + 1, lastDot);
        String testMethodName = testMethodQualifiedName.substring(lastDot + 1);

        TestRecord record = create.newRecord(Tables.TEST);
        record.setProjectId(this.getProjectId());

        Path testFilePath = this.projectRecord.getTestSourcePath().resolve(testPackageName.replace(".", "/") + "/" + testClassName + ".java");

        if (!testFilePath.toFile().exists()) {
            throw new RuntimeException("Test file " + testFilePath + " does not exist.");
        }

        record.setTestFilePath(testFilePath.toString());
        record.setTestClassQualifiedName(testPackageName + "." + testClassName);
        record.setTestMethodQualifiedName(testPackageName + "." + testClassName + "." + testMethodName);
        record.setTestPackageName(testPackageName);
        record.setTestClassName(testClassName);
        record.setTestMethodName(testMethodName);

        String driverPackageName = testPackageName + "." + TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated";
        String driverClassName = "_" + testClassName + "_Driver_" + testMethodName;
        Path driverFilePath = testFilePath.getParent().resolve(TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated/" + driverClassName + ".java");

        record.setDriverFilePath(driverFilePath.toString());
        record.setDriverPackageName(driverPackageName);
        record.setDriverClassName(driverClassName);

        Path jpfConfigPath = this.projectRecord.getDataPath().resolve(testMethodQualifiedName + ".jpf");
        Path inputSpecificationPath = this.projectRecord.getDataPath().resolve(testMethodQualifiedName + ".jpf.input.json");
        Path outputSpecificationPath = this.projectRecord.getDataPath().resolve(testMethodQualifiedName + ".jpf.output.json");

        record.setJpfConfigPath(jpfConfigPath.toString());
        record.setInputSpecificationPath(inputSpecificationPath.toString());
        record.setOutputSpecificationPath(outputSpecificationPath.toString());

        record.setIsIncluded(true);

        return record;
    }

    private JunitTestReportRecord buildTestReportRecord(DSLContext create, Path testReportPath, ReportTestCase testCaseReport) {
        String testMethodQualifiedName = testCaseReport.getFullName();
        int lastDot = testMethodQualifiedName.lastIndexOf('.');
        int penultimateDot = testMethodQualifiedName.substring(0, lastDot).lastIndexOf('.');

        String packageName = testMethodQualifiedName.substring(0, penultimateDot);
        String className = testMethodQualifiedName.substring(penultimateDot + 1, lastDot);
        String methodName = testMethodQualifiedName.substring(lastDot + 1);

        JunitTestReportRecord record = create.newRecord(Tables.JUNIT_TEST_REPORT);
        record.setProjectId(this.getProjectId());
        record.setTestId(this.stage == ProcessingStage.COLLECT_JUNIT_REPORTS_INITIAL ? this.getTestId() : null);
        record.setGeneralizationId(this.stage == ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED ? this.getGeneralizationId() : null);
        record.setStep(this.stage.getStep());
        record.setStage(this.stage);
        record.setVariant(this.variant);
        record.setTestPackageName(packageName);
        record.setTestClassName(className);
        record.setTestMethodName(methodName);
        record.setResult(getTestReportResult(testCaseReport));
        record.setRuntime(testCaseReport.getTime());
        record.setFailureMessage(testCaseReport.getFailureMessage());
        record.setFailureType(testCaseReport.getFailureType());
        record.setFailureErrorLine(testCaseReport.getFailureErrorLine());
        record.setFailureDetail(testCaseReport.getFailureDetail());
        record.setReportFilePath(testReportPath.toString());
        return record;
    }

    private static TestResult getTestReportResult(ReportTestCase testCaseReport) {
        if (testCaseReport.isSuccessful()) {
            return TestResult.PASSED;
        } else if (testCaseReport.hasFailure()) {
            return TestResult.FAILED;
        } else if (testCaseReport.hasSkipped()) {
            return TestResult.SKIPPED;
        } else if (testCaseReport.hasError()) {
            return TestResult.ERROR;
        }
        throw new RuntimeException("Failed to collect test data. Unable to determine test result for test case " + testCaseReport.getFullName() + ".");
    }
}
