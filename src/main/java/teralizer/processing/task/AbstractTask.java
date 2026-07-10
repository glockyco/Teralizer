package teralizer.processing.task;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.function.Consumer;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.Configuration;

public abstract class AbstractTask implements Task {

    protected abstract void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception;

    protected ProcessingStage stage;
    protected String variant;

    protected ProjectRecord projectRecord;
    protected TestRecord testRecord;
    protected AssertionRecord assertionRecord;
    protected GeneralizationRecord generalizationRecord;

    /**
     * The Maven build file a reduction or test-execution stage runs under. The ORIGINAL variant
     * keeps the project's native, unfloored runner (its surefire config is left as declared).
     * INITIAL and GENERALIZED use the floored POM so the JaCoCo agent attaches (its late-bound
     * argLine survives a project-declared argLine) and jqwik property tests are discovered. Keyed
     * on the stage, not the variant, because ORIGINAL and INITIAL both carry a null variant.
     */
    static String mavenBuildFileFor(ProcessingStage stage) {
        switch (stage) {
            case EXECUTE_TESTS_ORIGINAL:
            case COLLECT_JACOCO_DATA_ORIGINAL:
            case COLLECT_PIT_DATA_ORIGINAL:
                return Configuration.MAVEN_CUSTOM_BUILD_FILE;
            case EXECUTE_TESTS_INITIAL:
            case COLLECT_JACOCO_DATA_INITIAL:
            case COLLECT_PIT_DATA_INITIAL:
            case EXECUTE_TESTS_GENERALIZED:
            case COLLECT_JACOCO_DATA_GENERALIZED:
            case COLLECT_PIT_DATA_GENERALIZED:
                return Configuration.MAVEN_GENERALIZED_BUILD_FILE;
            default:
                throw new IllegalArgumentException("No Maven build-file mapping for stage: " + stage);
        }
    }

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
    public String getVariant() {
        return this.variant;
    }

    @Override
    public Long getProjectId() {
        return this.projectRecord == null ? null : this.projectRecord.getId();
    }

    @Override
    public Long getTestId() {
        return this.testRecord == null ? null : this.testRecord.getId();
    }

    @Override
    public Long getAssertionId() {
        return this.assertionRecord == null ? null : this.assertionRecord.getId();
    }

    @Override
    public Long getGeneralizationId() {
        return this.generalizationRecord == null ? null : this.generalizationRecord.getId();
    }

    @Override
    public String toString() {
        Long projectId = this.getProjectId();
        Long testId = this.getTestId();
        Long assertionId = this.getAssertionId();
        Long generalizationId = this.getGeneralizationId();

        String str = this.getClass().getSimpleName() + "{";
        str += "stage=" + this.getStage();
        str += this.getVariant() == null ? "" : ", variant=" + this.getVariant();
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

        Long thisProjectId = this.getProjectId();
        Long thatProjectId = that.getProjectId();
        Long thisTestId = this.getTestId();
        Long thatTestId = that.getTestId();
        Long thisAssertionId = this.getAssertionId();
        Long thatAssertionId = that.getAssertionId();
        Long thisGeneralizationId = this.getGeneralizationId();
        Long thatGeneralizationId = that.getGeneralizationId();

        return this.stage == that.stage
            && Objects.equals(this.variant, that.variant)
            && Objects.equals(thisProjectId, thatProjectId)
            && Objects.equals(thisTestId, thatTestId)
            && Objects.equals(thisAssertionId, thatAssertionId)
            && Objects.equals(thisGeneralizationId, thatGeneralizationId);
    }

    @Override
    public int hashCode() {
        Long projectId = this.getProjectId();
        Long testId = this.getTestId();
        Long assertionId = this.getAssertionId();
        Long generalizationId = this.getGeneralizationId();

        return Objects.hash(this.stage, this.variant, projectId, testId, assertionId, generalizationId);
    }
}
