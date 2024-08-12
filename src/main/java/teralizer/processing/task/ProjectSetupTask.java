package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import net.lingala.zip4j.ZipFile;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectType;
import teralizer.processing.TaskContext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
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
        if (this.projectRecord.getRootPath() == null) {
            throw new RuntimeException("Cannot setup project. Project root path is null.");
        } else if (!Files.exists(this.projectRecord.getRootPath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Project root path '" + this.projectRecord.getRootPath() + "' does not exist.");
        }

        this.projectRecord.setType(this.identifyProjectType(this.projectRecord.getRootPath()));

        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                this.setupJaiganticSourcePaths(this.projectRecord);
                this.setupJaiganticCompiledPaths(this.projectRecord);
                this.setupJaiganticClasspath(this.projectRecord);
                break;
            case ANT:
                this.setupAntSourcePaths(this.projectRecord, this.projectRecord.getRootPath());
                this.setupAntCompiledPaths(this.projectRecord);
                this.setupAntClasspath(this.projectRecord);
                break;
            case GRADLE:
                this.setupGradleSourcePaths(this.projectRecord, this.projectRecord.getRootPath());
                this.setupGradleCompiledPaths(this.projectRecord);
                this.setupGradleClasspath(this.projectRecord);
                break;
            case MAVEN:
                this.setupMavenSourcePaths(this.projectRecord, this.projectRecord.getRootPath());
                this.setupMavenCompiledPaths(this.projectRecord);
                this.setupMavenClasspath(this.projectRecord);
                break;
            default:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        this.projectRecord.store();

        if (this.projectRecord.getMainSourcePath() == null || !Files.exists(this.projectRecord.getMainSourcePath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Main source path '" + this.projectRecord.getMainSourcePath() + "' does not exist.");
        }
        if (this.projectRecord.getTestSourcePath() == null || !Files.exists(this.projectRecord.getTestSourcePath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Test source path '" + this.projectRecord.getTestSourcePath() + "' does not exist.");
        }

        JavaParser javaParser = this.createJavaParser(this.projectRecord.getMainSourcePath(), this.projectRecord.getTestSourcePath());
        context.put(this.projectRecord.getId(), TaskContext.JAVA_PARSER, javaParser);

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_ORIGINAL, this.projectRecord));
        scheduleTask.accept(new TestDetectionTask(ProcessingStage.TEST_DETECTION, this.projectRecord));
    }

    private ProjectType identifyProjectType(Path projectRootPath) {
        if (Files.exists(projectRootPath.resolve("build.command"))) {
            return ProjectType.JAIGANTIC;
        } else if (Files.exists(projectRootPath.resolve("build.xml"))) {
            return ProjectType.ANT;
        } else if (Files.exists(projectRootPath.resolve("build.gradle"))) {
            return ProjectType.GRADLE;
        } else if (Files.exists(projectRootPath.resolve("pom.xml"))) {
            return ProjectType.MAVEN;
        }
        return ProjectType.UNKNOWN;
    }

    private void setupJaiganticSourcePaths(ProjectRecord projectRecord) throws IOException {
        String directory = projectRecord.getRootPath().getFileName().toString();
        String[] parts = directory.split("##");
        assert parts.length == 2;
        Path unzippedProjectRootPath = projectRecord.getRootPath().resolve(parts[1] + "-master");

        if (!Files.exists(unzippedProjectRootPath)) {
            Path projectZipPath = projectRecord.getRootPath().resolve(directory + ".zip");
            assert Files.exists(projectZipPath);
            try (ZipFile zipFile = new ZipFile(projectZipPath.toFile())) {
                zipFile.extractAll(projectRecord.getRootPath().toString());
            }
        }

        assert Files.exists(unzippedProjectRootPath);

        ProjectType projectType = this.identifyProjectType(unzippedProjectRootPath);

        switch (projectType) {
            case UNKNOWN:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Nested JAigantic projects are not supported.");
            case ANT:
                this.setupAntSourcePaths(projectRecord, unzippedProjectRootPath);
                break;
            case GRADLE:
                this.setupGradleSourcePaths(projectRecord, unzippedProjectRootPath);
                break;
            case MAVEN:
                this.setupMavenSourcePaths(projectRecord, unzippedProjectRootPath);
                break;
            default:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }
    }

    private void setupJaiganticCompiledPaths(ProjectRecord projectRecord) {
        if (projectRecord.getMainCompiledPath() == null) {
            projectRecord.setMainCompiledPath(projectRecord.getRootPath().resolve("build"));
        }
        if (projectRecord.getTestCompiledPath() == null) {
            projectRecord.setTestCompiledPath(projectRecord.getRootPath().resolve("build"));
        }
    }

    private void setupJaiganticClasspath(ProjectRecord projectRecord) throws IOException {
        List<String> classpathElements = new ArrayList<>();

        classpathElements.add(projectRecord.getRootPath().resolve("build").toString());

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(projectRecord.getRootPath().resolve("depends"), "*.jar")) {
            directoryStream.forEach(path -> classpathElements.add(path.toString()));
        }

        projectRecord.setClasspath(String.join(File.pathSeparator, classpathElements));
    }

    private void setupAntSourcePaths(ProjectRecord projectRecord, Path projectRootPath) {
        throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
    }

    private void setupAntCompiledPaths(ProjectRecord projectRecord) {
        throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
    }

    private void setupAntClasspath(ProjectRecord projectRecord) {
        throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
    }

    private void setupGradleSourcePaths(ProjectRecord projectRecord, Path projectRootPath) {
        if (projectRecord.getMainSourcePath() == null) {
            projectRecord.setMainSourcePath(projectRootPath.resolve("src/main/java"));
        }
        if (projectRecord.getTestSourcePath() == null) {
            projectRecord.setTestSourcePath(projectRootPath.resolve("src/test/java"));
        }
    }

    private void setupGradleCompiledPaths(ProjectRecord projectRecord) {
        if (projectRecord.getMainCompiledPath() == null) {
            projectRecord.setMainCompiledPath(projectRecord.getRootPath().resolve("build/classes/java/main"));
        }
        if (projectRecord.getTestCompiledPath() == null) {
            projectRecord.setTestCompiledPath(projectRecord.getRootPath().resolve("build/classes/java/test"));
        }
    }

    private void setupGradleClasspath(ProjectRecord projectRecord) {
        File projectDirectoryFile = projectRecord.getRootPath().toFile();

        if (!projectDirectoryFile.exists() || !projectDirectoryFile.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + projectRecord.getRootPath());
        }

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDirectoryFile);

        List<String> classpathElements = new ArrayList<>();

        classpathElements.add(projectRecord.getMainCompiledPath().toString());
        classpathElements.add(projectRecord.getRootPath().resolve("build/classes/java/test").toString());

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject project = modelBuilder.get();
            project.getClasspath().forEach(d -> classpathElements.add(d.getFile().toString()));
        }

        projectRecord.setClasspath(String.join(File.pathSeparator, classpathElements));
    }

    private void setupMavenSourcePaths(ProjectRecord projectRecord, Path projectRootPath) {
        if (projectRecord.getMainSourcePath() == null) {
            projectRecord.setMainSourcePath(projectRootPath.resolve("src/main/java"));
        }
        if (projectRecord.getTestSourcePath() == null) {
            projectRecord.setTestSourcePath(projectRootPath.resolve("src/test/java"));
        }
    }

    private void setupMavenCompiledPaths(ProjectRecord projectRecord) {
        if (projectRecord.getMainCompiledPath() == null) {
            projectRecord.setMainCompiledPath(projectRecord.getRootPath().resolve("target/classes"));
        }
        if (projectRecord.getTestCompiledPath() == null) {
            projectRecord.setTestCompiledPath(projectRecord.getRootPath().resolve("target/test-classes"));
        }
    }

    private void setupMavenClasspath(ProjectRecord projectRecord) throws IOException, InterruptedException {
        String classpath = "";

        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        ProcessBuilder processBuilder = new ProcessBuilder("mvn", "dependency:build-classpath");
        processBuilder.directory(projectRecord.getRootPath().toFile());
        Process process = processBuilder.start();

        try (
            InputStreamReader outputStream = new InputStreamReader(process.getInputStream());
            BufferedReader outputReader = new BufferedReader(outputStream);
            InputStreamReader errorStream = new InputStreamReader(process.getErrorStream());
            BufferedReader errorReader = new BufferedReader(errorStream)
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

        int exitCode = process.waitFor();

        if (exitCode == 0 && error.toString().isEmpty()) {
            LOGGER.atDebug().log(output.toString());
        } else {
            String errorMessage = "Output:\n\n" + output + (error.toString().isEmpty() ? "" : "\n\nError:\n\n" + error);
            throw new RuntimeException(errorMessage);
        }

        List<String> classpathElements = new ArrayList<>();

        classpathElements.add(projectRecord.getMainCompiledPath().toString());
        classpathElements.add(projectRecord.getTestCompiledPath().toString());

        Path workingPath = Paths.get(System.getProperty("user.dir"));
        Arrays.stream(classpath.split(File.pathSeparator)).map(path -> workingPath.relativize(Paths.get(path)).toString()).forEach(classpathElements::add);

        projectRecord.setClasspath(String.join(File.pathSeparator, classpathElements));
    }

    private JavaParser createJavaParser(Path mainSourcePath, Path testSourcePath) {
        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver(
            new JavaParserTypeSolver(mainSourcePath),
            new JavaParserTypeSolver(testSourcePath),
            new ReflectionTypeSolver()
        );

        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setSymbolResolver(new JavaSymbolSolver(combinedTypeSolver));

        return new JavaParser(configuration);
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
