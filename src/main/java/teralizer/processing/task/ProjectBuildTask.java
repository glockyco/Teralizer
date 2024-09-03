package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ConsoleCommand;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class ProjectBuildTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public ProjectBuildTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
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
        if (this.projectRecord.getTestCompiledPath() == null || !Files.exists(this.projectRecord.getTestCompiledPath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Test compiled path '" + this.projectRecord.getTestCompiledPath() + "' does not exist.");
        }
    }

    private void buildAnt(Path projectRootPath) throws IOException, InterruptedException {
        List<String> command = Arrays.asList("ant", "-f", "build.xml", "compile");
        ConsoleCommand.execute(projectRootPath, command);
    }

    private void buildGradle(Path projectRootPath) throws IOException, InterruptedException {
        List<String> command = Arrays.asList("./gradlew", "compileJava", "compileTestJava");
        ConsoleCommand.execute(projectRootPath, command);
    }

    private void buildMaven(Path projectRootPath) throws IOException, InterruptedException {
        List<String> command = Arrays.asList("mvn", "compile", "test-compile");
        ConsoleCommand.execute(projectRootPath, command);
    }

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return null;
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        return "ProjectBuildTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectBuildTask)) return false;
        ProjectBuildTask that = (ProjectBuildTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
