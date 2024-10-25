package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.filter.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestFilteringTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFilteringTask.class);

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
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
                this.executeTask(context, reportInfo);
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
            scheduleTask.accept(new TestFilteringTask(this.stage, this.projectRecord, testRecord));
        }
    }

    private void executeTask(TaskContext context, Consumer<String> reportInfo) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        AssertionCountFilter assertionCountFilter = new AssertionCountFilter(create);
        MissingValueFilter missingValueFilter = new MissingValueFilter();
        ParameterTypeFilter parameterTypeFilter = new ParameterTypeFilter(gson);

        List<Filter> filters = Arrays.asList(assertionCountFilter, missingValueFilter, parameterTypeFilter);

        List<FilterResult> decisions = new ArrayList<>();
        for (Filter filter : filters) {
            decisions.add(filter.check(this.testRecord));
        }

        List<FilterResult> rejections = decisions.stream().filter(d -> d.getDecision() == FilterDecision.REJECT).collect(Collectors.toList());
        FilterDecision decision = rejections.isEmpty() ? FilterDecision.ACCEPT : FilterDecision.REJECT;
        String filterResults = decisions.stream().map(FilterResult::toString).collect(Collectors.joining("\n"));
        reportInfo.accept("Overall: " + decision + "\n\n" + filterResults);

        if (rejections.isEmpty()) {
            return;
        }

        String rejectingFilters = rejections.stream().map(FilterResult::getFilter).collect(Collectors.joining(", "));
        LOGGER.atDebug().log("Filtering test with ID {} because {} rejected.", this.testRecord.getId(), rejectingFilters);
        this.testRecord.setIsIncluded(false);
        this.testRecord.setExclusionInfo("Excluded by " + this + ".");
        this.testRecord.store();
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
        return "TestFilteringTask{" +
            "stage=" + this.stage +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + testRecordId +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestFilteringTask)) return false;
        TestFilteringTask that = (TestFilteringTask) o;
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
