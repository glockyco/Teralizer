package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.filter.*;
import teralizer.processing.filter.AssertionCountFilter;
import teralizer.processing.filter.MissingValueFilter;
import teralizer.processing.filter.ParameterTypeFilter;
import teralizer.processing.filter.Filter;
import teralizer.repository.SQLiteRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestFilteringTask extends AbstractTask {

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, null, projectRecord, testRecord, null);
    }

    public TestFilteringTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null);
    }

    public TestFilteringTask(
        ProcessingStage stage,
        GeneralizationVariant variant,
        ProjectRecord projectRecord,
        TestRecord testRecord,
        GeneralizationRecord generalizationRecord
    ) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
            return;
        }

        switch (this.stage) {
            case FILTER_TESTS:
                this.filterTest(context);
                break;
            case FILTER_GENERALIZATIONS:
                this.filterGeneralization(context);
                break;
            default:
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        if (this.stage == ProcessingStage.FILTER_TESTS) {
            Result<TestRecord> testRecords = SQLiteRepository.fetchIncludedTests(create, this.getProjectId());
            for (TestRecord testRecord : testRecords) {
                scheduleTask.accept(new TestFilteringTask(this.stage, this.projectRecord, testRecord));
            }
        } else if (this.stage == ProcessingStage.FILTER_GENERALIZATIONS) {
            Result<Record> records = SQLiteRepository.fetchIncludedGeneralizations(create, this.variant, this.getProjectId());
            for (Record record : records) {
                TestRecord testRecord = record.into(TestRecord.class);
                GeneralizationRecord generalizationRecord = record.into(GeneralizationRecord.class);
                scheduleTask.accept(new TestFilteringTask(this.stage, this.variant, this.projectRecord, testRecord, generalizationRecord));
            }
        } else {
            throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
    }

    private void filterTest(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        List<Filter> filters = Arrays.asList(
            new AssertionCountFilter(create, this.testRecord),
            new MissingValueFilter(this.testRecord),
            new ParameterTypeFilter(gson, this.testRecord),
            new NonPassingTestFilter(create, this.testRecord)
        );

        this.checkFilters(filters);
    }

    private void filterGeneralization(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        List<Filter> filters = Collections.singletonList(
            new NonPassingTestFilter(create, this.testRecord, this.generalizationRecord)
        );

        this.checkFilters(filters);
    }

    private void checkFilters(List<Filter> filters) throws Exception {
        List<FilterResult> decisions = new ArrayList<>();
        for (Filter filter : filters) {
            decisions.add(filter.check());
        }

        List<FilterResult> rejections = decisions.stream().filter(d -> d.getDecision() == FilterDecision.REJECT).collect(Collectors.toList());
        FilterDecision decision = rejections.isEmpty() ? FilterDecision.ACCEPT : FilterDecision.REJECT;
        String filterResults = decisions.stream().map(FilterResult::toString).collect(Collectors.joining("\n"));
        String info = "Overall: " + decision + "\n\n" + filterResults;

        if (!rejections.isEmpty()) {
            if (this.testRecord != null) {
                this.testRecord.setIsIncluded(false);
                this.testRecord.setExclusionInfo("Excluded by " + this + ".\n\n" + info);
                this.testRecord.store();
            } else if (this.generalizationRecord != null) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo("Excluded by " + this + ".\n\n" + info);
                this.generalizationRecord.store();
            }
        }
    }
}
