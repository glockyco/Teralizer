package teralizer.processing.task;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProjectBuildTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectBuildTask.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public ProjectBuildTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        this.buildProject();
    }

    private void buildProject() throws IOException, InterruptedException {
        Path projectPath = Paths.get(this.projectRecord.getPath());
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot build project " + projectPath + ". No pom.xml / build.gradle found.");
            case ANT:
                throw new RuntimeException("Cannot build project " + projectPath + ". Ant projects are not supported yet.");
            case GRADLE:
                this.buildGradleProject();
                break;
            case MAVEN:
                this.buildMavenProject(projectPath);
                break;
        }
    }

    private void buildMavenProject(Path projectPath) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "compile", "test-compile");
        processBuilder.directory(projectPath.toFile());
        Process process = processBuilder.start();

        try (
            InputStreamReader outputStream = new InputStreamReader(process.getInputStream());
            BufferedReader outputReader = new BufferedReader(outputStream);
            InputStreamReader errorStream = new InputStreamReader(process.getErrorStream());
            BufferedReader errorReader = new BufferedReader(errorStream)
        ) {
            output.append(outputReader.lines().collect(Collectors.joining("\n")));
            error.append(errorReader.lines().collect(Collectors.joining("\n")));
        }

        int exitCode = process.waitFor();

        if (exitCode == 0 && error.toString().isEmpty()) {
            LOGGER.atDebug().log(output.toString());
        } else {
            String errorMessage = "Output:\n\n" + output + (error.toString().isEmpty() ? "" : "\n\nError:\n\n" + error);
            throw new RuntimeException(errorMessage);
        }
    }

    private void buildGradleProject() {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File(this.projectRecord.getPath()));

        try (ProjectConnection connection = connector.connect()) {
            BuildLauncher build = connection.newBuild();
            build.forTasks("compileJava", "compileTestJava");
            build.run();
        }
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
