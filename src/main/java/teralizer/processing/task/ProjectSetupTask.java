package teralizer.processing.task;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProjectSetupTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public ProjectSetupTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
        this.fetchClasspath();

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_ORIGINAL, this.projectRecord));
        scheduleTask.accept(new TestDetectionTask(ProcessingStage.TEST_DETECTION, this.projectRecord));
    }

    private void fetchClasspath() {
        Path projectDirectory = Paths.get(this.projectRecord.getPath());
        File projectDirectoryFile = projectDirectory.toFile();

        if (!projectDirectoryFile.exists() || !projectDirectoryFile.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + projectDirectory);
        }

        // @TODO: Add support for Maven projects?
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDirectoryFile);

        String classpath = "";
        // @TODO: Retrieve the build directories of a project programmatically.
        classpath += Paths.get(projectDirectory.toString(), "build", "classes", "java", "main") + ":";
        classpath += Paths.get(projectDirectory.toString(), "build", "resources", "main") + ":";
        classpath += Paths.get(projectDirectory.toString(), "build", "classes", "java", "test") + ":";
        classpath += Paths.get(projectDirectory.toString(), "build", "resources", "test") + ":";

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject project = modelBuilder.get();
            classpath += project.getClasspath().stream().map(d -> d.getFile().toString()).collect(Collectors.joining(":"));
        }

        this.projectRecord.setClasspath(classpath);
        this.projectRecord.store();
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
        return "ProjectSetupTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectSetupTask)) return false;
        ProjectSetupTask that = (ProjectSetupTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
