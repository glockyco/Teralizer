package teralizer.processing.task;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProjectSetupTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectSetupTask.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public ProjectSetupTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Path projectPath = Paths.get(this.projectRecord.getPath());
        if (Files.exists(projectPath.resolve("pom.xml"))) {
            this.projectRecord.setClasspath(this.fetchMavenClasspath(projectPath));
        } else if (Files.exists(projectPath.resolve("build.gradle"))) {
            this.projectRecord.setClasspath(this.fetchGradleClasspath(projectPath));
        } else {
            throw new RuntimeException("Cannot setup project " + projectPath + ". No pom.xml / build.gradle found.");
        }

        this.projectRecord.store();

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_ORIGINAL, this.projectRecord));
        scheduleTask.accept(new TestDetectionTask(ProcessingStage.TEST_DETECTION, this.projectRecord));
    }

    private String fetchMavenClasspath(Path projectPath) throws IOException, InterruptedException {
        String classpath = "";

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        // Reading console output from a separate process is not the cleanest solution,
        // but anything based on the MavenCli etc. classes just did not work at all,
        // seemingly due missing environment settings / information / dependencies.

        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "dependency:build-classpath");
        processBuilder.directory(projectPath.toFile());
        Process process = processBuilder.start();

        try (
            BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))
        ) {
            String line;
            String previousLine = "";
            while ((line = outputReader.readLine()) != null) {
                output.append(line).append("\n");
                if (previousLine.startsWith("[INFO] Dependencies classpath:")) {
                    classpath = line.trim();
                }
                previousLine = line;
            }

            error.append(errorReader.lines().collect(Collectors.joining("\n")));
        }

        process.waitFor();

        LOGGER.atDebug().log(output.toString());

        if (!error.toString().isEmpty()) {
            throw new RuntimeException(error.toString());
        }

        List<String> classpathElements = new ArrayList<>();

        // @TODO: Retrieve the build directories of a project programmatically.
        classpathElements.add(Paths.get(projectPath.toString(), "target", "classes").toString());
        classpathElements.add(Paths.get(projectPath.toString(), "target", "test-classes").toString());

        Path workingPath = Paths.get(System.getProperty("user.dir"));
        Arrays.stream(classpath.split(":")).map(path -> workingPath.relativize(Paths.get(path)).toString()).forEach(classpathElements::add);

        return String.join(":", classpathElements);
    }

    private String fetchGradleClasspath(Path projectPath) {
        File projectDirectoryFile = projectPath.toFile();

        if (!projectDirectoryFile.exists() || !projectDirectoryFile.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + projectPath);
        }

        // @TODO: Add support for Maven projects?
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDirectoryFile);

        List<String> classpathElements = new ArrayList<>();

        // @TODO: Retrieve the build directories of a project programmatically.
        classpathElements.add(Paths.get(projectPath.toString(), "build", "classes", "java", "main").toString());
        classpathElements.add(Paths.get(projectPath.toString(), "build", "resources", "main").toString());
        classpathElements.add(Paths.get(projectPath.toString(), "build", "classes", "java", "test").toString());
        classpathElements.add(Paths.get(projectPath.toString(), "build", "resources", "test").toString());

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject project = modelBuilder.get();
            project.getClasspath().forEach(d -> classpathElements.add(d.getFile().toString()));
        }

        return String.join(":", classpathElements);
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
