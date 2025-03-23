package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ProjectBuildTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public ProjectBuildTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public ProjectBuildTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.variant = variant;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot build project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
            case ANT:
                this.buildAnt(this.projectRecord.getRootPath());
                break;
            case GRADLE:
                this.buildGradle(this.projectRecord.getRootPath());
                break;
            case MAVEN:
                this.buildMaven(this.projectRecord.getRootPath());
                break;
            default:
                throw new RuntimeException("Cannot build project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        if (this.projectRecord.getMainCompiledPath() == null || !Files.exists(this.projectRecord.getMainCompiledPath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Main compiled path '" + this.projectRecord.getMainCompiledPath() + "' does not exist.");
        }
        if ((this.projectRecord.getTestCompiledPath() == null || !Files.exists(this.projectRecord.getTestCompiledPath())) && !this.projectRecord.getUseTestGeneration()) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Test compiled path '" + this.projectRecord.getTestCompiledPath() + "' does not exist.");
        }
    }

    private void buildAnt(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("ant", "-f", "build.xml", "compile");
        this.consoleCommand.execute(projectRootPath, command);
    }

    private void buildGradle(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("./gradlew", "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE, "--info", "clean", "compileJava", "compileTestJava");
        this.consoleCommand.execute(projectRootPath, command);
    }

    private void buildMaven(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("mvn", "--file", Configuration.MAVEN_CUSTOM_BUILD_FILE, "clean", "compile", "test-compile");
        this.consoleCommand.execute(projectRootPath, command);
    }
}
