package teralizer.processing.task;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.FilterResultRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;
import teralizer.processing.filter.FilterDecision;
import teralizer.processing.filter.FilterReasonCodes;

public class ProjectBuildTaskTest {

    @Example
    void recordQuarantineExclusionStoresAssertionOwnerAndJavacReason() {
        RecordingFilterResults store = new RecordingFilterResults();
        String javacError = "line 17: cannot find symbol\n  symbol: variable MissingType";

        task().recordQuarantineExclusion(store.dsl(), 11L, null,
            FilterReasonCodes.UNCOMPILABLE_INSTRUMENTED_WRAPPER, javacError);

        FilterResultRecord record = store.onlyRecord();
        assertCommonQuarantineResult(record, FilterReasonCodes.UNCOMPILABLE_INSTRUMENTED_WRAPPER, javacError);
        Assert.assertEquals(Long.valueOf(11L), record.getAssertionId());
        Assert.assertNull(record.getGeneralizationId());
        Assert.assertNull(record.getTestId());
    }

    @Example
    void recordQuarantineExclusionStoresGeneralizationOwnerAndJavacReason() {
        RecordingFilterResults store = new RecordingFilterResults();
        String javacError = "line 23: cannot find symbol\n  symbol: method missing()";

        task().recordQuarantineExclusion(store.dsl(), null, 19L,
            FilterReasonCodes.UNCOMPILABLE_GENERALIZED_TEST, javacError);

        FilterResultRecord record = store.onlyRecord();
        assertCommonQuarantineResult(record, FilterReasonCodes.UNCOMPILABLE_GENERALIZED_TEST, javacError);
        Assert.assertNull(record.getAssertionId());
        Assert.assertEquals(Long.valueOf(19L), record.getGeneralizationId());
        Assert.assertNull(record.getTestId());
    }

    private static void assertCommonQuarantineResult(FilterResultRecord record, String reasonCode, String reason) {
        Assert.assertEquals(Long.valueOf(7L), record.getProjectId());
        Assert.assertEquals("GeneratedTestValidator", record.getFilterName());
        Assert.assertEquals(FilterDecision.REJECT, record.getDecision());
        Assert.assertEquals(reasonCode, record.getReasonCode());
        Assert.assertEquals(reason, record.getReason());
    }

    private static ProjectBuildTask task() {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setDataPath(Paths.get("data/test-project"));
        return new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_INSTRUMENTED, project);
    }

    private static final class RecordingFilterResults implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final List<FilterResultRecord> filterResults = new ArrayList<>();

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        private FilterResultRecord onlyRecord() {
            Assert.assertEquals(1, this.filterResults.size());
            return this.filterResults.get(0);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert") && sql.contains("filter_result")) {
                FilterResultRecord record = this.records.newRecord(Tables.FILTER_RESULT);
                bindRecord(record, Tables.FILTER_RESULT, context.sql(), context.bindings());
                this.filterResults.add(record);
                Result<FilterResultRecord> result = this.records.newResult(Tables.FILTER_RESULT);
                result.add(record);
                return new MockResult[] {new MockResult(1, result)};
            }

            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.FILTER_RESULT))};
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void bindRecord(org.jooq.Record record, Table<?> table, String sql, Object[] bindings) {
            String columns = sql.substring(sql.indexOf('(') + 1, sql.toLowerCase(Locale.ROOT).indexOf(") values"));
            String[] names = columns.split(",");
            for (int i = 0; i < names.length && i < bindings.length; i++) {
                String name = names[i].replace("\"", "").trim();
                if (name.contains(".")) {
                    name = name.substring(name.lastIndexOf('.') + 1);
                }
                Field field = table.field(DSL.name(name));
                if (field != null) {
                    Object value = bindings[i];
                    if (field.equals(Tables.FILTER_RESULT.DECISION) && value instanceof String) {
                        value = FilterDecision.valueOf((String) value);
                    }
                    record.set(field, value);
                }
            }
        }
    }
}
