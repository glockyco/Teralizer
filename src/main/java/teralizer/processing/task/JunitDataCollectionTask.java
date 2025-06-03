package teralizer.processing.task;

import org.apache.maven.plugin.surefire.log.api.NullConsoleLogger;
import org.apache.maven.plugins.surefire.report.ReportTestCase;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.plugins.surefire.report.TestSuiteXmlParser;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.*;
import org.xml.sax.SAXException;
import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestResult;
import teralizer.repository.SQLiteRepository;
import teralizer.util.Configuration;

import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JunitDataCollectionTask extends AbstractTask {

    public JunitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public JunitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, null, projectRecord, testRecord, null, null);
    }

    public JunitDataCollectionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null, null);
    }

    public JunitDataCollectionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, variant, projectRecord, testRecord, null, null);
    }

    public JunitDataCollectionTask(
        ProcessingStage stage,
        String variant,
        ProjectRecord projectRecord,
        TestRecord testRecord,
        AssertionRecord assertionRecord,
        GeneralizationRecord generalizationRecord
    ) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (!this.projectRecord.getTestReportsPath().toFile().exists()) {
            // The test reports directory might be missing if the project does
            // not contain any tests or uses a non-standard reports directory.
            throw new RuntimeException("Failed to collect test data. Report directory '" + this.projectRecord.getTestReportsPath() + "' does not exist.");
        }

        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);

        switch (this.stage) {
            case COLLECT_JUNIT_REPORTS_ORIGINAL:
                if (this.testRecord == null) {
                    List<TestRecord> testRecords = this.collectTests(create);
                    create.batchInsert(testRecords).execute();
                    this.scheduleTasks(create, scheduleTask);
                } else {
                    // We add the remaining test data using a test-level
                    // task (after initially creating them in a project-level
                    // task) to ensure that any errors that occur only cause
                    // test-level (rather than project-level) exclusions.
                    this.updateTestRecord(spoonLauncher.getFactory(), this.testRecord);
                    List<JunitTestReportRecord> testReportRecords = this.collectTestReportData(create);
                    create.batchInsert(testReportRecords).execute();
                }
                break;
            case COLLECT_JUNIT_REPORTS_INITIAL:
                if (this.testRecord == null) {
                    this.scheduleTasks(create, scheduleTask);
                } else {
                    List<JunitTestReportRecord> testReportRecords = this.collectTestReportData(create);
                    create.batchInsert(testReportRecords).execute();
                }
                break;
            case COLLECT_JUNIT_REPORTS_GENERALIZED:
                if (this.testRecord == null && this.generalizationRecord == null) {
                    this.scheduleTasks(create, scheduleTask);
                } else if (this.generalizationRecord == null) {
                    List<JunitTestReportRecord> testReportRecords = this.collectTestReportData(create);
                    create.batchInsert(testReportRecords).execute();
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
        switch (this.stage) {
            case COLLECT_JUNIT_REPORTS_ORIGINAL:
            case COLLECT_JUNIT_REPORTS_INITIAL: {
                Result<Record> records = SQLiteRepository.fetchIncludedTests(create, this.getProjectId());
                for (Record record : records) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.projectRecord, testRecord));
                }
                break;
            }
            case COLLECT_JUNIT_REPORTS_GENERALIZED: {
                Result<Record> testRecords = SQLiteRepository.fetchIncludedTests(create, this.getProjectId());
                for (Record record : testRecords) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.variant, this.projectRecord, testRecord));
                }
                Result<Record> generalizationRecords = SQLiteRepository.fetchIncludedGeneralizations(create, this.variant, this.getProjectId());
                for (Record record : generalizationRecords) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    AssertionRecord assertionRecord = record.into(AssertionRecord.class);
                    GeneralizationRecord generalizationRecord = record.into(GeneralizationRecord.class);
                    scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.variant, this.projectRecord, testRecord, assertionRecord, generalizationRecord));
                }
                break;
            }
            default:
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
    }

    private List<TestRecord> collectTests(DSLContext create) throws IOException {
        try (Stream<Path> paths = Files.walk(this.projectRecord.getTestReportsPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .flatMap(testReportPath -> this.parseTestCaseReports(testReportPath, null, null).stream())
                .filter(testCaseReport -> !testCaseReport.hasFailure() || !testCaseReport.getFailureMessage().startsWith("No tests found"))
                .map(testCaseReport -> this.buildTestRecord(create, testCaseReport))
                // Keep only the first record for each test method name to
                // avoid duplicates caused by repeated or parameterized tests.
                .collect(Collectors.collectingAndThen(
                    Collectors.toMap(
                        TestRecord::getTestMethodQualifiedName,
                        Function.identity(),
                        (existing, replacement) -> existing
                    ),
                    map -> new ArrayList<>(map.values())
                ));
        }
    }

    private List<JunitTestReportRecord> collectTestReportData(DSLContext create) {
        String testClassQualifiedName = this.testRecord.getTestClassQualifiedName();
        String testMethodQualifiedName = this.testRecord.getTestMethodQualifiedName();
        Path testReportPath = this.identifyTestReportPath(this.testRecord.getTestClassName(), testClassQualifiedName);
        return this.parseTestCaseReports(testReportPath, testClassQualifiedName, testMethodQualifiedName).stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, testReportPath, testCaseReport))
            .collect(Collectors.toList());
    }

    private List<JunitTestReportRecord> collectGeneralizationReportData(DSLContext create) {
        String testClassQualifiedName = this.generalizationRecord.getClassQualifiedName();
        String testMethodQualifiedName = this.generalizationRecord.getMethodQualifiedName();
        Path testReportPath = this.identifyTestReportPath(this.generalizationRecord.getClassName(), testClassQualifiedName);
        return this.parseTestCaseReports(testReportPath, testClassQualifiedName, testMethodQualifiedName).stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, testReportPath, testCaseReport))
            .collect(Collectors.toList());
    }

    private Path identifyTestReportPath(String testClassName, String testClassQualifiedName) {
        // If the file name is short enough, Surefire creates report files at the default location:
        Path defaultTestReportPath = this.projectRecord.getTestReportsPath().resolve("TEST-" + testClassQualifiedName + ".xml");
        if (Files.exists(defaultTestReportPath)) {
            return defaultTestReportPath;
        }

        // If the file name is too long, Surefire instead creates reports at the alternative location:
        Path alternativeTestReportPath = this.projectRecord.getTestReportsPath().resolve("TEST-" + testClassName.replace("_", " ") + ".xml");
        if (Files.exists(alternativeTestReportPath)) {
            return alternativeTestReportPath;
        }

        // @TODO: Handle cases where the alternative file name is still too long.
        throw new RuntimeException(
            "Unable to identify test report path for test class: " + testClassQualifiedName + ". " +
            "No file at default path " + defaultTestReportPath + " or alternative path " + alternativeTestReportPath + "."
        );
    }

    private List<ReportTestCase> parseTestCaseReports(Path testReportPath, String testClassQualifiedName, String testMethodQualifiedName) {
        try {
            TestSuiteXmlParser testSuiteReportParser = new TestSuiteXmlParser(new NullConsoleLogger());
            List<ReportTestSuite> testSuiteReports = testSuiteReportParser.parse(testReportPath.toString());
            List<ReportTestCase> testCaseReports = testSuiteReports.stream()
                .flatMap(testSuiteReport -> testSuiteReport.getTestCases().stream())
                .collect(Collectors.toList());

            if (testClassQualifiedName == null && testMethodQualifiedName == null && testCaseReports.isEmpty()) {
                return new ArrayList<>();
            }

            List<ReportTestCase> filteredTestCaseReports = testCaseReports.stream()
                .peek(testCaseReport -> {
                    testCaseReport.setName(replaceSpaces(testCaseReport.getName()));
                    testCaseReport.setFullName(replaceSpaces(testCaseReport.getFullName()));
                })
                .filter(testCaseReport -> {
                    if (testMethodQualifiedName != null) {
                        String reportMethodQualifiedName = testCaseReport.getFullName().replaceAll("\\(.*", "");
                        return reportMethodQualifiedName.equals(testMethodQualifiedName);
                    } else if (testClassQualifiedName != null) {
                        String reportClassQualifiedName = testCaseReport.getFullClassName();
                        return reportClassQualifiedName.equals(testClassQualifiedName);
                    }
                    return true;
                })
                .collect(Collectors.toList());

            if (filteredTestCaseReports.isEmpty()) {
                throw new RuntimeException("Failed to identify matching test case report for " + testMethodQualifiedName + " in " + testReportPath + ".");
            }

            return filteredTestCaseReports;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String replaceSpaces(String text) {
        if (text == null) {
            return null;
        }

        int firstParenIndex = text.indexOf('(');
        if (firstParenIndex > 0) {
            String beforeParen = text.substring(0, firstParenIndex).replace(" ", "_");
            String afterParen = text.substring(firstParenIndex);
            return beforeParen + afterParen;
        } else {
            return text.replace(" ", "_");
        }
    }

    private TestRecord buildTestRecord(DSLContext create, ReportTestCase testCaseReport) {
        String testMethodQualifiedName = testCaseReport.getFullName().replaceAll("\\(.*", "");
        String testMethodName = testCaseReport.getName().replaceAll("\\(.*", "");
        String testClassName = testCaseReport.getClassName();
        String testPackageName = testCaseReport.getFullClassName().replaceAll("\\.[^.]*$", "");

        TestRecord record = create.newRecord(Tables.TEST);
        record.setProjectId(this.getProjectId());

        Path testFilePath = this.projectRecord.getTestSourcePath().resolve(testCaseReport.getFullClassName().replace(".", "/") + ".java");

        if (!testFilePath.toFile().exists()) {
            throw new RuntimeException("Test file " + testFilePath + " does not exist.");
        }

        record.setTestFilePath(testFilePath.toString());
        record.setTestClassQualifiedName(testCaseReport.getFullClassName());
        record.setTestMethodQualifiedName(testMethodQualifiedName);
        record.setTestPackageName(testPackageName);
        record.setTestClassName(testClassName);
        record.setTestMethodName(testMethodName);
        record.setIsIncluded(true);

        return record;
    }

    private void updateTestRecord(Factory factory, TestRecord record) throws IOException {
        CtClass<?> testClass = factory.Class().get(this.testRecord.getTestClassQualifiedName());

        List<CtMethod<?>> matchingMethods = testClass.getMethodsByName(this.testRecord.getTestMethodName());

        if (matchingMethods.isEmpty()) {
            // This can happen if the test method was inherited from some other class.
            // The JUnit reports list the test as part of the child class then, but
            // the source code file of the child class does not contain the method.
            throw new RuntimeException("No method matches for test method (might be inherited): " + this.testRecord.getTestMethodQualifiedName());
        }

        List<CtMethod<?>> knownTestMethods = matchingMethods.stream()
            .filter(method -> method.getAnnotations().stream()
                .anyMatch(a -> Configuration.KNOWN_TEST_ANNOTATIONS.contains(a.getType().getSimpleName())))
            .collect(Collectors.toList());

        if (knownTestMethods.size() > 1) {
            throw new RuntimeException("Multiple matches for test method (" + knownTestMethods.size() + " total): " + this.testRecord.getTestMethodQualifiedName());
        }

        // At this point, we have 0-1 knownTestMethods and 1+ matchingMethods.
        // If we do have a known test method, take that one as our test method.
        // If we don't have any known test methods, just take the first of the
        // matching ones (which can either be an unknown type of test method or
        // a non-test method). It will later be excluded anyway, but we want to
        // keep processing it for now to collect more data about it.
        CtMethod<?> testMethod = knownTestMethods.stream().findFirst().orElse(matchingMethods.get(0));

        record.setTestMethodAbsolutePath(testMethod.getPath().toString());
        record.setTestMethodRelativePath(testMethod.getPath().relativePath(testClass).toString());

        CtAnnotation<?> testAnnotation = testMethod.getAnnotations().stream()
            .filter(a -> Configuration.KNOWN_TEST_ANNOTATIONS.contains(a.getType().getSimpleName()))
            .findFirst().orElse(null);

        if (testAnnotation != null) {
            String annotationName = testAnnotation.getType().getSimpleName();
            record.setTestAnnotationName(annotationName);
        }

        record.setTestAnnotationsSourceCode(testMethod.getAnnotations().stream()
            .map(Object::toString).collect(Collectors.joining("\n")));

        try (BufferedReader reader = new BufferedReader(new StringReader(testMethod.toString()))) {
            record.setLineCount((int) reader.lines().count());
        }

        record.store();
    }

    private JunitTestReportRecord buildTestReportRecord(DSLContext create, Path testReportPath, ReportTestCase testCaseReport) {
        // Preserve the full raw data in the data directory:
        Path dataDirectory = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/junit-data");
        Path testReportDataPath;
        try {
            String stageName = this.getStage().getStep() + "-" + this.getStage();
            String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
            String fileName = stageName + variantName + "." + testReportPath.getFileName().toString();
            testReportDataPath = dataDirectory.resolve(fileName);
            Files.createDirectories(dataDirectory);
            Files.copy(testReportPath, testReportDataPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Write (relevant parts of) the data to the DB:
        JunitTestReportRecord record = create.newRecord(Tables.JUNIT_TEST_REPORT);
        record.setProjectId(this.getProjectId());
        record.setStep(this.stage.getStep());
        record.setStage(this.stage);
        record.setVariant(this.variant);

        if (this.generalizationRecord == null) {
            record.setTestId(this.getTestId());
            record.setGeneralizationId(null);
            record.setTestPackageName(this.testRecord.getTestPackageName());
            record.setTestClassName(this.testRecord.getTestClassName());
            record.setTestMethodName(this.testRecord.getTestMethodName());
        } else {
            record.setTestId(null);
            record.setGeneralizationId(this.getGeneralizationId());
            record.setTestPackageName(this.generalizationRecord.getPackageName());
            record.setTestClassName(this.generalizationRecord.getClassName());
            record.setTestMethodName(this.generalizationRecord.getMethodName());
        }

        record.setTestCaseName(testCaseReport.getName());
        record.setResult(getTestReportResult(testCaseReport));
        record.setRuntime(testCaseReport.getTime());
        record.setFailureMessage(testCaseReport.getFailureMessage());
        record.setFailureType(testCaseReport.getFailureType());
        record.setFailureErrorLine(testCaseReport.getFailureErrorLine());
        record.setFailureDetail(testCaseReport.getFailureDetail());
        record.setReportFilePath(testReportDataPath.toString());
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
