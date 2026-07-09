package teralizer.processing.task;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

public class GeneralizedSourceRestoreTask extends AbstractTask {

    public GeneralizedSourceRestoreTask(String variant, ProjectRecord projectRecord) {
        this(ProcessingStage.RESTORE_GENERALIZED_BUILD, variant, projectRecord);
    }

    public GeneralizedSourceRestoreTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.stage != ProcessingStage.RESTORE_GENERALIZED_BUILD) {
            throw new RuntimeException("Cannot restore generalized sources. Unsupported processing stage " + this.stage + ".");
        }

        restoreGeneralizedSources(this.projectRecord, this.getProjectId(), this.variant);
        new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_GENERALIZED, this.variant, this.projectRecord)
            .buildProject(context, reportInfo);
    }

    public static Path generalizedSourceArchivePath(ProjectRecord projectRecord, long projectId, String variant) {
        return projectRecord.getDataPath()
            .resolve("project-id-" + projectId)
            .resolve("generalized-sources")
            .resolve(variant);
    }

    static void archiveGeneralizedSources(ProjectRecord projectRecord, long projectId, String variant) throws IOException {
        Path testSourceRoot = testSourceRoot(projectRecord);
        Path archiveRoot = generalizedSourceArchivePath(projectRecord, projectId, variant);
        deleteDirectory(archiveRoot);
        Files.createDirectories(archiveRoot);
        if (!Files.exists(testSourceRoot)) {
            return;
        }

        Files.walkFileTree(testSourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isGeneralizedSource(file)) {
                    Path relative = testSourceRoot.relativize(file);
                    Path destination = archiveRoot.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void restoreGeneralizedSources(ProjectRecord projectRecord, long projectId, String variant) throws IOException {
        Path testSourceRoot = testSourceRoot(projectRecord);
        Path archiveRoot = generalizedSourceArchivePath(projectRecord, projectId, variant);
        if (!Files.exists(archiveRoot)) {
            throw new RuntimeException("Cannot restore generalized sources. Archive path '" + archiveRoot + "' does not exist.");
        }

        deleteGeneralizedSources(testSourceRoot);
        Files.walkFileTree(archiveRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative = archiveRoot.relativize(file);
                Path destination = testSourceRoot.resolve(relative);
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void deleteAllGeneralizedSources(ProjectRecord projectRecord) throws IOException {
        deleteGeneralizedSources(testSourceRoot(projectRecord));
    }

    static void deleteGeneralizedSources(Path testSourceRoot) throws IOException {
        if (!Files.exists(testSourceRoot)) {
            return;
        }

        Files.walkFileTree(testSourceRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isGeneralizedSource(file)) {
                    Files.delete(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static boolean isGeneralizedSource(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("_") && fileName.contains("_Generalized_");
    }

    private static Path testSourceRoot(ProjectRecord projectRecord) {
        Path testSourcePath = projectRecord.getTestSourcePath();
        if (testSourcePath == null) {
            testSourcePath = projectRecord.getRootPath().resolve("src/test/java");
        }
        if (!Files.exists(testSourcePath)) {
            testSourcePath = projectRecord.getRootPath();
        }
        return testSourcePath;
    }

    private static void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
