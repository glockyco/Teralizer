package teralizer.processing.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.TaskDiagnosticRecord;
import org.jooq.generated.tables.records.TaskRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;

public class TaskDiagnosticWriterTest {

    @Example
    void recordsGeneralizedExecutionDiagnostics() {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        TaskRecord task = task(ProcessingStage.EXECUTE_TESTS_GENERALIZED);

        String code = TaskDiagnosticWriter.recordFailure(
            diagnostics.dsl(),
            task,
            new RuntimeException("Command execution timeout exceeded.")
        );

        Assert.assertEquals(TaskDiagnosticCodes.SUITE_TIMEOUT, code);
        Assert.assertTrue(diagnostics.reasonCodes.contains(TaskDiagnosticCodes.SUITE_TIMEOUT));
    }

    private static TaskRecord task(ProcessingStage stage) {
        TaskRecord task = new TaskRecord();
        task.setId(11L);
        task.setProjectId(7L);
        task.setStage(stage);
        return task;
    }

    private static final class RecordingDiagnostics implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final List<String> reasonCodes = new ArrayList<>();

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert") && sql.contains("task_diagnostic")) {
                for (Object binding : context.bindings()) {
                    if (TaskDiagnosticCodes.SUITE_TIMEOUT.equals(binding)) {
                        this.reasonCodes.add((String) binding);
                    }
                }
                Result<TaskDiagnosticRecord> result = this.records.newResult(Tables.TASK_DIAGNOSTIC);
                return new MockResult[] {new MockResult(1, result)};
            }
            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.TASK_DIAGNOSTIC))};
        }
    }
}
