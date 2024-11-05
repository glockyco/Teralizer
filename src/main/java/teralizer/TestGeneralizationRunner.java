package teralizer;

import com.google.gson.Gson;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
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

    public void run() {
        // @TODO: Get project directories from input args.

        List<ProjectInfo> projectInfos = Arrays.asList(
            new ProjectInfo(
                Paths.get("projects/EqBench"),
                Paths.get("projects/EqBench/src/main/code"),
                Paths.get("projects/EqBench/src/test/code"),
                Paths.get("projects/EqBench/target/classes"),
                Paths.get("projects/EqBench/target/test-classes"),
                Paths.get("projects/EqBench/target/surefire-reports"),
                Paths.get("projects/EqBench/target/site/jacoco"),
                Paths.get("projects/EqBench/target/pit-reports")
            ),
            new ProjectInfo("projects/example-gradle-junit4"),
            new ProjectInfo("projects/example-gradle-junit5"),
            new ProjectInfo("projects/example-maven-junit4"),
            new ProjectInfo("projects/example-maven-junit5")
        );

        DSLContext create = DSL.using("jdbc:sqlite:" + DB_PATH.toAbsolutePath() + "?foreign_keys=on");

        ProcessingPipeline pipeline = new ProcessingPipeline(create);
        pipeline.getContext().put(TaskContext.DSL_CONTEXT, create);
        pipeline.getContext().put(TaskContext.GSON, new Gson());
        pipeline.getContext().put(TaskContext.VELOCITY_ENGINE, this.createVelocityEngine());

        for (ProjectInfo projectInfo : projectInfos) {
            long startTime = System.currentTimeMillis();

            ProjectRecord projectRecord = create.newRecord(Tables.PROJECT);
            projectRecord.setType(ProjectType.UNKNOWN);
            projectRecord.setRootPath(projectInfo.getRootPath());
            projectRecord.setDataPath(projectInfo.getDataPath());
            projectRecord.setMainSourcePath(projectInfo.getMainSourcePath());
            projectRecord.setTestSourcePath(projectInfo.getTestSourcePath());
            projectRecord.setMainCompiledPath(projectInfo.getMainCompiledPath());
            projectRecord.setTestCompiledPath(projectInfo.getTestCompiledPath());
            projectRecord.setTestReportsPath(projectInfo.getTestReportsPath());
            projectRecord.setCoverageReportsPath(projectInfo.getCoverageReportsPath());
            projectRecord.setMutationReportsPath(projectInfo.getMutationReportsPath());
            projectRecord.setUseTestGeneration(projectInfo.getUseTestGeneration());
            projectRecord.store();

            pipeline.addTask(new ProjectDownloadTask(ProcessingStage.DOWNLOAD_PROJECT, projectRecord));
            pipeline.executeAll();

            long endTime = System.currentTimeMillis();

            projectRecord.setRuntime((endTime - startTime) / 1000.0f);
            projectRecord.store();
        }
    }

    private VelocityEngine createVelocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader.file.path", "src/main/resources/templates");

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        return velocityEngine;
    }
}
