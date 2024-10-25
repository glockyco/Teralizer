package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class TestExecutionTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestExecutionTask.class);

    private final ConsoleCommand consoleCommand;

    public TestExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        List<String> command;

        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot run tests for project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = Arrays.asList("./gradlew", "--build-file", ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE, "--info", "-Djacoco.skip=false", "test");
                break;
            case MAVEN:
                command = Arrays.asList("mvn", "--file", ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE, "-Djacoco.skip=false", "test");
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
}
