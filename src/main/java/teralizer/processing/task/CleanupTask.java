package teralizer.processing.task;

import org.apache.commons.io.FileUtils;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.GeneralizationVariant;
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

    public CleanupTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.projectPath = projectRecord.getRootPath();
        this.testSourcePath = projectRecord.getTestSourcePath();
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.stage != ProcessingStage.CLEANUP_PROJECT && this.stage != ProcessingStage.CLEANUP_GENERALIZATION) {
            throw new RuntimeException("Cannot preform cleanup. Unsupported processing stage " + this.stage + ".");
        }
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

        if (this.stage == ProcessingStage.CLEANUP_PROJECT) {
            File mavenBuildFile = this.projectPath.resolve(ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE).toFile();
            File gradleBuildFile =  this.projectPath.resolve(ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE).toFile();
            List<File> buildFiles = Arrays.asList(mavenBuildFile, gradleBuildFile);
            for (File buildFile : buildFiles) {
                if (buildFile.exists()) {
                    LOGGER.atInfo().log("Deleting build file '" + buildFile + "'.");
                    if (!buildFile.delete()) {
                        throw new RuntimeException("Failed to delete build file '" + buildFile + "'.");
                    }
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

        Files.walkFileTree(testSourcePath, new DirectoryDeletionVisitor(this.stage, this.variant));

        // We do not automatically remove collected data in the DB and the data
        // directory. These should be preserved even if the generalization is
        // reverted to enable comparisons across multiple generalization runs.
    }

    private static class DirectoryDeletionVisitor extends SimpleFileVisitor<Path> {

        private final ProcessingStage stage;
        private final GeneralizationVariant variant;

        public DirectoryDeletionVisitor(ProcessingStage stage, GeneralizationVariant variant) {
            this.stage = stage;
            this.variant = variant;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
            if (this.shouldDeleteDirectory(directory)) {
                LOGGER.atInfo().log("Deleting directory '" + directory + "'.");
                FileUtils.deleteDirectory(directory.toFile());
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        private boolean shouldDeleteDirectory(Path directory) {
            if (this.stage == ProcessingStage.CLEANUP_PROJECT) {
                return this.isGeneratedDirectory(directory);
            } else if (this.stage == ProcessingStage.CLEANUP_GENERALIZATION) {
                return this.isGeneratedDirectory(directory.getParent()) && this.isVariantDirectory(directory);
            } else {
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
            }
        }

        private boolean isGeneratedDirectory(Path directory) {
            return directory.getFileName().toString().equals(TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated");
        }

        private boolean isVariantDirectory(Path directory) {
            for (GeneralizationVariant variant : GeneralizationVariant.values()) {
                if (directory.getFileName().toString().equals(variant.toString().toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public String toString() {
        String str = this.getClass().getSimpleName() + "{";
        str += "stage=" + this.getStage();
        str += this.getVariant() == null ? "" : ", tool=" + this.getVariant();
        str += ", projectPath=" + this.projectPath;
        str += ", testSourcePath=" + this.testSourcePath;
        str += "}";
        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CleanupTask)) return false;
        CleanupTask that = (CleanupTask) o;
        return this.stage == that.stage
            && this.variant == that.variant
            && Objects.equals(this.projectPath, that.projectPath)
            && Objects.equals(this.testSourcePath, that.testSourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.variant, this.projectPath, this.testSourcePath);
    }
}
