package teralizer.processing.task;

import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
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
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestResult;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.processing.diagnostics.JqwikDiagnosticOutcome;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.InheritedTestMethodScreens;
import teralizer.util.Configuration;

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
                    this.importJqwikDiagnostics(create, gson);
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

    private List<TestRecord> collectTests(DSLContext create, Consumer<String> reportInfo) throws IOException {
        try (Stream<Path> paths = Files.walk(this.projectRecord.getTestReportsPath())) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".xml"))
                .flatMap(testReportPath -> this.parseTestCaseReports(testReportPath, null, null).stream())
                .filter(testCaseReport -> {
                    String className = testCaseReport.getFullClassName();
                    String methodName = testCaseReport.getName();

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

                    if (className.contains("$")) {
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
        String testMethodQualifiedName = testClassQualifiedName + "." + this.testRecord.getTestMethodName();
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

    private void importJqwikDiagnostics(DSLContext create, Gson gson) {
        JqwikExecutionRunRecord runRecord = create
            .selectFrom(Tables.JQWIK_EXECUTION_RUN)
            .where(Tables.JQWIK_EXECUTION_RUN.PROJECT_ID.eq(this.getProjectId()))
            .and(Tables.JQWIK_EXECUTION_RUN.VARIANT.eq(this.variant))
            .and(Tables.JQWIK_EXECUTION_RUN.EXECUTION_KIND.eq("JUNIT"))
            .orderBy(Tables.JQWIK_EXECUTION_RUN.ID.desc())
            .limit(1)
            .fetchOne();

        if (runRecord == null) {
            // No execution run was registered, so there is nothing to key diagnostics against.
            return;
        }

        Long junitTestReportId = create
            .select(Tables.JUNIT_TEST_REPORT.ID)
            .from(Tables.JUNIT_TEST_REPORT)
            .where(Tables.JUNIT_TEST_REPORT.GENERALIZATION_ID.eq(this.getGeneralizationId()))
            .and(Tables.JUNIT_TEST_REPORT.STAGE.eq(this.stage))
            .and(Tables.JUNIT_TEST_REPORT.VARIANT.eq(this.variant))
            .orderBy(Tables.JUNIT_TEST_REPORT.ID.desc())
            .limit(1)
            .fetchOne(Tables.JUNIT_TEST_REPORT.ID);

        Path outcomePath = this.resolveDiagnosticSidecarPath(runRecord.getExecutionId());

        JqwikPropertyExecutionRecord record = create.newRecord(Tables.JQWIK_PROPERTY_EXECUTION);
        record.setJqwikExecutionRunId(runRecord.getId());
        record.setProjectId(this.getProjectId());
        record.setGeneralizationId(this.getGeneralizationId());
        record.setJunitTestReportId(junitTestReportId);
        record.setDiagnosticSidecarPath(outcomePath.toString());

        if (Files.exists(outcomePath)) {
            try {
                String json = new String(Files.readAllBytes(outcomePath), StandardCharsets.UTF_8);
                JqwikDiagnosticOutcome outcome = JqwikDiagnosticOutcome.fromJson(gson, json);
                record.setTestCaseName(stripNul(outcome.testCaseName != null ? outcome.testCaseName : this.generalizationRecord.getMethodName()));
                record.setDiagnosticKind(stripNul(outcome.diagnosticKind));
                record.setRawStatus(stripNul(outcome.rawStatus));
                record.setFinalStatus(stripNul(outcome.finalStatus));
                record.setThrowableType(stripNul(outcome.throwableType));
                record.setThrowableMessage(stripNul(outcome.throwableMessage));
                record.setTries(outcome.tries);
                record.setChecks(outcome.checks);
                record.setDistinctTuples(outcome.distinctTuples);
                record.setDistinctNewTuples(outcome.distinctNewTuples);
                record.setSeed(stripNul(outcome.seed));
                record.setSelectedValueLogPath(outcomePath.resolveSibling(this.getGeneralizationId() + "." + this.getVariant() + ".values.tsv").toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            // Absence of a sidecar is recorded explicitly so FULL is never inferred from a missing row.
            record.setTestCaseName(this.generalizationRecord.getMethodName());
            record.setDiagnosticKind("DIAGNOSTIC_MISSING");
            record.setRawStatus("UNKNOWN");
            record.setFinalStatus("UNKNOWN");
        }

        record.store();
    }

    private static String stripNul(String value) {
        // Postgres TEXT columns reject NUL (0x00); a generated char/string value (e.g. CharUtils
        // isAscii over char 0) can carry it into a throwable message. Strip it before insert.
        return value == null ? null : value.replace("\u0000", "");
    }

    private Path resolveDiagnosticSidecarPath(String executionId) {
        Path relativePath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve("jqwik-data")
            .resolve("executions")
            .resolve(executionId)
            .resolve(this.getGeneralizationId() + "." + this.getVariant() + ".outcome.json");

        // The recorder runs with the project root as its working directory, so a data path that
        // is relative resolves against the root there; mirror that here when locating the file.
        Path rootedPath = this.projectRecord.getRootPath().resolve(relativePath);
        return Files.exists(rootedPath) ? rootedPath : relativePath;
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
                .peek(testCaseReport -> {
                    testCaseReport.setName(replaceSpaces(testCaseReport.getName()));
                    testCaseReport.setFullName(replaceSpaces(testCaseReport.getFullName()));
                })
                .filter(testCaseReport -> {
                    if (testMethodQualifiedName != null) {
                        String reportMethodQualifiedName = testCaseReport.getFullName().replaceAll("\\(.*", "");
                        return matchesQualifiedName(testMethodQualifiedName, reportMethodQualifiedName);
                    } else if (testClassQualifiedName != null) {
                        String reportClassQualifiedName = replaceSpaces(testCaseReport.getFullClassName());
                        return matchesQualifiedName(testClassQualifiedName, reportClassQualifiedName);
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
     * A surefire report identifies a testcase either by fully-qualified name (surefire < 3.0.2,
     * and all vintage-engine tests) or — for JUnit-platform tests since surefire 3.0.2 — by the
     * engine's display name, which jqwik beautifies by replacing underscores with spaces and
     * dropping the package. After space→underscore normalization the display-name shape is the
     * package-less suffix of the expected qualified name; the '.' boundary keeps simple-name
     * collisions from matching.
     */
    static boolean matchesQualifiedName(String expectedQualifiedName, String normalizedReportName) {
        return expectedQualifiedName.equals(normalizedReportName)
            || expectedQualifiedName.endsWith("." + normalizedReportName);
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
        ResolvedTestMethod resolved = this.findTestMethod(testClass);

        List<CtMethod<?>> knownTestMethods = resolved.matchingMethods.stream()
            .filter(method -> method.getAnnotations().stream()
                .anyMatch(a -> Configuration.KNOWN_TEST_ANNOTATIONS.contains(a.getType().getSimpleName())))
            .collect(Collectors.toList());

        if (knownTestMethods.size() > 1) {
            throw new RuntimeException("Multiple matches for test method (" + knownTestMethods.size() + " total): " + this.testRecord.getTestMethodQualifiedName());
        }

        CtMethod<?> testMethod = knownTestMethods.stream().findFirst().orElse(resolved.matchingMethods.get(0));
        CtClass<?> declaringClass = (CtClass<?>) testMethod.getParent(CtClass.class);

        record.setTestMethodQualifiedName(declaringClass.getQualifiedName() + "." + testMethod.getSimpleName());
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
