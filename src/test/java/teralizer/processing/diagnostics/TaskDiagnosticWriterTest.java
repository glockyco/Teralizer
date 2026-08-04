package teralizer.processing.diagnostics;

import java.util.ArrayList;
import java.util.Arrays;
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

    @Example
    void recordsInitialPitCollectionTimeoutDiagnostics() {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        TaskRecord task = task(ProcessingStage.COLLECT_PIT_DATA_INITIAL);

        String code = TaskDiagnosticWriter.recordFailure(
            diagnostics.dsl(),
            task,
            new RuntimeException("Command execution timeout exceeded.")
        );

        Assert.assertEquals(TaskDiagnosticCodes.EXECUTION_TIMEOUT, code);
        Assert.assertTrue(diagnostics.reasonCodes.contains(TaskDiagnosticCodes.EXECUTION_TIMEOUT));
    }

    @Example
    void recordsGeneralizedPitAndJacocoCollectionTimeoutDiagnostics() {
        assertRecordsExecutionTimeout(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED);
        assertRecordsExecutionTimeout(ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED);
    }

    @Example
    void skipsNonDiagnosticStages() {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        TaskRecord task = task(ProcessingStage.SETUP_PROJECT);

        String code = TaskDiagnosticWriter.recordFailure(
            diagnostics.dsl(),
            task,
            new RuntimeException("Any failure")
        );

        Assert.assertNull(code);
        Assert.assertTrue(diagnostics.reasonCodes.isEmpty());
    }

    private static void assertRecordsExecutionTimeout(ProcessingStage stage) {
        RecordingDiagnostics diagnostics = new RecordingDiagnostics();
        TaskRecord task = task(stage);

        String code = TaskDiagnosticWriter.recordFailure(
            diagnostics.dsl(),
            task,
            new RuntimeException("Command execution timeout exceeded.")
        );

        Assert.assertEquals(stage.name(), TaskDiagnosticCodes.EXECUTION_TIMEOUT, code);
        Assert.assertTrue(stage.name(), diagnostics.reasonCodes.contains(TaskDiagnosticCodes.EXECUTION_TIMEOUT));
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
        private static final List<String> DIAGNOSTIC_CODES = Arrays.asList(
            TaskDiagnosticCodes.EXECUTION_TIMEOUT,
            TaskDiagnosticCodes.FOUND_REPORT_NO_MATCHING_TESTCASE,
            TaskDiagnosticCodes.GENERATED_SOURCE_LEVEL_TOO_NEW,
            TaskDiagnosticCodes.JPF_DIVERGENT_ASSERTION,
            TaskDiagnosticCodes.LISTENER_BUG,
            TaskDiagnosticCodes.MISSING_JPF_MODEL_CLASS,
            TaskDiagnosticCodes.MISSING_JPF_MODEL_METHOD,
            TaskDiagnosticCodes.MISSING_DEPENDENCY,
            TaskDiagnosticCodes.MISSING_REPORT_FILE,
            TaskDiagnosticCodes.MISSING_NATIVE_PEER,
            TaskDiagnosticCodes.MINION_DIED,
            TaskDiagnosticCodes.NO_TESTS_FOUND,
            TaskDiagnosticCodes.PLUGIN_UNUSABLE,
            TaskDiagnosticCodes.REPORT_ABSENT,
            TaskDiagnosticCodes.SUITE_NOT_GREEN,
            TaskDiagnosticCodes.NO_INPUT_SPEC,
            TaskDiagnosticCodes.NO_OUTPUT_SPEC,
            TaskDiagnosticCodes.OTHER_COMPILE_FAILURE,
            TaskDiagnosticCodes.PC_SIZE_LIMIT,
            TaskDiagnosticCodes.SEARCH_DEPTH_LIMIT,
            TaskDiagnosticCodes.SUITE_TIMEOUT,
            TaskDiagnosticCodes.UNCAUGHT_EXCEPTION_PATH,
            TaskDiagnosticCodes.TEST_COMPILE_OUTPUT_MISSING,
            TaskDiagnosticCodes.UNSUPPORTED_BYTECODE,
            TaskDiagnosticCodes.UNSUPPORTED_REPORT_LAYOUT
        );
        private final List<String> reasonCodes = new ArrayList<>();

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert") && sql.contains("task_diagnostic")) {
                for (Object binding : context.bindings()) {
                    if (binding instanceof String && DIAGNOSTIC_CODES.contains(binding)) {
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
