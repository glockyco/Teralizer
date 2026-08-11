package teralizer.processing.task;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
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
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestResult;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.processing.diagnostics.JqwikDiagnosticsImporter;
import teralizer.processing.reports.SurefireReportNames;
import teralizer.repository.PipelineQueries;
import teralizer.spoon.InheritedTestMethodScreens;
import teralizer.spoon.analysis.TestShape;
import teralizer.util.Configuration;

public class JunitDataCollectionTask extends AbstractTask {

    private static final org.slf4j.Logger LOGGER =
        org.slf4j.LoggerFactory.getLogger(JunitDataCollectionTask.class);

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
        Gson gson = context.get(TaskContext.GSON);
        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);

        switch (this.stage) {
            case COLLECT_JUNIT_REPORTS_ORIGINAL:
                if (this.testRecord == null) {
                    List<TestRecord> testRecords = this.collectTests(create, reportInfo);
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
                    JqwikDiagnosticsImporter.importOutcome(create, gson, this.projectRecord,
                        this.getProjectId(), this.getGeneralizationId(),
                        this.generalizationRecord.getMethodName(), this.stage, this.variant);
                    GeneralizationLifecycleWriter.recordGeneralizationStageSucceeded(create, this.stage, this.getGeneralizationId());
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
                Result<Record> records = PipelineQueries.fetchIncludedTests(create, this.getProjectId());
                for (Record record : records) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.projectRecord, testRecord));
                }
                break;
            }
            case COLLECT_JUNIT_REPORTS_GENERALIZED: {
                Result<Record> testRecords = PipelineQueries.fetchIncludedTests(create, this.getProjectId());
                for (Record record : testRecords) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    scheduleTask.accept(new JunitDataCollectionTask(this.stage, this.variant, this.projectRecord, testRecord));
                }
                Result<Record> generalizationRecords = PipelineQueries.fetchIncludedGeneralizations(create, this.variant, this.getProjectId());
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

    private List<TestRecord> collectTests(DSLContext create, Consumer<String> reportInfo) throws IOException {
        List<Path> reportPaths;
        try (Stream<Path> paths = Files.walk(this.projectRecord.getTestReportsPath())) {
            reportPaths = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .sorted()
                .collect(Collectors.toList());
        }

        List<ReportTestCase> testCaseReports = new ArrayList<>();
        int parsedReportCount = 0;
        for (Path reportPath : reportPaths) {
            try {
                testCaseReports.addAll(this.parseTestCaseReports(reportPath, null, null));
                parsedReportCount++;
            } catch (RuntimeException e) {
                LOGGER.atWarn()
                    .setCause(e)
                    .log("Skipping unparseable test report: {}", reportPath);
            }
        }
        if (!reportPaths.isEmpty() && parsedReportCount == 0) {
            throw new RuntimeException("Unable to parse any test report files: " + reportPaths + ".");
        }

        Predicate<String> isValidIdentifierName = name -> {
            if (name == null || name.isEmpty()) {
                return false;
            }
            if (!Character.isJavaIdentifierStart(name.charAt(0))) {
                return false;
            }
            for (int i = 1; i < name.length(); i++) {
                if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                    return false;
                }
            }
            return true;
        };

        Predicate<String> isValidClassName = name -> {
            if (name == null || name.isEmpty()) {
                return false;
            }
            String[] parts = name.split("\\.");
            for (String part : parts) {
                if (!isValidIdentifierName.test(part)) {
                    return false;
                }
            }
            return true;
        };

        Map<String, ReportTestCase> reportsByMethod = testCaseReports.stream()
            .filter(testCaseReport -> {
                String className = SurefireReportNames.normalize(testCaseReport.getFullClassName());
                String methodName = testCaseReport.getName();
                if (className != null && className.contains("$")) {
                    reportInfo.accept("Skipping report " + testCaseReport.getFullName() + " because it references an anonymous inner class.");
                    return false;
                }
                if (!isValidClassName.test(className) || !isValidIdentifierName.test(methodName)) {
                    reportInfo.accept("Skipping report " + testCaseReport.getFullName() + " because it does not reference a valid class / method name.");
                    return false;
                }
                if (testCaseReport.hasFailure()) {
                    String failureMessage = testCaseReport.getFailureMessage();
                    if (failureMessage != null && failureMessage.startsWith("No tests found")) {
                        reportInfo.accept("Skipping report " + testCaseReport.getFullName() + " because it does not reference any tests.");
                        return false;
                    }
                }
                return true;
            })
            // Lexical report ordering makes equal keys reproducible. A duplicate with a recorded
            // failure, error, or skip is retained over a record with no recorded outcome.
            .collect(Collectors.toMap(
                report -> SurefireReportNames.normalize(SurefireReportNames.withoutArguments(report.getFullName())),
                java.util.function.Function.identity(),
                JunitDataCollectionTask::preferReport,
                LinkedHashMap::new));

        return reportsByMethod.values().stream()
            .map(testCaseReport -> this.buildTestRecord(create, testCaseReport))
            .collect(Collectors.toList());
    }

    private static ReportTestCase preferReport(ReportTestCase existing, ReportTestCase replacement) {
        boolean existingHasOutcome = hasRecordedOutcome(existing);
        boolean replacementHasOutcome = hasRecordedOutcome(replacement);
        if (existingHasOutcome != replacementHasOutcome) {
            return existingHasOutcome ? existing : replacement;
        }
        if (existing.hasFailure() != replacement.hasFailure()) {
            return existing.hasFailure() ? existing : replacement;
        }
        return existing;
    }

    private static boolean hasRecordedOutcome(ReportTestCase report) {
        return report.hasFailure() || report.hasError() || report.hasSkipped();
    }

    private List<JunitTestReportRecord> collectTestReportData(DSLContext create) {
        String testClassQualifiedName = this.testRecord.getTestClassQualifiedName();
        String testMethodQualifiedName = testClassQualifiedName + "." + this.testRecord.getTestMethodName();
        TestReportSelection selection = this.identifyTestReportPath(
            this.testRecord.getTestClassName(), testClassQualifiedName, testMethodQualifiedName);
        return selection.testCaseReports.stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, selection.path, testCaseReport))
            .collect(Collectors.toList());
    }

    private List<JunitTestReportRecord> collectGeneralizationReportData(DSLContext create) {
        String testClassQualifiedName = this.generalizationRecord.getClassQualifiedName();
        String testMethodQualifiedName = this.generalizationRecord.getMethodQualifiedName();
        TestReportSelection selection = this.identifyTestReportPath(
            this.generalizationRecord.getClassName(), testClassQualifiedName, testMethodQualifiedName);
        // The method name selects the report file. It does not select the rows.
        // The generated class contains the property and the test methods from its superclass.
        // PIT runs all of these tests and stops if one test fails.
        // This task therefore records one row for each test case in the file.
        // If it records only the property, no filter can see a failed inherited test.
        List<ReportTestCase> classTestCaseReports = parseTestCaseReports(
            selection.path, testClassQualifiedName, null);
        return classTestCaseReports.stream()
            .map(testCaseReport -> this.buildTestReportRecord(create, selection.path, testCaseReport))
            .collect(Collectors.toList());
    }


    private TestReportSelection identifyTestReportPath(
        String testClassName,
        String testClassQualifiedName,
        String testMethodQualifiedName
    ) {
        return identifyTestReportPath(
            this.projectRecord.getTestReportsPath(), testClassName, testClassQualifiedName, testMethodQualifiedName);
    }

    static TestReportSelection identifyTestReportPath(
        Path reportsDirectory,
        String testClassName,
        String testClassQualifiedName,
        String testMethodQualifiedName
    ) {
        Path defaultTestReportPath = reportsDirectory.resolve("TEST-" + testClassQualifiedName + ".xml");
        Path alternativeTestReportPath = reportsDirectory.resolve("TEST-" + testClassName.replace("_", " ") + ".xml");

        List<Path> candidatePaths = new ArrayList<>();
        for (Path preferred : new Path[]{defaultTestReportPath, alternativeTestReportPath}) {
            if (Files.isRegularFile(preferred) && !candidatePaths.contains(preferred)) {
                candidatePaths.add(preferred);
            }
        }
        try (Stream<Path> paths = Files.walk(reportsDirectory, 1)) {
            paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .sorted()
                .forEach(path -> {
                    if (!candidatePaths.contains(path)) {
                        candidatePaths.add(path);
                    }
                });
        } catch (IOException e) {
            throw new RuntimeException("Unable to inspect test report directory " + reportsDirectory + ".", e);
        }

        List<Path> inspectedPaths = new ArrayList<>();
        for (Path candidatePath : candidatePaths) {
            inspectedPaths.add(candidatePath);
            try {
                List<ReportTestCase> reports = parseTestCaseReports(
                    candidatePath, testClassQualifiedName, testMethodQualifiedName);
                return new TestReportSelection(candidatePath, reports);
            } catch (RuntimeException e) {
                // A candidate can be valid XML for another class, or malformed. The complete
                // candidate list in the final error keeps both cases diagnosable.
                LOGGER.atDebug().setCause(e).log("Report candidate did not contain the expected testcase: {}", candidatePath);
            }
        }

        if (inspectedPaths.isEmpty()) {
            throw new RuntimeException(
                "Unable to identify test report path for test class: " + testClassQualifiedName + ". " +
                    "No report candidates exist. Candidates inspected: []."
            );
        }
        throw new RuntimeException(
            "Failed to identify matching test case report for " + testMethodQualifiedName + ". " +
                "Candidates inspected: " + inspectedPaths + "."
        );
    }

    static final class TestReportSelection {
        final Path path;
        final List<ReportTestCase> testCaseReports;

        private TestReportSelection(Path path, List<ReportTestCase> testCaseReports) {
            this.path = path;
            this.testCaseReports = testCaseReports;
        }
    }

    static List<ReportTestCase> parseTestCaseReports(Path testReportPath, String testClassQualifiedName, String testMethodQualifiedName) {
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
                .filter(testCaseReport -> {
                    if (testMethodQualifiedName != null) {
                        return SurefireReportNames.matches(testMethodQualifiedName, testCaseReport.getFullName());
                    } else if (testClassQualifiedName != null) {
                        return SurefireReportNames.matches(testClassQualifiedName, testCaseReport.getFullClassName());
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

    /**
     * A JUnit 3 test: no annotation, the conventional {@code test} name prefix, no parameters, and
     * a {@code junit.framework.TestCase} ancestor. The ancestor is checked through superclass
     * references rather than declarations, because TestCase itself is not part of the Spoon model.
     */
    static boolean isJunit3TestMethod(CtMethod<?> testMethod, CtClass<?> declaringClass) {
        if (!testMethod.getSimpleName().startsWith(Configuration.JUNIT3_METHOD_PREFIX)
            || !testMethod.getParameters().isEmpty()) {
            return false;
        }
        return extendsTestCase(declaringClass);
    }

    private static boolean extendsTestCase(CtClass<?> declaringClass) {
        CtTypeReference<?> superclass = declaringClass == null ? null : declaringClass.getSuperclass();
        while (superclass != null) {
            if (Configuration.JUNIT3_TEST_CASE_CLASS.equals(superclass.getQualifiedName())) {
                return true;
            }
            superclass = superclass.getSuperclass();
        }
        return false;
    }

    private TestRecord buildTestRecord(DSLContext create, ReportTestCase testCaseReport) {
        String testClassQualifiedName = SurefireReportNames.normalize(testCaseReport.getFullClassName());
        String testMethodName = SurefireReportNames.withoutArguments(testCaseReport.getName());
        String testMethodQualifiedName = testClassQualifiedName + "." + testMethodName;
        String testClassName = SurefireReportNames.normalize(testCaseReport.getClassName());
        String testPackageName = testClassQualifiedName.replaceAll("\\.[^.]*$", "");

        TestRecord record = create.newRecord(Tables.TEST);
        record.setProjectId(this.getProjectId());

        Path testFilePath = this.projectRecord.getTestSourcePath().resolve(testClassQualifiedName.replace(".", "/") + ".java");

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
        ResolvedTestMethod resolved = this.findTestMethod(testClass);

        List<CtMethod<?>> knownTestMethods = resolved.matchingMethods.stream()
            .filter(TestShape::hasTestAnnotation)
            .collect(Collectors.toList());

        if (knownTestMethods.size() > 1) {
            throw new RuntimeException("Multiple matches for test method (" + knownTestMethods.size() + " total): " + this.testRecord.getTestMethodQualifiedName());
        }

        CtMethod<?> testMethod = knownTestMethods.stream().findFirst().orElse(resolved.matchingMethods.get(0));
        CtClass<?> declaringClass = (CtClass<?>) testMethod.getParent(CtClass.class);

        // test_method_qualified_name keeps the CONCRETE run identity from the surefire report
        // (JUnit, PIT, and jqwik all report the executing subclass). The declaring class of an
        // inherited method is reachable through the stored absolute CtPath.
        record.setTestMethodAbsolutePath(testMethod.getPath().toString());
        record.setTestMethodRelativePath(testMethod.getPath().relativePath(declaringClass).toString());

        if (!declaringClass.getQualifiedName().equals(testClass.getQualifiedName())) {
            InheritedTestMethodScreens.Result screen = InheritedTestMethodScreens.evaluate(testClass, testMethod);
            if (!screen.isFlattenable()) {
                record.setIsIncluded(false);
                record.setExclusionInfo(screen.getExclusionInfo());
                record.store();
                return;
            }
        }

        String marker = TestShape.markerOf(testMethod, declaringClass);
        if (marker != null) {
            record.setTestAnnotationName(marker);
        }

        record.setTestAnnotationsSourceCode(testMethod.getAnnotations().stream()
            .map(Object::toString).collect(Collectors.joining("\n")));

        try (BufferedReader reader = new BufferedReader(new StringReader(testMethod.toString()))) {
            record.setLineCount((int) reader.lines().count());
        }

        record.store();
    }

    private ResolvedTestMethod findTestMethod(CtClass<?> testClass) {
        CtType<?> current = testClass;
        while (current != null) {
            if (current instanceof CtClass<?>) {
                List<CtMethod<?>> matchingMethods = ((CtClass<?>) current).getMethodsByName(this.testRecord.getTestMethodName());
                if (!matchingMethods.isEmpty()) {
                    return new ResolvedTestMethod(matchingMethods);
                }
            }
            current = current.getSuperclass() == null ? null : current.getSuperclass().getDeclaration();
        }

        throw new RuntimeException("No method matches for test method (might be inherited): " + this.testRecord.getTestMethodQualifiedName());
    }

    private static final class ResolvedTestMethod {
        private final List<CtMethod<?>> matchingMethods;

        private ResolvedTestMethod(List<CtMethod<?>> matchingMethods) {
            this.matchingMethods = matchingMethods;
        }
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
