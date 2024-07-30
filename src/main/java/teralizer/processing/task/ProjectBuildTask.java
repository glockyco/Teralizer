package teralizer.processing.task;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.File;
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
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
        this.buildProject();
    }

    private void buildProject() {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File(this.projectRecord.getPath()));

        try (ProjectConnection connection = connector.connect()) {
            // @TODO: Add support for Maven projects?
            // @TODO: Gracefully handle build failures.
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
