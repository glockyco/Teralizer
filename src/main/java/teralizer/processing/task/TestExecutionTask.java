package teralizer.processing.task;

import org.jooq.DSLContext;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class TestExecutionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public TestExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public TestExecutionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        List<String> includedTests;
        switch (this.stage) {
            case EXECUTE_TESTS_ORIGINAL:
                // Use `null` to include all tests.
                includedTests = null;
                break;
            case EXECUTE_TESTS_INITIAL:
                includedTests = SQLiteRepository.fetchIncludedTestClasses(create, this.projectRecord.getId());
                break;
            case EXECUTE_TESTS_GENERALIZED:
                includedTests = SQLiteRepository.fetchIncludedTestClasses(create, this.projectRecord.getId());
                includedTests.addAll(SQLiteRepository.fetchIncludedGeneralizedClasses(create, this.variant, this.projectRecord.getId()));
                break;
            default:
                throw new RuntimeException("Cannot execute tests. Unsupported processing stage " + this.stage + ".");
        }

        if (includedTests != null && includedTests.isEmpty()) {
            throw new RuntimeException("Failed test execution. All tests of the project are excluded.");
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
                    /* JUnit 5 */ line.contains("AssertionFailedError") ||
                    /* JUnit 4 */ line.contains("AssertionError") ||
                    /* jqwik */ line.contains("TooManyFilterMissesException"))
                ) {
                    // There might be other errors beyond the assertion / filtering ones,
                    // but we just assume the best and keep going until something breaks.
                    reportInfo.accept(e.getMessage() + "\nFailure is (partially(?)) caused by assertion / filtering error(s).");
                } else {
                    throw e;
                }
            }
        }
    }

    private List<String> buildGradleCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList(
            "./gradlew",
            "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE,
            "--info",
            "-Djacoco.skip=false",
            "-DforkCount=1",
            "-DreuseForks=false",
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

    private List<String> buildMavenCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList(
            "mvn",
            "--file", Configuration.MAVEN_CUSTOM_BUILD_FILE,
            "-Djacoco.skip=false",
            "-DforkCount=1",
            "-DreuseForks=false",
            "test"
        ));

        if (includedTests != null) {
            // Set -Dtest parameter via MAVEN_OPTS to avoid "Argument list too long" errors.
            String mavenOpts = System.getenv("MAVEN_OPTS");
            mavenOpts = (mavenOpts == null ? "" : (mavenOpts + " ")) + "-Dtest=" + String.join(",", includedTests);
            this.consoleCommand.addEnvironmentVariable("MAVEN_OPTS", mavenOpts);
        }

        return command;
    }
}
