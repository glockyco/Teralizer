package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.google.gson.Gson;
import org.jooq.DSLContext;
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

    public TestFilteringTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);
        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);

        AssertionCountFilter assertionCountFilter = new AssertionCountFilter(create);
        InheritanceFilter inheritanceFilter = new InheritanceFilter(javaParser);
        MissingValueFilter missingValueFilter = new MissingValueFilter();
        ParameterTypeFilter parameterTypeFilter = new ParameterTypeFilter(gson);

        List<Filter> filters = Arrays.asList(assertionCountFilter, inheritanceFilter, missingValueFilter, parameterTypeFilter);

        List<FilterResult> decisions = new ArrayList<>();
        for (Filter filter : filters) {
            decisions.add(filter.check(this.testRecord));
        }

        List<FilterResult> rejections = decisions.stream().filter(d -> d.getDecision() == FilterDecision.REJECT).collect(Collectors.toList());
        FilterDecision decision = rejections.isEmpty() ? FilterDecision.ACCEPT : FilterDecision.REJECT;
        String filterResults = decisions.stream().map(FilterResult::toString).collect(Collectors.joining("\n"));
        reportInfo.accept("Overall: " + decision + "\n\n" + filterResults);

        if (!rejections.isEmpty()) {
            String rejectingFilters = rejections.stream().map(FilterResult::getFilter).collect(Collectors.joining(", "));
            LOGGER.atDebug().log("Filtering test with ID {} because {} rejected.", this.testRecord.getId(), rejectingFilters);
            return;
        }

        scheduleTask.accept(new JpfInstrumentationTask(ProcessingStage.JPF_INSTRUMENTATION, this.projectRecord, this.testRecord));
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
        return "TestFilteringTask{" +
            "stage=" + this.stage +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + this.testRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestFilteringTask)) return false;
        TestFilteringTask that = (TestFilteringTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.testRecord.getId(), that.testRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId(), this.testRecord.getId());
    }
}
