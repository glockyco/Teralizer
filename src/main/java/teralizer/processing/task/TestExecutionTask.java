package teralizer.processing.task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.JqwikExecutionRunRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectType;
import teralizer.processing.TaskContext;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.repository.PipelineQueries;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

public class TestExecutionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public TestExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public TestExecutionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;

        this.consoleCommand = new ConsoleCommand(
            stage,
            variant,
            projectRecord.getId(),
            projectRecord.getDataPath(),
            Configuration.getJunitMaxExecutionTime(),
            TimeUnit.SECONDS
        );
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        List<String> includedTests;
        List<String> includedGeneralizedTests = null;
        switch (this.stage) {
            case EXECUTE_TESTS_ORIGINAL:
                // Use `null` to include all tests.
                includedTests = null;
                break;
            case EXECUTE_TESTS_INITIAL:
                includedTests = PipelineQueries.fetchIncludedTestClasses(create, this.projectRecord.getId());
                break;
            case EXECUTE_TESTS_GENERALIZED:
                includedTests = PipelineQueries.fetchIncludedTestClasses(create, this.projectRecord.getId());
                includedGeneralizedTests = PipelineQueries.fetchIncludedGeneralizedClasses(create, this.variant, this.projectRecord.getId());
                includedTests.addAll(includedGeneralizedTests);
                break;
            default:
                throw new RuntimeException("Cannot execute tests. Unsupported processing stage " + this.stage + ".");
        }

        if (includedTests != null && includedTests.isEmpty()) {
            throw new RuntimeException("Failed test execution. All tests of the project are excluded.");
        }

        if (this.stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED) {
            this.consoleCommand.setTimeout(
                generalizedExecutionTimeoutSeconds(includedGeneralizedTests.size()),
                TimeUnit.SECONDS
            );
        }

        if (this.stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED) {
            // One persisted diagnostics execution per generalized JUnit run. The generated
            // recorder writes execution-scoped sidecars keyed by this id; collection finds
            // the run and imports them. Forked Surefire/Gradle test JVMs inherit the process
            // environment, so the recorder reads these without per-build argLine tweaks.
            String executionId = UUID.randomUUID().toString();

            JqwikExecutionRunRecord runRecord = create.newRecord(Tables.JQWIK_EXECUTION_RUN);
            runRecord.setExecutionId(executionId);
            runRecord.setProjectId(this.projectRecord.getId());
            runRecord.setStep(this.stage.getStep());
            runRecord.setStage(this.stage.name());
            runRecord.setVariant(this.variant);
            runRecord.setExecutionKind("JUNIT");
            runRecord.store();

            this.consoleCommand.addEnvironmentVariable("TERALIZER_JQWIK_DIAGNOSTICS_MODE", "PERSISTED");
            this.consoleCommand.addEnvironmentVariable("TERALIZER_JQWIK_EXECUTION_ID", executionId);
        }

        List<String> command;
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = this.buildGradleCommand(includedTests);
                break;
            case MAVEN:
                command = this.buildMavenCommand(includedTests);
                break;
            default:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        try {
            this.consoleCommand.execute(this.projectRecord.getRootPath(), command);
        } catch (ConsoleCommandException e) {
            try (Stream<String> lines = Files.lines(e.getOutputPath())) {
                if (lines.anyMatch(line ->
                    // Try to detect assertion failures early so we don't have to process the whole file:
                    /* JUnit 5 */ line.contains("AssertionFailedError") ||
                    /* JUnit 4 */ line.contains("AssertionError") ||
                    /* JUnit 4 */ line.contains("ComparisonFailure") ||
                    /* jqwik */ line.contains("TooManyFilterMissesException") ||
                    // If we don't find anything with the above, also look at the test execution summary:
                    line.contains("[ERROR] Errors:") ||
                    line.contains("[ERROR] Failures:") ||
                    line.contains("There are test failures.")
                )) {
                    // There might be other errors beyond the assertion / filtering ones,
                    // but we just assume the best and keep going until something breaks.
                    // The processing pipeline handles processing errors rather gracefully
                    // (logs them and then terminates), so no need to be overly strict here.
                    reportInfo.accept(e.getMessage() + "\nFailure is (partially(?)) caused by test failures.");
                } else {
                    throw e;
                }
            }
        }

        Path producedExec = this.projectRecord.getType() == ProjectType.GRADLE
            ? this.projectRecord.getRootPath().resolve("build/jacoco/test.exec")
            : this.projectRecord.getRootPath().resolve("target/jacoco.exec");
        if (Files.exists(producedExec)) {
            Path preserved = JacocoDataCollectionTask.preservedExecPath(
                this.projectRecord,
                this.getProjectId(),
                JacocoDataCollectionTask.jacocoStageFor(this.stage),
                this.getVariant()
            );
            Files.createDirectories(preserved.getParent());
            Files.copy(producedExec, preserved, StandardCopyOption.REPLACE_EXISTING);
        }

        if (this.stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED) {
            GeneralizedSourceRestoreTask.archiveGeneralizedSources(this.projectRecord, this.getProjectId(), this.getVariant());
        }

        if (this.stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED) {
            boolean generalizedTestsIncluded = includedGeneralizedTests != null && !includedGeneralizedTests.isEmpty();
            requireGeneralizedReportsPresent(this.projectRecord.getTestReportsPath(), generalizedTestsIncluded);
            GeneralizationLifecycleWriter.recordProjectStageSucceeded(create, this.stage, this.getProjectId(), this.getVariant());
        }
    }

    static void requireGeneralizedReportsPresent(Path testReportsPath, boolean generalizedTestsIncluded) throws IOException {
        if (!generalizedTestsIncluded) {
            // Zero reports is correct when no generalized class was included. License refusals can
            // empty a project's generalized set, and that healthy run must not be recorded FAILED.
            return;
        }
        boolean hasGeneralizedReport = false;
        if (Files.exists(testReportsPath)) {
            try (Stream<Path> reportPaths = Files.list(testReportsPath)) {
                hasGeneralizedReport = reportPaths
                    .map(path -> path.getFileName().toString())
                    .anyMatch(fileName -> fileName.contains("Generalized"));
            }
        }

        if (!hasGeneralizedReport) {
            throw new RuntimeException(
                "Test execution reported success but produced no reports for any generalized test class. " +
                "The project's test runner likely cannot run JUnit-platform tests (surefire < 2.22); " +
                "refusing to record a false pass."
            );
        }
    }

    private long generalizedExecutionTimeoutSeconds(int propertyCount) {
        return scaledGeneralizedTimeoutSeconds(
            propertyCount,
            Configuration.getGeneralizationJqwikTries(this.variant),
            Configuration.getJunitMaxExecutionTime(),
            Configuration.getJunitBaselineTriesBudget(),
            Configuration.getJunitMaxGeneralizedExecutionTime()
        );
    }

    static long scaledGeneralizedTimeoutSeconds(int propertyCount, int tries, int flatTimeoutSeconds, int baselineTriesBudget, int maxTimeoutSeconds) {
        if (baselineTriesBudget <= 0) {
            throw new IllegalArgumentException("baselineTriesBudget must be positive.");
        }
        long propertyTries = (long) Math.max(0, propertyCount) * Math.max(0, tries);
        long multiplier = Math.max(1L, (propertyTries + baselineTriesBudget - 1L) / baselineTriesBudget);
        long scaled = (long) flatTimeoutSeconds * multiplier;
        // Clamp to the ceiling, but never below the flat budget.
        return Math.min(scaled, Math.max(flatTimeoutSeconds, maxTimeoutSeconds));
    }

    private List<String> buildGradleCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList(
            "./gradlew",
            "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE,
            "--info",
            "-Djacoco.skip=false",
            "test"
        ));

        if (includedTests != null) {
            // @TODO: Avoid "Argument list too long" errors if there are many included tests.
            for (String includedTest : includedTests) {
                command.add("--tests");
                command.add(includedTest);
            }
        }

        return command;
    }

    private List<String> buildMavenCommand(List<String> includedTests) throws IOException {
        // Generalized runs need the platform-capable runner; native runs keep the project's own.
        String mavenBuildFile = this.stage == ProcessingStage.EXECUTE_TESTS_GENERALIZED
            ? Configuration.MAVEN_GENERALIZED_BUILD_FILE
            : Configuration.MAVEN_CUSTOM_BUILD_FILE;
        List<String> command = new ArrayList<>(Arrays.asList(
            "mvn",
            "--file", mavenBuildFile,
            "-Djacoco.skip=false",
            "test"
        ));

        if (includedTests != null && !includedTests.isEmpty()) {
            // We are setting the included tests via includesFile (rather than -Dtest=...)
            // to avoid "Argument list too long" errors.

            Path commandDataPath = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/command-data");
            Files.createDirectories(commandDataPath);

            String stageName = this.stage.getStep() + "-" + this.stage;
            String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
            String executionName = "." + System.currentTimeMillis();
            String baseName = stageName + variantName + executionName;

            Path includesFilePath = commandDataPath.resolve(baseName + ".tests.txt");
            Files.write(includesFilePath, includedTests);

            command.add("-Dsurefire.includesFile=" + includesFilePath.toAbsolutePath());
        }

        return command;
    }
}
