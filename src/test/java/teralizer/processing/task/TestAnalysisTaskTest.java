package teralizer.processing.task;

import com.google.gson.Gson;
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
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.processing.ProcessingStage;
import teralizer.spoon.analysis.GeneralizationRecipe;

public class TestAnalysisTaskTest {
    @Example
    void typeNameOfReturnsSentinelForUnresolvedType() {
        Assert.assertEquals("<unresolved>", TestAnalysisTask.typeNameOf(null));
    }

    @Example
    void typeNameOfReturnsQualifiedNameForResolvedType() {
        CtTypeReference<?> type = new Launcher().getFactory().Type().integerPrimitiveType();

        Assert.assertEquals("int", TestAnalysisTask.typeNameOf(type));
    }

    @Example
    void unpathableMethodDeclarationStoresInformationalPickOnly() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(1, new Object() { int f() { return 1; } }.f());\n"
                + "  }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();

        task().createAssertionRecords(testMethod, store.dsl(), new Gson());

        Assert.assertEquals(1, store.assertions.size());
        Assert.assertEquals(1, store.observationCount);
        AssertionRecord record = store.assertions.get(0);
        Assert.assertEquals("f", record.getTestedMethodName());
        Assert.assertNotNull(record.getTestedMethodCallArguments());
        Assert.assertNotNull(record.getTestedMethodCallSourceCode());
        Assert.assertNull(record.getTestedFilePath());
        Assert.assertNull(record.getTestedClassQualifiedName());
        Assert.assertNull(record.getTestedMethodQualifiedName());
        Assert.assertNull(record.getTestedMethodParameters());
        Assert.assertNull(record.getTestedMethodReturnType());
        Assert.assertNull(record.getTestedMethodAbsolutePath());
        Assert.assertNull(record.getTestedMethodRelativePath());
    }

    @Example
    void storesValidatedGeneralizationRecipeForGeneralizationGradePick() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  static final class Subject { int id(int x) { return x; } }\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(1, new Subject().id(1));\n"
                + "  }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();

        task().createAssertionRecords(testMethod, store.dsl(), new Gson());

        AssertionRecord record = store.assertions.get(0);
        Assert.assertNotNull(record.getGeneralizationRecipe());
        GeneralizationRecipe.Resolved resolved = GeneralizationRecipe.fromJson(new Gson(), record.getGeneralizationRecipe())
            .resolveAgainst(testMethod, testMethod.getFactory().getModel().getRootPackage());
        Assert.assertEquals("id", resolved.getOracleExpression().getExecutable().getSimpleName());
        Assert.assertEquals(1, resolved.getInputs().size());
        Assert.assertEquals("x", resolved.getInputs().get(0).toMethodParameter().getName());
    }

    @Example
    void assertionRuntimeFailureDoesNotAbortLaterAssertions() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  static final class Subject { int id(int x) { return x; } }\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(1, new Subject().id(1));\n"
                + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
                + "  }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();
        store.throwOnFirstAssertionInsert = true;

        task().createAssertionRecords(testMethod, store.dsl(), new Gson());

        Assert.assertEquals(2, store.assertionInsertAttempts);
        Assert.assertEquals(1, store.assertions.size());
        Assert.assertEquals(1, store.observationCount);
        Assert.assertTrue(store.assertions.get(0).getAssertionSourceCode().contains("id(2)"));
    }

    private static TestAnalysisTask task() {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        TestRecord test = new TestRecord();
        test.setId(11L);
        return new TestAnalysisTask(ProcessingStage.ANALYZE_TESTS, project, test);
    }

    private static CtMethod<?> testMethodFromSource(String source) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        return testClass.getMethodsByName("t").get(0);
    }

    private static final class RecordingStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final List<AssertionRecord> assertions = new ArrayList<>();
        private int assertionInsertAttempts;
        private int observationCount;
        private long nextAssertionId = 1L;
        private boolean throwOnFirstAssertionInsert;

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert") && (sql.contains("\"assertion\"") || sql.contains("insert into assertion"))) {
                this.assertionInsertAttempts++;
                if (this.throwOnFirstAssertionInsert && this.assertionInsertAttempts == 1) {
                    throw new RuntimeException("synthetic assertion failure");
                }

                AssertionRecord record = this.records.newRecord(Tables.ASSERTION);
                bindRecord(record, Tables.ASSERTION, context.sql(), context.bindings());
                record.setId(this.nextAssertionId++);
                this.assertions.add(record);

                Result<AssertionRecord> result = this.records.newResult(Tables.ASSERTION);
                result.add(record);
                return new MockResult[] {new MockResult(1, result)};
            }

            if (sql.startsWith("insert") && sql.contains("mut_resolution_observation")) {
                this.observationCount++;
                Result<MutResolutionObservationRecord> result = this.records.newResult(Tables.MUT_RESOLUTION_OBSERVATION);
                return new MockResult[] {new MockResult(1, result)};
            }

            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.ASSERTION))};
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
                    record.set(field, bindings[i]);
                }
            }
        }
    }
}
