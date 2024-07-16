package teralizer.processing.task;

import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.jooq.generated.tables.records.ProjectRecord;

import java.io.File;

public class ProjectBuildTask extends AbstractTask {

    public TaskCallable<Void> create(ProjectRecord projectRecord) {
        this.setProjectId(projectRecord.getId());

        return new TaskCallable<>(this, () -> {
            this.buildProject(projectRecord);
            return null;
        });
    }

    private void buildProject(ProjectRecord projectRecord) {
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File(projectRecord.getPath()));

        try (ProjectConnection connection = connector.connect()) {
            // @TODO: Add support for Maven projects?
            // @TODO: Gracefully handle build failures.
            BuildLauncher build = connection.newBuild();
            build.forTasks("compileJava", "compileTestJava");
            build.run();
        }
    }
}
