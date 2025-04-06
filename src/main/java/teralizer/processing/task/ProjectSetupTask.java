package teralizer.processing.task;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
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
import teralizer.processing.TestFramework;
import teralizer.util.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProjectSetupTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectSetupTask.class);

    public ProjectSetupTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.projectRecord.getRootPath() == null) {
            throw new RuntimeException("Cannot setup project. Project root path is null.");
        } else if (!Files.exists(this.projectRecord.getRootPath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Project root path '" + this.projectRecord.getRootPath() + "' does not exist.");
        }

        this.projectRecord.setType(this.identifyProjectType(this.projectRecord.getRootPath()));
        this.projectRecord.setGitVersion(identifyGitVersion(this.projectRecord.getRootPath().toAbsolutePath()));
        this.projectRecord.setToolGitVersion(identifyGitVersion(Paths.get(System.getProperty("user.dir"))));

        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case GRADLE:
                this.setupGradleProjectPaths(this.projectRecord);
                break;
            case MAVEN:
                this.setupMavenProjectPaths(this.projectRecord);
                break;
            case JAIGANTIC:
            case ANT:
            default:
                throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        this.setupBuildFile(this.projectRecord);
        this.setupTestFramework(this.projectRecord);

        this.projectRecord.store();

        if (this.projectRecord.getMainSourcePath() == null || !Files.exists(this.projectRecord.getMainSourcePath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Main source path '" + this.projectRecord.getMainSourcePath() + "' does not exist.");
        }
        if (this.projectRecord.getTestSourcePath() == null || !Files.exists(this.projectRecord.getTestSourcePath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Test source path '" + this.projectRecord.getTestSourcePath() + "' does not exist.");
        }
        if (this.projectRecord.getTestFramework() == TestFramework.UNKNOWN) {
            throw new RuntimeException("Cannot setup project" + this.projectRecord.getRootPath() + ". No supported test framework identified.");
        }

        scheduleTask.accept(new CleanupTask(ProcessingStage.CLEANUP_PROJECT, this.projectRecord));
        scheduleTask.accept(new AddDependenciesTask(ProcessingStage.ADD_DEPENDENCIES, this.projectRecord));
        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_ORIGINAL, this.projectRecord));

        if (this.projectRecord.getUseTestGeneration()) {
            scheduleTask.accept(new EvoSuiteGenerationTask(ProcessingStage.GENERATE_EVOSUITE_TESTS, this.projectRecord));
            scheduleTask.accept(new EvoSuitePostprocessingTask(ProcessingStage.POSTPROCESS_EVOSUITE_TESTS, this.projectRecord));
        }

        if (this.projectRecord.getUseTestGeneralization()) {
            scheduleTask.accept(new SpoonModelBuildingTask(ProcessingStage.BUILD_SPOON_MODEL, this.projectRecord));

            scheduleTask.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_ORIGINAL, this.projectRecord));
            scheduleTask.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL, this.projectRecord));
            scheduleTask.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL, this.projectRecord));
            scheduleTask.accept(new TestFilteringTask(ProcessingStage.FILTER_TESTS_ORIGINAL, this.projectRecord));
            scheduleTask.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_ORIGINAL, this.projectRecord));

            scheduleTask.accept(new TestAnalysisTask(ProcessingStage.ANALYZE_TESTS, this.projectRecord));
            scheduleTask.accept(new TestFilteringTask(ProcessingStage.FILTER_TESTS, this.projectRecord));
            scheduleTask.accept(new TestFilteringTask(ProcessingStage.FILTER_ASSERTIONS, this.projectRecord));

            scheduleTask.accept(new JpfInstrumentationTask(ProcessingStage.ADD_JPF_INSTRUMENTATION, this.projectRecord));
            scheduleTask.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_INSTRUMENTED, this.projectRecord));
            scheduleTask.accept(new JpfExecutionTask(ProcessingStage.EXECUTE_JPF, this.projectRecord));
            scheduleTask.accept(new JpfAnalysisTask(ProcessingStage.ANALYZE_JPF, this.projectRecord));
            scheduleTask.accept(new CleanupTask(ProcessingStage.CLEANUP_JPF_INSTRUMENTATION, this.projectRecord));

            scheduleTask.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_INITIAL, this.projectRecord));
            scheduleTask.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_INITIAL, this.projectRecord));

            scheduleTask.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_INITIAL, this.projectRecord));
            scheduleTask.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_INITIAL, this.projectRecord));
            scheduleTask.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_INITIAL, this.projectRecord));

            for (String variant : Configuration.getGeneralizationVariants()) {
                scheduleTask.accept(new CleanupTask(ProcessingStage.CLEANUP_GENERALIZATION, variant, this.projectRecord));

                scheduleTask.accept(new TestGeneralizationTask(ProcessingStage.GENERALIZE_TESTS, variant, this.projectRecord));
                scheduleTask.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_GENERALIZED, variant, this.projectRecord));

                scheduleTask.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_GENERALIZED, variant, this.projectRecord));
                scheduleTask.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED, variant, this.projectRecord));
                scheduleTask.accept(new TestFilteringTask(ProcessingStage.FILTER_GENERALIZATIONS, variant, this.projectRecord));

                scheduleTask.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED, variant, this.projectRecord));
                scheduleTask.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED, variant, this.projectRecord));
            }
        }
    }

    private ProjectType identifyProjectType(Path projectRootPath) {
        if (Files.exists(projectRootPath.resolve(Configuration.MAVEN_DEFAULT_BUILD_FILE))) {
            return ProjectType.MAVEN;
        } else if (Files.exists(projectRootPath.resolve(Configuration.GRADLE_DEFAULT_BUILD_FILE))) {
            return ProjectType.GRADLE;
        } else if (Files.exists(projectRootPath.resolve("build.command"))) {
            return ProjectType.JAIGANTIC;
        } else if (Files.exists(projectRootPath.resolve("build.xml"))) {
            return ProjectType.ANT;
        }
        return ProjectType.UNKNOWN;
    }

    private static String identifyGitVersion(Path directoryPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File gitDir = directoryPath.resolve(".git").toFile();

        if (!gitDir.exists() || !gitDir.isDirectory()) {
            return null;
        }

        try (Repository repository = builder.setGitDir(gitDir).build()) {
            if (repository.resolve("HEAD") == null) {
                return null;
            }
            return repository.resolve("HEAD").getName();
        }
    }

    private void setupBuildFile(ProjectRecord projectRecord) throws IOException {
        Path sourcePath;
        Path destinationPath;
        Path buildDataPath;

        if (projectRecord.getType() == ProjectType.MAVEN) {
            sourcePath = projectRecord.getRootPath().resolve(Configuration.MAVEN_DEFAULT_BUILD_FILE);
            destinationPath = projectRecord.getRootPath().resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
            buildDataPath = projectRecord.getDataPath()
                .resolve("project-id-" + this.getProjectId())
                .resolve(Configuration.TOOL_NAME_LOWER + "-data/build")
                .resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE);
        } else if (projectRecord.getType() == ProjectType.GRADLE) {
            sourcePath = projectRecord.getRootPath().resolve(Configuration.GRADLE_DEFAULT_BUILD_FILE);
            destinationPath = projectRecord.getRootPath().resolve(Configuration.GRADLE_CUSTOM_BUILD_FILE);
            buildDataPath = projectRecord.getDataPath()
                .resolve("project-id-" + this.getProjectId())
                .resolve(Configuration.TOOL_NAME_LOWER + "-data/build")
                .resolve(Configuration.GRADLE_CUSTOM_BUILD_FILE);
        } else {
            throw new RuntimeException("Failed to setup build file. Operation is not implemented for projects of type " + projectRecord.getType() + ".");
        }

        Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);

        Files.createDirectories(buildDataPath.getParent());
        Files.copy(destinationPath, buildDataPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void setupTestFramework(ProjectRecord projectRecord) {
        Pattern junit4Pattern = Pattern.compile("junit-([0-9.]+)\\.jar");
        Matcher junit4Matcher = junit4Pattern.matcher(projectRecord.getClasspath());

        Pattern junit5Pattern = Pattern.compile("junit-jupiter-api-([0-9.]+)\\.jar");
        Matcher junit5Matcher = junit5Pattern.matcher(projectRecord.getClasspath());

        if (junit4Matcher.find()) {
            projectRecord.setTestFramework(TestFramework.JUNIT_4);
            projectRecord.setTestFrameworkVersion(junit4Matcher.group(1));
        } else if (junit5Matcher.find()) {
            projectRecord.setTestFramework(TestFramework.JUNIT_5);
            projectRecord.setTestFrameworkVersion(junit5Matcher.group(1));
        } else {
            projectRecord.setTestFramework(TestFramework.UNKNOWN);
            projectRecord.setTestFrameworkVersion(null);
        }
    }

    private void setupGradleProjectPaths(ProjectRecord projectRecord) {
        if (projectRecord.getMainSourcePath() == null) {
            projectRecord.setMainSourcePath(projectRecord.getRootPath().resolve("src/main/java"));
        }
        if (projectRecord.getTestSourcePath() == null) {
            projectRecord.setTestSourcePath(projectRecord.getRootPath().resolve("src/test/java"));
        }
        if (projectRecord.getMainCompiledPath() == null) {
            projectRecord.setMainCompiledPath(projectRecord.getRootPath().resolve("build/classes/java/main"));
        }
        if (projectRecord.getTestCompiledPath() == null) {
            projectRecord.setTestCompiledPath(projectRecord.getRootPath().resolve("build/classes/java/test"));
        }
        if (projectRecord.getTestReportsPath() == null) {
            projectRecord.setTestReportsPath(projectRecord.getRootPath().resolve("build/test-results/test"));
        }
        if (projectRecord.getCoverageReportsPath() == null) {
            projectRecord.setCoverageReportsPath(projectRecord.getRootPath().resolve("build/reports/jacoco/test"));
        }
        if (projectRecord.getMutationReportsPath() == null) {
            projectRecord.setMutationReportsPath(projectRecord.getRootPath().resolve("build/reports/pitest"));
        }
        this.setupGradleProjectClasspath(projectRecord);
    }

    private void setupGradleProjectClasspath(ProjectRecord projectRecord) {
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

    private void setupMavenProjectPaths(ProjectRecord projectRecord) throws IOException, InterruptedException {
        if (projectRecord.getMainSourcePath() == null) {
            projectRecord.setMainSourcePath(projectRecord.getRootPath().resolve("src/main/java"));
        }
        if (projectRecord.getTestSourcePath() == null) {
            projectRecord.setTestSourcePath(projectRecord.getRootPath().resolve("src/test/java"));
        }
        if (projectRecord.getMainCompiledPath() == null) {
            projectRecord.setMainCompiledPath(projectRecord.getRootPath().resolve("target/classes"));
        }
        if (projectRecord.getTestCompiledPath() == null) {
            projectRecord.setTestCompiledPath(projectRecord.getRootPath().resolve("target/test-classes"));
        }
        if (projectRecord.getTestReportsPath() == null) {
            projectRecord.setTestReportsPath(projectRecord.getRootPath().resolve("target/surefire-reports"));
        }
        if (projectRecord.getCoverageReportsPath() == null) {
            projectRecord.setCoverageReportsPath(projectRecord.getRootPath().resolve("target/site/jacoco"));
        }
        if (projectRecord.getMutationReportsPath() == null) {
            projectRecord.setMutationReportsPath(projectRecord.getRootPath().resolve("target/pit-reports"));
        }
        this.setupMavenProjectClasspath(projectRecord);
    }

    private void setupMavenProjectClasspath(ProjectRecord projectRecord) throws IOException, InterruptedException {
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
                if (previousLine.contains("Dependencies classpath:")) {
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
}
