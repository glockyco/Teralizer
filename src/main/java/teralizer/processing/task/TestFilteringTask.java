package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.filter.*;
import teralizer.repository.SQLiteRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestFilteringTask extends AbstractTask {

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null, null);
    }

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this(stage, null, projectRecord, testRecord, null, null);
    }

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this(stage, null, projectRecord, testRecord, assertionRecord, null);
    }

    public TestFilteringTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null, null);
    }

    public TestFilteringTask(
        ProcessingStage stage,
        String variant,
        ProjectRecord projectRecord,
        TestRecord testRecord,
        AssertionRecord assertionRecord,
        GeneralizationRecord generalizationRecord
    ) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
        this.generalizationRecord = generalizationRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        // If this IS a project-level task, we should schedule the corresponding
        // test-/assertion-/generalization-level tasks.
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
            return;
        }

        // If this is NOT a project-level task, we should perform the filtering.
        switch (this.stage) {
            case FILTER_TESTS_ORIGINAL:
                this.filterTestOriginal(context);
                break;
            case FILTER_TESTS:
                this.filterTest(context);
                break;
            case FILTER_ASSERTIONS:
                this.filterAssertion(context);
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
        switch (this.stage) {
            case FILTER_TESTS_ORIGINAL:
            case FILTER_TESTS: {
                Result<Record> records = SQLiteRepository.fetchIncludedTests(create, this.getProjectId());
                for (Record record : records) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    scheduleTask.accept(new TestFilteringTask(this.stage, this.projectRecord, testRecord));
                }
                break;
            }
            case FILTER_ASSERTIONS: {
                Result<Record> records = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId());
                for (Record record : records) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    AssertionRecord assertionRecord = record.into(AssertionRecord.class);
                    scheduleTask.accept(new TestFilteringTask(this.stage, this.projectRecord, testRecord, assertionRecord));
                }
                break;
            }
            case FILTER_GENERALIZATIONS: {
                Result<Record> records = SQLiteRepository.fetchIncludedGeneralizations(create, this.variant, this.getProjectId());
                for (Record record : records) {
                    TestRecord testRecord = record.into(TestRecord.class);
                    AssertionRecord assertionRecord = record.into(AssertionRecord.class);
                    GeneralizationRecord generalizationRecord = record.into(GeneralizationRecord.class);
                    scheduleTask.accept(new TestFilteringTask(this.stage, this.variant, this.projectRecord, testRecord, assertionRecord, generalizationRecord));
                }
                break;
            }
            default:
                throw new RuntimeException("Unsupported processing stage " + this.stage + ".");
        }
    }

    private void filterTestOriginal(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<Filter> filters = Collections.singletonList(new NonPassingTestFilter(create, this.testRecord));
        this.checkFilters(filters);
    }

    private void filterTest(TaskContext context) throws Exception {
        Launcher spoonLauncher = context.get(this.testRecord.getProjectId(), TaskContext.SPOON_LAUNCHER);

        List<Filter> filters = Arrays.asList(
            new UnnamedPackageFilter(this.testRecord),
            new AssertionInMethodFilter(spoonLauncher, this.testRecord)
        );

        this.checkFilters(filters);
    }

    private void filterAssertion(TaskContext context) throws Exception {
        Gson gson = context.get(TaskContext.GSON);
        Launcher spoonLauncher = context.get(this.assertionRecord.getProjectId(), TaskContext.SPOON_LAUNCHER);

        List<Filter> filters = Arrays.asList(
            new MissingValueFilter(this.assertionRecord),
            new UnsupportedAssertionFilter(this.assertionRecord),
            new ParameterTypeFilter(gson, this.assertionRecord),
            new AssertionInLoopFilter(spoonLauncher, this.assertionRecord),
            new TestedMethodInLoopFilter(spoonLauncher, this.assertionRecord)
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
            if (this.generalizationRecord != null) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo("Excluded by " + this + ".\n\n" + info);
                this.generalizationRecord.store();
            } else if (this.assertionRecord != null) {
                this.assertionRecord.setIsIncluded(false);
                this.assertionRecord.setExclusionInfo("Excluded by " + this + ".\n\n" + info);
                this.assertionRecord.store();
            } else if (this.testRecord != null) {
                this.testRecord.setIsIncluded(false);
                this.testRecord.setExclusionInfo("Excluded by " + this + ".\n\n" + info);
                this.testRecord.store();
            }
        }
    }
}
