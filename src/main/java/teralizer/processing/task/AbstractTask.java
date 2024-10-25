package teralizer.processing.task;

import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.util.Objects;
import java.util.function.Consumer;

public abstract class AbstractTask implements Task {

    protected abstract void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception;

    protected ProcessingStage stage;
    protected GeneralizationVariant variant;

    protected ProjectRecord projectRecord;
    protected TestRecord testRecord;
    protected GeneralizationRecord generalizationRecord;

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        try {
            this.executeInternal(context, reportInfo, scheduleTask);
        } catch (Exception e) {
            if (this.testRecord != null) {
                this.testRecord.setIsIncluded(false);
                this.testRecord.setExclusionInfo("Excluded by " + this + ".");
                this.testRecord.store();
            }
            if (this.generalizationRecord != null) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo("Excluded by " + this + ".");
                this.generalizationRecord.store();
            }
            throw e;
        }
    }

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public GeneralizationVariant getVariant() {
        return this.variant;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord == null ? null : this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return this.testRecord == null ? null : this.testRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return this.generalizationRecord == null ? null : this.generalizationRecord.getId();
    }

    @Override
    public String toString() {
        Integer projectId = this.getProjectId();
        Integer testId = this.getTestId();
        Integer generalizationId = this.getGeneralizationId();

        String str = this.getClass().getSimpleName() + "{";
        str += "stage=" + this.getStage();
        str += this.getVariant() == null ? "" : ", tool=" + this.getVariant();
        str += projectId == null ? "" : ", projectId=" + projectId;
        str += testId == null ? "" : ", testId=" + testId;
        str += generalizationId == null ? "" : ", generalizationId=" + generalizationId;
        str += "}";

        return str;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTask)) return false;
        AbstractTask that = (AbstractTask) o;

        Integer thisProjectId = this.getProjectId();
        Integer thatProjectId = that.getProjectId();
        Integer thisTestId = this.getTestId();
        Integer thatTestId = that.getTestId();
        Integer thisGeneralizationId = this.getGeneralizationId();
        Integer thatGeneralizationId = that.getGeneralizationId();

        return this.stage == that.stage
            && this.variant == that.variant
            && Objects.equals(thisProjectId, thatProjectId)
            && Objects.equals(thisTestId, thatTestId)
            && Objects.equals(thisGeneralizationId, thatGeneralizationId);
    }

    @Override
    public int hashCode() {
        Integer projectId = this.getProjectId();
        Integer testId = this.getTestId();
        Integer generalizationId = this.getGeneralizationId();

        return Objects.hash(this.stage, this.variant, projectId, testId, generalizationId);
    }
}
