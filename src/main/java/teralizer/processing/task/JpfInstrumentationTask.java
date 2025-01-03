package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.SUPPORTED_TYPES;

public class JpfInstrumentationTask extends AbstractTask {

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null, null);
    }

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
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

        Result<Record> records = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId());
        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfInstrumentationTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        Gson gson =  context.get(TaskContext.GSON);
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        this.updateAssertionRecord();
        this.createDriverClassFile(velocityEngine);
        this.createJpfConfigFile(gson, velocityEngine);
    }

    private void updateAssertionRecord() {
        String driverClassName = "_" + this.testRecord.getTestClassName() + "_Driver_" + this.testRecord.getTestMethodName() + "_" + this.getAssertionId();
        Path driverFilePath = Paths.get(this.testRecord.getTestFilePath()).getParent().resolve(driverClassName + ".java");

        String testPackageName = this.testRecord.getTestPackageName();

        this.assertionRecord.setDriverFilePath(driverFilePath.toString());
        this.assertionRecord.setDriverClassQualifiedName((testPackageName.isEmpty() ? "" : (testPackageName + ".")) + driverClassName);
        this.assertionRecord.setDriverPackageName(testPackageName);
        this.assertionRecord.setDriverClassName(driverClassName);

        Path jpfDataPath = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/jpf-data");
        String baseName = this.testRecord.getTestMethodQualifiedName() + "." + this.getAssertionId();
        Path jpfConfigPath = jpfDataPath.resolve(baseName + ".jpf");
        Path inputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.input.json");
        Path outputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.output.json");

        this.assertionRecord.setJpfConfigPath(jpfConfigPath.toString());
        this.assertionRecord.setInputSpecificationPath(inputSpecificationPath.toString());
        this.assertionRecord.setOutputSpecificationPath(outputSpecificationPath.toString());

        this.assertionRecord.store();
    }

    private void createDriverClassFile(VelocityEngine velocityEngine) throws IOException {
        VelocityContext context = new VelocityContext();
        context.put("driverPackageName", this.assertionRecord.getDriverPackageName());
        context.put("driverClassName", this.assertionRecord.getDriverClassName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testClassName", this.testRecord.getTestClassName());
        context.put("testMethodName", this.testRecord.getTestMethodName());

        File driverClassFile = new File(this.assertionRecord.getDriverFilePath());
        driverClassFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(driverClassFile)) {
            Template template = velocityEngine.getTemplate("driver-class.vm");
            template.merge(context, fileWriter);
        }
    }

    private void createJpfConfigFile(Gson gson, VelocityEngine velocityEngine) throws IOException {
        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.assertionRecord.getTestedMethodParameters(), type);
        String symbolicParams = testedMethodParameters.stream().map(p -> SUPPORTED_TYPES.contains(p.getType()) ? "sym" : "con").collect(Collectors.joining("#"));
        String symbolicMethod = this.assertionRecord.getTestedMethodQualifiedName() + "(" + symbolicParams + ")";

        VelocityContext context = new VelocityContext();
        context.put("classpath", this.projectRecord.getClasspath());
        context.put("symbolicMethod", symbolicMethod);

        context.put("maxExecutionTime", TestGeneralizationRunner.JPF_MAX_EXECUTION_TIME);
        context.put("maxPathConditionSize", TestGeneralizationRunner.JPF_MAX_PATH_CONDITION_SIZE);
        context.put("driverClassQualifiedName", this.assertionRecord.getDriverClassQualifiedName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testMethodQualifiedName", this.testRecord.getTestMethodQualifiedName());
        context.put("testedClassQualifiedName", this.assertionRecord.getTestedClassQualifiedName());
        context.put("testedMethodQualifiedName", this.assertionRecord.getTestedMethodQualifiedName());
        context.put("inputSpecificationPath", this.assertionRecord.getInputSpecificationPath());
        context.put("outputSpecificationPath", this.assertionRecord.getOutputSpecificationPath());

        File jpfConfigFile = new File(this.assertionRecord.getJpfConfigPath());
        jpfConfigFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(jpfConfigFile)) {
            // @TODO: Add execution of @BeforeAll, @Before, @After, @AfterAll to the template.
            // @TODO: How to handle methods (without parameters) that depend on object state?
            Template template = velocityEngine.getTemplate("jpf-config.vm");
            template.merge(context, fileWriter);
        }
    }
}
