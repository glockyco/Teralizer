package teralizer.processing.task;

import java.io.File;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.commons.io.FilenameUtils;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.declaration.CtType;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.Configuration;

public class CleanupTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTask.class);

    private final Path projectPath;
    private final Path testSourcePath;

    public CleanupTask(ProcessingStage stage, Path projectPath, Path testSourcePath) {
        this.stage = stage;
        this.projectPath = projectPath;
        this.testSourcePath = testSourcePath;
    }

    public CleanupTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public CleanupTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.projectPath = projectRecord.getRootPath();
        this.testSourcePath = projectRecord.getTestSourcePath();
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.stage != ProcessingStage.CLEANUP_PROJECT
            && this.stage != ProcessingStage.CLEANUP_JPF_INSTRUMENTATION
            && this.stage != ProcessingStage.CLEANUP_GENERALIZATION
        ) {
            throw new RuntimeException("Cannot perform cleanup. Unsupported processing stage " + this.stage + ".");
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

        if (this.stage == ProcessingStage.CLEANUP_PROJECT && this.projectRecord == null) {
            File mavenBuildFile = this.projectPath.resolve(Configuration.MAVEN_CUSTOM_BUILD_FILE).toFile();
            File gradleBuildFile =  this.projectPath.resolve(Configuration.GRADLE_CUSTOM_BUILD_FILE).toFile();
            List<File> buildFiles = Arrays.asList(mavenBuildFile, gradleBuildFile);
            for (File buildFile : buildFiles) {
                if (buildFile.exists()) {
                    LOGGER.atInfo().log("Deleting " + Configuration.TOOL_NAME + " build file '" + buildFile + "'.");
                    if (!buildFile.delete()) {
                        throw new RuntimeException("Failed to delete " + Configuration.TOOL_NAME + " build file '" + buildFile + "'.");
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

        Files.walkFileTree(testSourcePath, new CleanupVisitor(
            context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER),
            this.projectRecord,
            this.stage
        ));

        // We do not automatically remove collected data in the DB and the data
        // directory. These should be preserved even if the generalization is
        // reverted to enable comparisons across multiple generalization runs.
    }

    private static class CleanupVisitor extends SimpleFileVisitor<Path> {

        private final Launcher spoonLauncher;
        private final ProjectRecord projectRecord;
        private final ProcessingStage stage;

        public CleanupVisitor(Launcher spoonLauncher, ProjectRecord projectRecord, ProcessingStage stage) {
            this.spoonLauncher = spoonLauncher;
            this.projectRecord = projectRecord;
            this.stage = stage;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            this.deleteIfEvoSuiteFile(file);
            this.deleteIfInstrumentationFile(file);
            this.deleteIfGeneralizationFile(file);
            return FileVisitResult.CONTINUE;
        }

        private void deleteIfEvoSuiteFile(Path file) {
            // We only delete EvoSuite files if they were (probably) generated
            // by us. Of course, we could use a more robust check. However, the
            // current check is likely "good enough" for most cases. After all,
            // there are not that many projects that use EvoSuite, and even
            // fewer ones (presumably: zero?) that are also processed by us.
            boolean shouldDeleteFile = this.stage == ProcessingStage.CLEANUP_PROJECT
                && this.projectRecord != null
                && this.projectRecord.getUseTestGeneration()
                && file.getFileName().toString().endsWith("ESTest.java");

            if (shouldDeleteFile) {
                LOGGER.atInfo().log("Deleting EvoSuite test file '" + file + "'.");
                file.toFile().delete();
            }
        }

        private void deleteIfInstrumentationFile(Path file) {
            String fileName = file.getFileName().toString();
            boolean isDriverFile = fileName.startsWith("_") && fileName.contains("_Driver_");
            boolean isInstrumentedFile = fileName.startsWith("_") && fileName.contains("_Instrumented_");
            boolean shouldDeleteFile = (isDriverFile || isInstrumentedFile) && (this.stage == ProcessingStage.CLEANUP_PROJECT || this.stage == ProcessingStage.CLEANUP_JPF_INSTRUMENTATION);

            if (shouldDeleteFile) {
                LOGGER.atInfo().log("Deleting JPF instrumentation file: " + file);
                if (file.toFile().delete()) {
                    this.deleteTypeFromSpoonModel(file);
                }

            }
        }

        private void deleteIfGeneralizationFile(Path file) {
            String fileName = file.getFileName().toString();
            boolean isGeneralizedFile = fileName.startsWith("_") && fileName.contains("_Generalized_");
            boolean shouldDeleteFile = isGeneralizedFile && (this.stage == ProcessingStage.CLEANUP_PROJECT || this.stage == ProcessingStage.CLEANUP_GENERALIZATION);

            if (shouldDeleteFile) {
                LOGGER.atInfo().log("Deleting generalization file: " + file);
                if (file.toFile().delete()) {
                    this.deleteTypeFromSpoonModel(file);
                }
            }
        }

        private void deleteTypeFromSpoonModel(Path file) {
            if (this.spoonLauncher != null) {
                String className = FilenameUtils.getBaseName(file.getFileName().toString());
                Path relativePath = this.projectRecord.getTestSourcePath().relativize(file.getParent());
                String packageName = relativePath.toString().replace("/", ".").replace("\\", ".");
                String qualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

                CtType<?> type = this.spoonLauncher.getFactory().Type().get(qualifiedName);
                if (type != null) {
                    LOGGER.atInfo().log("Deleting type from Spoon model: " + qualifiedName);
                    type.delete();
                }
            }
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
