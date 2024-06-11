package teralizer.tasks;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class ProjectSetupTask {

    private final Task task = Task.PROJECT_SETUP;

    public ProjectRecord run(DSLContext create, Path projectPath) {
        File projectDirectoryFile = projectPath.toFile();

        if (!projectDirectoryFile.exists() || !projectDirectoryFile.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + projectDirectoryFile);
        }

        String projectClasspath = this.fetchClasspath(projectDirectoryFile);

        ProjectRecord projectRecord = create.newRecord(Tables.PROJECT);
        projectRecord.setPath(projectPath.toAbsolutePath().toString());
        projectRecord.setClasspath(projectClasspath);
        projectRecord.store();

        return projectRecord;
    }

    private String fetchClasspath(File projectDirectoryFile) {
        // @TODO: Add support for Maven projects?
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDirectoryFile);

        String classpath = "";
        // @TODO: Retrieve the build directories of a project programmatically.
        classpath += Paths.get(projectDirectoryFile.toString(), "build", "classes", "java", "main") + ":";
        classpath += Paths.get(projectDirectoryFile.toString(), "build", "resources", "main") + ":";
        classpath += Paths.get(projectDirectoryFile.toString(), "build", "classes", "java", "test") + ":";
        classpath += Paths.get(projectDirectoryFile.toString(), "build", "resources", "test") + ":";

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject project = modelBuilder.get();
            classpath += project.getClasspath().stream().map(d -> d.getFile().toString()).collect(Collectors.joining(":"));
        }

        return classpath;
    }
}
