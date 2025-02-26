package teralizer.processing.task;

import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.Consumer;

public abstract class AbstractTask implements Task {

    protected abstract void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception;

    protected ProcessingStage stage;
    protected GeneralizationVariant variant;
    protected GeneralizationVariant combinedVariant;

    protected ProjectRecord projectRecord;
    protected TestRecord testRecord;
    protected AssertionRecord assertionRecord;
    protected GeneralizationRecord generalizationRecord;

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        try {
            this.executeInternal(context, reportInfo, scheduleTask);
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            String stackTrace = stringWriter.toString();

            String exclusionMessage = String.format("Excluded by %s.%n%n%s", this, stackTrace);

            if (this.generalizationRecord != null) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo(exclusionMessage);
                this.generalizationRecord.store();
            } else if (this.assertionRecord != null) {
                this.assertionRecord.setIsIncluded(false);
                this.assertionRecord.setExclusionInfo(exclusionMessage);
                this.assertionRecord.store();
            } else if (this.testRecord != null) {
                this.testRecord.setIsIncluded(false);
                this.testRecord.setExclusionInfo(exclusionMessage);
                this.testRecord.store();
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
    public GeneralizationVariant getCombinedVariant() {
        return this.combinedVariant;
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
    public Integer getAssertionId() {
        return this.assertionRecord == null ? null : this.assertionRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return this.generalizationRecord == null ? null : this.generalizationRecord.getId();
    }

    @Override
    public String toString() {
        Integer projectId = this.getProjectId();
        Integer testId = this.getTestId();
        Integer assertionId = this.getAssertionId();
        Integer generalizationId = this.getGeneralizationId();

        String str = this.getClass().getSimpleName() + "{";
        str += "stage=" + this.getStage();
        str += this.getVariant() == null ? "" : ", tool=" + this.getVariant();
        str += this.getCombinedVariant() == null ? "" : ", combined_variant=" + this.getCombinedVariant();
        str += projectId == null ? "" : ", projectId=" + projectId;
        str += testId == null ? "" : ", testId=" + testId;
        str += assertionId == null ? "" : ", assertionId=" + assertionId;
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
        Integer thisAssertionId = this.getAssertionId();
        Integer thatAssertionId = that.getAssertionId();
        Integer thisGeneralizationId = this.getGeneralizationId();
        Integer thatGeneralizationId = that.getGeneralizationId();

        return this.stage == that.stage
            && this.variant == that.variant
            && this.combinedVariant == that.combinedVariant
            && Objects.equals(thisProjectId, thatProjectId)
            && Objects.equals(thisTestId, thatTestId)
            && Objects.equals(thisAssertionId, thatAssertionId)
            && Objects.equals(thisGeneralizationId, thatGeneralizationId);
    }

    @Override
    public int hashCode() {
        Integer projectId = this.getProjectId();
        Integer testId = this.getTestId();
        Integer assertionId = this.getAssertionId();
        Integer generalizationId = this.getGeneralizationId();

        return Objects.hash(this.stage, this.variant, this.combinedVariant, projectId, testId, assertionId, generalizationId);
    }
}
