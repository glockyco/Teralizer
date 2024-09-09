package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
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

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Gson gson =  context.get(TaskContext.GSON);
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        this.createDriverClassFile(velocityEngine);
        this.createJpfConfigFile(gson, velocityEngine);

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_INSTRUMENTED, this.projectRecord));
        scheduleTask.accept(new JpfExecutionTask(ProcessingStage.JPF_EXECUTION, this.projectRecord, this.testRecord));
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
        return this.testRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        return "JpfInstrumentationTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + this.testRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpfInstrumentationTask)) return false;
        JpfInstrumentationTask that = (JpfInstrumentationTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.testRecord.getId(), that.testRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId(), this.testRecord.getId());
    }
}
