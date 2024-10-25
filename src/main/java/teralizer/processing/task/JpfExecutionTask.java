package teralizer.processing.task;

import gov.nasa.jpf.Config;
import gov.nasa.jpf.Error;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFNativePeerException;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationListener;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JpfExecutionTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;

    public JpfExecutionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public JpfExecutionTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
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
            scheduleTask.accept(new JpfExecutionTask(this.stage, this.projectRecord, testRecord));
        }
    }

    private void executeTask(TaskContext context) {
        Config config = JPF.createConfig(new String[]{testRecord.getJpfConfigPath()});

        JPF jpf = new JPF(config);
        jpf.addListener(new TestGeneralizationListener(config));

        try {
            jpf.run();
        } catch (JPFNativePeerException e) {
            // Exception that is (likely) due to JPFs incorrect handling of shadowing.
            // See https://github.com/glockyco/test-generalization/issues/37 for further details
            throw new RuntimeException("Failed JPF execution due to exception in native peers.", e);
        }

        if (jpf.foundErrors()) {
            List<Error> errors = jpf.getSearchErrors();
            String errorMessage = "Identified " + errors.size() + " error(s) during JPF execution.\n\n--\n\n" +
                jpf.getSearchErrors().stream().map(
                    e -> e.getDescription() + "\n\n" + e.getDetails()
                ).collect(Collectors.joining("\n--\n\n"));
            throw new RuntimeException(errorMessage);
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
        return "JpfExecutionTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + testRecordId +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JpfExecutionTask)) return false;
        JpfExecutionTask that = (JpfExecutionTask) o;
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
