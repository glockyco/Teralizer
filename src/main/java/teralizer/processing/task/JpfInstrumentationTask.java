package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JpfInstrumentationTask extends AbstractTask {

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            this.executeTask(context);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<TestRecord> testRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.projectRecord.getId()))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();

        for (TestRecord testRecord : testRecords) {
            scheduleTask.accept(new JpfInstrumentationTask(this.stage, this.projectRecord, testRecord));
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        Gson gson =  context.get(TaskContext.GSON);
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        this.createDriverClassFile(velocityEngine);
        this.createJpfConfigFile(gson, velocityEngine);
    }

    private void createDriverClassFile(VelocityEngine velocityEngine) throws IOException {
        VelocityContext context = new VelocityContext();
        context.put("driverPackageName", this.testRecord.getDriverPackageName());
        context.put("driverClassName", this.testRecord.getDriverClassName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testClassName", this.testRecord.getTestClassName());
        context.put("testMethodName", this.testRecord.getTestMethodName());

        File driverClassFile = new File(this.testRecord.getDriverFilePath());
        driverClassFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(driverClassFile)) {
            Template template = velocityEngine.getTemplate("driver-class.vm");
            template.merge(context, fileWriter);
        }
    }

    private void createJpfConfigFile(Gson gson, VelocityEngine velocityEngine) throws IOException {
        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.testRecord.getTestedMethodParamTypes(), type);
        String symbolicParams = testedMethodParameters.stream().map(p -> "sym").collect(Collectors.joining("#"));
        String symbolicMethod = this.testRecord.getTestedMethodQualifiedName() + "(" + symbolicParams + ")";

        VelocityContext context = new VelocityContext();
        context.put("classpath", this.projectRecord.getClasspath());
        context.put("symbolicMethod", symbolicMethod);

        context.put("maxExecutionTime", TestGeneralizationRunner.JPF_MAX_EXECUTION_TIME);
        context.put("driverClassQualifiedName", this.testRecord.getDriverClassQualifiedName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testMethodQualifiedName", this.testRecord.getTestMethodQualifiedName());
        context.put("testedClassQualifiedName", this.testRecord.getTestedClassQualifiedName());
        context.put("testedMethodQualifiedName", this.testRecord.getTestedMethodQualifiedName());
        context.put("inputSpecificationPath", this.testRecord.getInputSpecificationPath());
        context.put("outputSpecificationPath", this.testRecord.getOutputSpecificationPath());

        File jpfConfigFile = new File(this.testRecord.getJpfConfigPath());
        jpfConfigFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(jpfConfigFile)) {
            // @TODO: Add execution of @BeforeAll, @Before, @After, @AfterAll to the template.
            // @TODO: How to handle methods (without parameters) that depend on object state?
            Template template = velocityEngine.getTemplate("jpf-config.vm");
            template.merge(context, fileWriter);
        }
    }
}
