package teralizer.processing.task;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class CleanupTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTask.class);

    private final Path projectPath;
    private final Path testSourcePath;

    public CleanupTask(ProcessingStage stage, Path projectPath, Path testSourcePath) {
        this.stage = stage;
        this.projectPath = projectPath;
        this.testSourcePath = testSourcePath;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.projectPath == null) {
            LOGGER.atInfo().log("Skipping cleanup. Project path is null.");
            return;
        }
        if (!this.projectPath.toFile().exists()) {
            LOGGER.atInfo().log("Skipping cleanup. Project path '" + this.projectPath + "' does not exist.");
            return;
        }

        // We do not know whether we are dealing with a Maven or Gradle (or some other)
        // project. Even though this could be figured out, deleting all files that we
        // might have created in previous runs for any project type is much easier...

        File mavenBuildFile = this.projectPath.resolve(ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE).toFile();
        File gradleBuildFile =  this.projectPath.resolve(ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE).toFile();
        List<File> buildFiles = Arrays.asList(mavenBuildFile, gradleBuildFile);
        for (File buildFile : buildFiles) {
            if (buildFile.exists()) {
                LOGGER.atWarn().log("Deleting build file '" + buildFile + "'.");
                if (!buildFile.delete()) {
                    throw new RuntimeException("Failed to delete build file '" + buildFile + "'.");
                }
            }
        }

        Path testSourcePath = this.testSourcePath;
        if (testSourcePath == null) {
            testSourcePath = this.projectPath.resolve("src/test/java");
        }
        if (!testSourcePath.toFile().exists()) {
            testSourcePath = this.projectPath;
        }

        String dirToDelete = TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated";
        Files.walkFileTree(testSourcePath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (dir.getFileName().toString().equals(dirToDelete)) {
                    LOGGER.atInfo().log("Deleting directory '" + dir + "'.");
                    FileUtils.deleteDirectory(dir.toFile());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // We do not automatically remove collected data in the DB and the data
        // directory. These should be preserved even if the generalization is
        // reverted to enable comparisons across multiple generalization runs.
    }

    @Override
    public String toString() {
        return "CleanupTask{" +
            "stage=" + this.stage +
            ", projectPath=" + this.projectPath +
            ", testSourcePath=" + this.testSourcePath +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CleanupTask)) return false;
        CleanupTask that = (CleanupTask) o;
        return this.stage == that.stage && Objects.equals(this.projectPath, that.projectPath) && Objects.equals(this.testSourcePath, that.testSourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectPath, this.testSourcePath);
    }
}
