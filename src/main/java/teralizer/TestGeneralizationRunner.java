package teralizer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import teralizer.processing.ConfigIdentity;
import teralizer.processing.PipelinePlanner;
import teralizer.processing.ProcessingPipeline;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProjectIdentity;
import teralizer.processing.ProjectType;
import teralizer.processing.TaskContext;
import teralizer.processing.task.ProjectDownloadTask;
import teralizer.util.Configuration;

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

        if (create.meta().getTables(Tables.PROJECT.getQualifiedName()).isEmpty()) {
            String sql = new String(Files.readAllBytes(Configuration.DB_DDL_PATH));
            create.parser().parse(sql).forEach(create::execute);
        }

        Gson gson = new GsonBuilder().disableHtmlEscaping().create();

        ProcessingPipeline pipeline = new ProcessingPipeline(create);
        pipeline.getContext().put(TaskContext.DSL_CONTEXT, create);
        pipeline.getContext().put(TaskContext.GSON, gson);
        pipeline.getContext().put(TaskContext.VELOCITY_ENGINE, this.createVelocityEngine());

        long startTime = System.currentTimeMillis();

        String identityHash = ConfigIdentity.hash(Configuration.renderIdentity());
        ProjectRecord projectRecord = ProjectIdentity.resolveOrCreate(create, Configuration.getProjectRootPath(), identityHash);
        boolean freshStart = projectRecord == null;
        if (freshStart) {
            projectRecord = create.newRecord(Tables.PROJECT);
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
        }
        ProjectIdentity.applyRunScopedConfiguration(
            projectRecord,
            Configuration.getProjectUseTestGeneration(),
            Configuration.getProjectUseTestGeneralization(),
            Configuration.getProjectUseTestReduction(),
            Configuration.render()
        );
        projectRecord.store();

        pipeline.addTask(new ProjectDownloadTask(ProcessingStage.DOWNLOAD_PROJECT, projectRecord, freshStart));
        pipeline.executeAll();
        new PipelinePlanner(create, pipeline).run(projectRecord);

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
