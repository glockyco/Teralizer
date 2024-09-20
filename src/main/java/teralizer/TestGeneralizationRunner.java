package teralizer;

import com.google.gson.Gson;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.*;
import teralizer.processing.task.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class TestGeneralizationRunner {

    public static final String TOOL_NAME = "Teralizer";
    public static final Path DB_PATH = Paths.get("database/db.sqlite");

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGeneralizationRunner.class);

    public static void main(String[] args) {
        // Arguments: [benchmark]
        // - [benchmark]: Path to the benchmark directory, e.g., ../benchmarks/.
        //new TestGeneralizationRunner().run(args[0]);
        new TestGeneralizationRunner().run();
    }

    // @TODO: Add JPF models for more native methods (e.g., SymbolicStringHandler)?
    // @TODO: Use jpf-nhandler.

    // @TODO: Use some workflow engine to manage the tasks.
    // @TODO: Allow white-/blacklisting of individual tests/classes.
    // @TODO: Make individual tasks skip-able (=> "caching"?).
    // @TODO: Decide how to deal with files that are already created (perhaps from earlier runs).
    // @TODO: Compare how much longer test execution takes with JPF compared to "normally".

    public void run() {
        // @TODO: Get project directories from input args.

        List<ProjectInfo> projectInfos = Arrays.asList(
            new ProjectInfo(
                Paths.get("projects/EqBench"),
                Paths.get("projects/EqBench/src/main/code"),
                Paths.get("projects/EqBench/src/test/code"),
                Paths.get("projects/EqBench/target/classes"),
                Paths.get("projects/EqBench/target/test-classes"),
                Paths.get("projects/EqBench/target/surefire-reports")
            )
        );

        DSLContext create = DSL.using("jdbc:sqlite:" + DB_PATH.toAbsolutePath() + "?foreign_keys=on");

        ProcessingPipeline pipeline = new ProcessingPipeline(create);
        pipeline.getContext().put(TaskContext.DSL_CONTEXT, create);
        pipeline.getContext().put(TaskContext.GSON, new Gson());
        pipeline.getContext().put(TaskContext.VELOCITY_ENGINE, this.createVelocityEngine());

        for (ProjectInfo projectInfo : projectInfos) {
            pipeline.addTask(new CleanupTask(ProcessingStage.CLEANUP, projectInfo.getRootPath(), null, null));
            pipeline.executeAll();

            long startTime = System.currentTimeMillis();

            ProjectRecord projectRecord = create.newRecord(Tables.PROJECT);
            projectRecord.setType(ProjectType.UNKNOWN);
            projectRecord.setRootPath(projectInfo.getRootPath());
            projectRecord.setMainSourcePath(projectInfo.getMainSourcePath());
            projectRecord.setTestSourcePath(projectInfo.getTestSourcePath());
            projectRecord.setMainCompiledPath(projectInfo.getMainCompiledPath());
            projectRecord.setTestCompiledPath(projectInfo.getTestCompiledPath());
            projectRecord.setTestReportsPath(projectInfo.getTestReportsPath());
            projectRecord.store();

            pipeline.addTask(new ProjectDownloadTask(ProcessingStage.PROJECT_DOWNLOAD, projectRecord));
            pipeline.executeAll();

            long endTime = System.currentTimeMillis();

            projectRecord.setRuntime((endTime - startTime) / 1000.0f);
            projectRecord.store();

            if (LOGGER.isDebugEnabled()) {
                this.logCreatedRecords(create, projectRecord);
            }
        }

        // @TODO: Add shutdown handler.

        // @TODO: Store the pre-condition and post-condition of every test(method) in z3 representation (?).
        //   We need a z3 representation to identify duplicate tests.
        // @TODO: Implement test duplication detection.

        // @TODO: Execute @Before / @BeforeClass / @After / @AfterAll methods.
        // @TODO: Think about using JUnit InvocationInterceptor + @ExtendWith to handle more tests.
        // @TODO: Think about using Gradle's "test { executable = '/path/to/custom/java' }" to execute tests with SPF.

        // @TODO: Calculate how often multiple tests cover the same path / partition.

        // @TODO: Evaluate simplification of inputs + outputs.
    }

    private VelocityEngine createVelocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader.file.path", "src/main/resources/templates");

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        return velocityEngine;
    }

    private void logCreatedRecords(DSLContext create, ProjectRecord projectRecord) {
        Result<Record> testRecords = this.fetchTestRecords(create, projectRecord);
        Result<Record> generalizationRecords = this.fetchGeneralizationRecords(create, projectRecord);
        Result<Record> taskRecords = this.fetchTaskRecords(create, projectRecord);

        LOGGER.atDebug().log("Created project records:\n" + projectRecord);
        LOGGER.atDebug().log("Created test records:\n" + testRecords);
        LOGGER.atDebug().log("Created generalization records:\n" + generalizationRecords);
        LOGGER.atDebug().log("Created task records:\n" + taskRecords);
    }

    private Result<Record> fetchTestRecords(DSLContext create, ProjectRecord projectRecord) {
        return create.select(Tables.TEST.fields())
            .from(Tables.PROJECT)
            .join(Tables.TEST)
            .on(Tables.PROJECT.ID.eq(Tables.TEST.PROJECT_ID))
            .where(Tables.PROJECT.ID.eq(projectRecord.getId()))
            .fetch();
    }

    private Result<Record> fetchGeneralizationRecords(DSLContext create, ProjectRecord projectRecord) {
        return create.select(Tables.GENERALIZATION.fields())
            .from(Tables.PROJECT)
            .join(Tables.TEST)
            .on(Tables.PROJECT.ID.eq(Tables.TEST.PROJECT_ID))
            .join(Tables.GENERALIZATION)
            .on(Tables.TEST.ID.eq(Tables.GENERALIZATION.TEST_ID))
            .where(Tables.PROJECT.ID.eq(projectRecord.getId()))
            .fetch();
    }

    private Result<Record> fetchTaskRecords(DSLContext create, ProjectRecord projectRecord) {
        return create.select(Tables.TASK.fields())
            .from(Tables.PROJECT)
            .join(Tables.TASK)
            .on(Tables.PROJECT.ID.eq(Tables.TASK.PROJECT_ID))
            .where(Tables.PROJECT.ID.eq(projectRecord.getId()))
            .fetch();
    }
}
