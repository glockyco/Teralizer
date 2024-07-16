package teralizer.processing.task;

public abstract class AbstractTask implements Task {
    private Integer projectId;
    private Integer testId;
    private Integer generalizationId;

    @Override
    public Integer getProjectId() {
        return this.projectId;
    }

    @Override
    public Integer getTestId() {
        return this.testId;
    }

    @Override
    public Integer getGeneralizationId() {
        return this.generalizationId;
    }

    protected void setProjectId(Integer projectId) {
        this.projectId = projectId;
    }

    protected void setTestId(Integer testId) {
        this.testId = testId;
    }

    protected void setGeneralizationId(Integer generalizationId) {
        this.generalizationId = generalizationId;
    }
}
