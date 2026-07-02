package teralizer.processing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.processing.task.Task;

public class ProcessingPipelineTest {

    @Example
    void recordsFailedTaskStatusBeforeRethrowingUnrecoverableError() {
        RecordingTaskStore taskStore = new RecordingTaskStore();
        ProcessingPipeline pipeline = new ProcessingPipeline(taskStore.dsl());
        AssertionError failure = new AssertionError("fatal task failure");

        pipeline.addTask(new ThrowingTask(ProcessingStage.EXECUTE_JPF, failure));

        AssertionError thrown = Assert.assertThrows(AssertionError.class, pipeline::executeNext);

        Assert.assertSame(failure, thrown);
        Assert.assertTrue(
            "updated statuses: " + taskStore.updatedStatuses + "; updated info: " + taskStore.updatedInfo,
            taskStore.updatedStatuses.contains(ProcessingStatus.FAILED)
        );
        Assert.assertTrue(taskStore.updatedInfo.stream().anyMatch(info -> info.contains("fatal task failure")));
    }

    private static final class ThrowingTask implements Task {
        private final ProcessingStage stage;
        private final Error failure;

        private ThrowingTask(ProcessingStage stage, Error failure) {
            this.stage = stage;
            this.failure = failure;
        }

        @Override
        public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) {
            throw this.failure;
        }

        @Override
        public ProcessingStage getStage() {
            return this.stage;
        }

        @Override
        public String getVariant() {
            return null;
        }

        @Override
        public Long getProjectId() {
            return null;
        }

        @Override
        public Long getTestId() {
            return null;
        }

        @Override
        public Long getAssertionId() {
            return null;
        }

        @Override
        public Long getGeneralizationId() {
            return null;
        }
    }

    private static final class RecordingTaskStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final List<ProcessingStatus> updatedStatuses = new ArrayList<>();
        private final List<String> updatedInfo = new ArrayList<>();

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);

            if (sql.startsWith("insert") && sql.contains("task")) {
                this.recordBindings(context.bindings());
                Result<TaskRecord> result = this.records.newResult(Tables.TASK);
                TaskRecord inserted = this.records.newRecord(Tables.TASK);
                inserted.setId(1L);
                result.add(inserted);
                return new MockResult[] {new MockResult(1, result)};
            }

            if (sql.startsWith("select")) {
                org.jooq.Field<Boolean> exists = DSL.field("exists", Boolean.class);
                Result<Record1<Boolean>> result = this.records.newResult(exists);
                Record1<Boolean> record = this.records.newRecord(exists);
                record.value1(true);
                result.add(record);
                return new MockResult[] {new MockResult(1, result)};
            }

            if (sql.startsWith("update") && sql.contains("task")) {
                this.recordBindings(context.bindings());
                return new MockResult[] {new MockResult(1, this.records.newResult(Tables.TASK))};
            }

            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.TASK))};
        }

        private void recordBindings(Object[] bindings) {
            for (Object binding : bindings) {
                if (binding instanceof ProcessingStatus) {
                    this.updatedStatuses.add((ProcessingStatus) binding);
                } else if (binding instanceof String) {
                    try {
                        this.updatedStatuses.add(ProcessingStatus.valueOf((String) binding));
                    } catch (IllegalArgumentException ignored) {
                        this.updatedInfo.add((String) binding);
                    }
                }
            }
        }
    }
}
