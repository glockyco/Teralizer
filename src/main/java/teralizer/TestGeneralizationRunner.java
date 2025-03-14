package teralizer;

import com.google.gson.Gson;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import teralizer.processing.ProcessingPipeline;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectType;
import teralizer.processing.TaskContext;
import teralizer.processing.task.ProjectDownloadTask;
import teralizer.util.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class TestGeneralizationRunner {

    public static void main(String[] args) throws Exception {
        new TestGeneralizationRunner().run();
    }

    public void run() throws IOException {
        Path dbDirectory = Configuration.DB_PATH.getParent();
        if (!Files.exists(dbDirectory)) {
            Files.createDirectories(dbDirectory);
        }

        DSLContext create = DSL.using(Configuration.DB_CONNECTION_STRING);

        if (!Files.exists(Configuration.DB_PATH) || Files.size(Configuration.DB_PATH) == 0) {
            String sql = new String(Files.readAllBytes(Configuration.DB_DDL_PATH));
            create.parser().parse(sql).forEach(create::execute);
        }

        ProcessingPipeline pipeline = new ProcessingPipeline(create);
        pipeline.getContext().put(TaskContext.DSL_CONTEXT, create);
        pipeline.getContext().put(TaskContext.GSON, new Gson());
        pipeline.getContext().put(TaskContext.VELOCITY_ENGINE, this.createVelocityEngine());

        long startTime = System.currentTimeMillis();

        ProjectRecord projectRecord = create.newRecord(Tables.PROJECT);
        projectRecord.setType(ProjectType.UNKNOWN);
        projectRecord.setRootPath(Configuration.getProjectRootPath());
        projectRecord.setDataPath(Configuration.getProjectDataPath());
        projectRecord.setMainSourcePath(Configuration.getProjectMainSourcePath());
        projectRecord.setTestSourcePath(Configuration.getProjectTestSourcePath());
        projectRecord.setMainCompiledPath(Configuration.getProjectMainCompiledPath());
        projectRecord.setTestCompiledPath(Configuration.getProjectTestCompiledPath());
        projectRecord.setTestReportsPath(Configuration.getProjectTestReportsPath());
        projectRecord.setCoverageReportsPath(Configuration.getProjectCoverageReportsPath());
        projectRecord.setMutationReportsPath(Configuration.getProjectMutationReportsPath());
        projectRecord.setUseTestGeneration(Configuration.getProjectUseTestGeneration());
        projectRecord.setUseTestGeneralization(Configuration.getProjectUseTestGeneralization());
        projectRecord.setConfiguration(Configuration.render());
        projectRecord.store();

        pipeline.addTask(new ProjectDownloadTask(ProcessingStage.DOWNLOAD_PROJECT, projectRecord));
        pipeline.executeAll();

        long endTime = System.currentTimeMillis();

        projectRecord.setRuntime((endTime - startTime) / 1000.0f);
        projectRecord.store();
    }

    private VelocityEngine createVelocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader.file.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");

        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        return velocityEngine;
    }
}
