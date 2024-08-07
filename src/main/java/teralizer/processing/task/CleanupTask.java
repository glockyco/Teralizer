package teralizer.processing.task;

import org.apache.commons.io.FileUtils;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

public class CleanupTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTask.class);

    private final ProcessingStage stage;
    private final Path projectPath;

    private Integer projectId;
    private Integer testId;
    private Integer generalizationId;

    public CleanupTask(ProcessingStage stage) {
        this(stage, null, null, null);
    }

    public CleanupTask(ProcessingStage stage, Path projectPath, Integer testId, Integer generalizationId) {
        this.stage = stage;
        this.projectPath = projectPath;
        this.testId = testId;
        this.generalizationId = generalizationId;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        if (this.generalizationId != null) {
            this.cleanupGeneralization(create, reportInfo, this.generalizationId);
            this.generalizationId = null;
        } else if (this.testId != null) {
            this.cleanupTest(create, reportInfo, this.testId);
            this.testId = null;
        } else if (this.projectPath != null) {
            this.cleanupProject(create, reportInfo, this.projectPath);
        } else {
            this.cleanupAll(create);
        }
    }

    private void cleanupGeneralization(DSLContext create, Consumer<String> reportInfo, int generalizationId) {
        GeneralizationRecord generalizationRecord = create.selectFrom(Tables.GENERALIZATION).where(Tables.GENERALIZATION.ID.eq(generalizationId)).fetchOne();

        if (generalizationRecord == null) {
            LOGGER.atWarn().log("nothing to clean up for generalization with ID {}. Generalization could not be found.", generalizationId);
            return;
        }

        Paths.get(generalizationRecord.getGeneralizedClassPath()).toFile().delete();

        if (this.projectId ==  null) {
            this.projectId = create.select(Tables.TEST.PROJECT_ID).from(Tables.TEST).where(Tables.TEST.ID.eq(generalizationRecord.getTestId())).fetchOne().get(Tables.TEST.PROJECT_ID);
        }

        this.testId = generalizationRecord.getTestId();

        generalizationRecord.delete();

        String qualifiedName = generalizationRecord.getGeneralizedClassPackage() + "." + generalizationRecord.getGeneralizedClassName();
        reportInfo.accept("Cleaned up generalization with ID " + this.generalizationId + " (" + qualifiedName + ").");
    }

    private void cleanupTest(DSLContext create, Consumer<String> reportInfo, int testId) {
        TestRecord testRecord = create.selectFrom(Tables.TEST).where(Tables.TEST.ID.eq(testId)).fetchOne();

        if (testRecord == null) {
            LOGGER.atWarn().log("Nothing to clean up for test with ID {}. Test could not be found.", testId);
            return;
        }

        this.projectId = testRecord.getProjectId();

        List<GeneralizationRecord> generalizationRecords = create.select().from(Tables.GENERALIZATION).where(Tables.GENERALIZATION.TEST_ID.eq(testRecord.getId())).fetch().into(Tables.GENERALIZATION);
        for (GeneralizationRecord generalizationRecord : generalizationRecords) {
            Paths.get(generalizationRecord.getGeneralizedClassPath()).toFile().delete();
        }

        Paths.get(testRecord.getDriverClassPath()).toFile().delete();
        Paths.get(testRecord.getJpfConfigPath()).toFile().delete();
        Paths.get(testRecord.getInputSpecificationPath()).toFile().delete();
        Paths.get(testRecord.getOutputSpecificationPath()).toFile().delete();

        testRecord.delete();

        String qualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName() + "." + testRecord.getTestMethodName();
        reportInfo.accept("Cleaned up test with ID " + this.testId + " (" + qualifiedName + ").");
    }

    private void cleanupProject(DSLContext create, Consumer<String> reportInfo, Path projectPath) throws IOException {
        // No need to set a projectId for the project-level cleanup task because
        // the project is no longer visible in the DB once the task finishes.

        List<TestRecord> testRecords =  create.select(Tables.TEST.asterisk())
            .from(Tables.PROJECT)
            .join(Tables.TEST)
            .on(Tables.PROJECT.ID.eq(Tables.TEST.PROJECT_ID))
            .where(Tables.PROJECT.ROOT_PATH.eq(projectPath))
            .fetch()
            .into(TestRecord.class);

        for (TestRecord testRecord : testRecords) {
            FileUtils.deleteDirectory(Paths.get(testRecord.getDriverClassPath()).getParent().toFile());

            // Generalized test classes are stored in a subdirectory of the driver class directory,
            // so they are automatically removed when deleting the driver class directory.

            Paths.get(testRecord.getJpfConfigPath()).toFile().delete();
            Paths.get(testRecord.getInputSpecificationPath()).toFile().delete();
            Paths.get(testRecord.getOutputSpecificationPath()).toFile().delete();
        }

        int deletedCount = create.deleteFrom(Tables.PROJECT).where(Tables.PROJECT.ROOT_PATH.eq(projectPath)).execute();

        if (deletedCount == 0 && testRecords.isEmpty()) {
            LOGGER.atWarn().log("Nothing to clean up for project {}.", projectPath);
        } else {
            reportInfo.accept("Cleaned up project " + this.projectPath + ".");
        }
    }

    private void cleanupAll(DSLContext create) throws IOException {
        // No need to set a projectId for the overall cleanup task because
        // the project is no longer visible in the DB once the task finishes.

        List<TestRecord> testRecords = create.selectFrom(Tables.TEST).fetch();
        for (TestRecord testRecord : testRecords) {
            FileUtils.deleteDirectory(Paths.get(testRecord.getDriverClassPath()).getParent().toFile());
        }

        FileUtils.deleteDirectory(Paths.get("database").toFile());
        FileUtils.deleteDirectory(Paths.get("data").toFile());
    }

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectId;
    }

    @Override
    public Integer getTestId() {
        return this.testId;
    }

    @Override
    public Integer getGeneralizationId() {
        return this.generalizationId;
    }
}
