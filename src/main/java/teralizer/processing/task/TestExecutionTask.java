package teralizer.processing.task;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class TestExecutionTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutionTask.class);

    private final ConsoleCommand consoleCommand;

    public TestExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public TestExecutionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        List<String> includedTests;
        switch (this.stage) {
            case TEST_EXECUTION_ORIGINAL:
                // Use `null` to include all tests.
                includedTests = null;
                break;
            case TEST_EXECUTION_FILTERED:
                includedTests = this.fetchTestClasses(create, this.projectRecord.getId());
                break;
            case TEST_EXECUTION_GENERALIZED:
                includedTests = this.fetchTestClasses(create, this.projectRecord.getId());
                includedTests.addAll(this.fetchGeneralizedClasses(create, this.projectRecord.getId(), this.variant));
                break;
            default:
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
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
            if (e.getMessage().contains("AssertionFailedError")) {
                LOGGER.atDebug().log(e.getMessage());
                reportInfo.accept(e.getMessage());
            } else {
                throw e;
            }
        }
    }

    private List<String> fetchTestClasses(DSLContext create, Integer projectId) {
        return create.selectDistinct(Tables.TEST.TEST_CLASS_PACKAGE.concat('.').concat(Tables.TEST.TEST_CLASS_NAME))
            .from(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }

    private List<String> fetchGeneralizedClasses(DSLContext create, Integer projectId, GeneralizationVariant variant) throws Exception {
        return create.selectDistinct(Tables.GENERALIZATION.GENERALIZED_CLASS_PACKAGE.concat('.').concat(Tables.GENERALIZATION.GENERALIZED_CLASS_NAME))
            .from(Tables.TEST)
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.TEST.PROJECT_ID.eq(projectId))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .and(Tables.GENERALIZATION.VARIANT.eq(variant))
            .and(Tables.GENERALIZATION.IS_INCLUDED.eq(true))
            .fetchInto(String.class);
    }

    private List<String> buildGradleCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList("./gradlew", "--build-file", ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE, "--info", "-Djacoco.skip=false", "test"));
        if (includedTests != null) {
            // Set test inclusions for normal test execution and coverage reporting via JaCoCo:
            for (String includedTest : includedTests) {
                command.add("--tests");
                command.add(includedTest);
            }
            // Set test inclusions for mutation testing via PIT:
            command.add("-PtargetTests=" + String.join(",", includedTests));
        }
        return command;
    }

    private List<String> buildMavenCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList("mvn", "--file", ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE, "-Djacoco.skip=false"));
        if (includedTests != null) {
            String includedTestsJoined = String.join(",", includedTests);
            // Set test inclusions for normal test execution and coverage reporting via JaCoCo:
            command.add("-Dtest=" + includedTestsJoined);
            // Set test inclusions for mutation testing via PIT:
            command.add("-DtargetTests=" + includedTestsJoined);
        }
        command.add("test");
        return command;
    }
}
