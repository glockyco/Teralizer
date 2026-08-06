package teralizer.processing.task;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.BuildClasspathResolver;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectType;
import teralizer.processing.TaskContext;
import teralizer.processing.TestFramework;
import teralizer.util.Configuration;

public class ProjectSetupTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProjectSetupTask.class);

    private final boolean freshStart;

    public ProjectSetupTask(ProcessingStage stage, ProjectRecord projectRecord, boolean freshStart) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.freshStart = freshStart;
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

        this.scheduleBootstrapTasks(scheduleTask);
    }

    void scheduleBootstrapTasks(Consumer<Task> scheduleTask) {
        if (this.freshStart) {
            scheduleTask.accept(new CleanupTask(ProcessingStage.CLEANUP_PROJECT, this.projectRecord));
        }
        scheduleTask.accept(new AddDependenciesTask(ProcessingStage.ADD_DEPENDENCIES, this.projectRecord));
        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_ORIGINAL, this.projectRecord));
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

    void setupTestFramework(ProjectRecord projectRecord) {
        Pattern junitPattern = Pattern.compile("junit-([0-9.]+)\\.jar");
        Matcher junitMatcher = junitPattern.matcher(projectRecord.getClasspath());

        Pattern junit5Pattern = Pattern.compile("junit-jupiter-api-([0-9.]+)\\.jar");
        Matcher junit5Matcher = junit5Pattern.matcher(projectRecord.getClasspath());

        if (junitMatcher.find()) {
            String version = junitMatcher.group(1);
            projectRecord.setTestFramework(version.startsWith("3.") ? TestFramework.JUNIT_3 : TestFramework.JUNIT_4);
            projectRecord.setTestFrameworkVersion(version);
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
        projectRecord.setClasspath(BuildClasspathResolver.resolveGradle(
            projectRecord.getRootPath(),
            Configuration.GRADLE_DEFAULT_BUILD_FILE,
            projectRecord.getMainCompiledPath(),
            projectRecord.getTestCompiledPath()));
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

    /**
     * Resolve the original project's classpath at setup. After {@code AddDependenciesTask} adds the
     * tool's dependencies, {@code AddDependenciesTask} refreshes this from the custom build file, so
     * downstream consumers see jqwik/pitest/junit-platform.
     */
    private void setupMavenProjectClasspath(ProjectRecord projectRecord) throws IOException, InterruptedException {
        projectRecord.setClasspath(BuildClasspathResolver.resolveMaven(
            projectRecord.getRootPath(),
            Configuration.MAVEN_DEFAULT_BUILD_FILE,
            projectRecord.getMainCompiledPath(),
            projectRecord.getTestCompiledPath()));
    }
}
