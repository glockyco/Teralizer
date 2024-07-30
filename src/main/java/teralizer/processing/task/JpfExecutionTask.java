package teralizer.processing.task;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationListener;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.util.Objects;
import java.util.function.Consumer;

public class JpfExecutionTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;

    public JpfExecutionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
        this.runJpf(this.testRecord);

        scheduleTask.accept(new TestGeneralizationTask(ProcessingStage.TEST_GENERALIZATION, this.projectRecord, this.testRecord, "naive"));
    }

    private void runJpf(TestRecord testRecord) {
        Config config = JPF.createConfig(new String[]{testRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        jpf.addListener(new TestGeneralizationListener(config));
        jpf.run();
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
        return "JpfExecutionTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + this.testRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpfExecutionTask)) return false;
        JpfExecutionTask that = (JpfExecutionTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.testRecord.getId(), that.testRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId(), this.testRecord.getId());
    }
}
