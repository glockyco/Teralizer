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
import teralizer.domain.MethodParameter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JpfInstrumentationTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            try {
                this.executeTask(context);
            } catch (Exception e) {
                this.testRecord.setIsIncluded(false);
                this.testRecord.setExclusionInfo("Excluded by " + this + ".");
                this.testRecord.store();
                throw e;
            }
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
        context.put("driverPackageName", this.testRecord.getDriverClassPackage());
        context.put("driverClassName", this.testRecord.getDriverClassName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassPackage() + "." + this.testRecord.getTestClassName());
        context.put("testClassName", this.testRecord.getTestClassName());
        context.put("testMethodName", this.testRecord.getTestMethodName());

        File driverClassFile = new File(this.testRecord.getDriverClassPath());
        driverClassFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(driverClassFile)) {
            Template template = velocityEngine.getTemplate("driver-class.vm");
            template.merge(context, fileWriter);
        }
    }

    private void createJpfConfigFile(Gson gson, VelocityEngine velocityEngine) throws IOException {
        String driverClassQualifiedName = this.testRecord.getDriverClassPackage() + "." + this.testRecord.getDriverClassName();
        String testClassQualifiedName = this.testRecord.getTestClassPackage() + "." + this.testRecord.getTestClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + this.testRecord.getTestMethodName();
        String testedClassQualifiedName = this.testRecord.getTestedClassPackage() + "." + this.testRecord.getTestedClassName();
        // @TODO: Include method parameter types in the qualified name of the tested method.
        String testedMethodQualifiedName = testedClassQualifiedName + "." + this.testRecord.getTestedMethodName();

        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.testRecord.getTestedMethodParamTypes(), type);
        String symbolicParams = testedMethodParameters.stream().map(p -> "sym").collect(Collectors.joining("#"));
        String symbolicMethod = testedMethodQualifiedName + "(" + symbolicParams + ")";

        VelocityContext context = new VelocityContext();
        context.put("classpath", this.projectRecord.getClasspath());
        context.put("symbolicMethod", symbolicMethod);

        context.put("driverClassQualifiedName", driverClassQualifiedName);
        context.put("testClassQualifiedName", testClassQualifiedName);
        context.put("testMethodQualifiedName", testMethodQualifiedName);
        context.put("testedClassQualifiedName", testedClassQualifiedName);
        context.put("testedMethodQualifiedName", testedMethodQualifiedName);
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

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return this.testRecord == null ? null : this.testRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        Integer testRecordId = this.testRecord == null ? null : this.testRecord.getId();
        return "JpfInstrumentationTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + testRecordId +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpfInstrumentationTask)) return false;
        JpfInstrumentationTask that = (JpfInstrumentationTask) o;
        Integer thisTestRecordId = this.testRecord == null ? null : this.testRecord.getId();
        Integer thatTestRecordId = that.testRecord == null ? null : that.testRecord.getId();
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(thisTestRecordId, thatTestRecordId);
    }

    @Override
    public int hashCode() {
        Integer testRecordId = this.testRecord == null ? null : this.testRecord.getId();
        return Objects.hash(this.stage, this.projectRecord.getId(), testRecordId);
    }
}
