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
import org.jooq.generated.tables.records.AssertionSemanticsRecord;
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
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.processing.ProcessingStage;
import teralizer.spoon.analysis.AssertionSemanticCodes;
import teralizer.spoon.analysis.FocalTypeResolver;
import teralizer.spoon.analysis.GeneralizableInput;
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
    void resolvesInheritedTestMethodRelativePathAgainstDeclaringClass() {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(
            "package smoke;\n"
                + "public class AbstractBase {\n"
                + "  public void inherited() { org.junit.Assert.assertEquals(1, 1); }\n"
                + "}\n",
            Paths.get(System.getProperty("user.dir"), "AbstractBase.java").toString()
        ));
        launcher.addInputResource(new VirtualFile(
            "package smoke;\n"
                + "public class SubjectTest extends AbstractBase {\n"
                + "}\n",
            Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()
        ));
        launcher.buildModel();
        CtClass<?> parent = launcher.getModel().getElements(new NamedElementFilter<>(CtClass.class, "AbstractBase")).get(0);
        CtMethod<?> inherited = parent.getMethodsByName("inherited").get(0);
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName("smoke.SubjectTest");
        record.setTestMethodQualifiedName("smoke.AbstractBase.inherited");
        record.setTestMethodRelativePath(inherited.getPath().relativePath(parent).toString());

        CtMethod<?> resolved = TestAnalysisTask.resolveTestMethod(launcher.getFactory(), record);

        Assert.assertEquals(inherited, resolved);
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

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

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

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

        AssertionRecord record = store.assertions.get(0);
        Assert.assertNotNull(record.getGeneralizationRecipe());
        GeneralizationRecipe.Resolved resolved = GeneralizationRecipe.fromJson(new Gson(), record.getGeneralizationRecipe())
            .resolveAgainst(testMethod, testMethod.getFactory().getModel().getRootPackage());
        Assert.assertEquals("id", ((CtInvocation<?>) resolved.getOracleExpression()).getExecutable().getSimpleName());
        Assert.assertEquals(1, resolved.getInputs().size());
        Assert.assertEquals("x", resolved.getInputs().get(0).toMethodParameter().getName());
    }

    @Example
    void assertTrueCompositeConditionStoresExpressionRecipe() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertTrue;\n"
                + "public class SubjectTest {\n"
                + "  static int intCompare(int site0, int site1) { return java.lang.Integer.compare(site0, site1); }\n"
                + "  public void t() {\n"
                + "    assertTrue(intCompare(4, 1) > 0);\n"
                + "  }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

        AssertionRecord record = store.assertions.get(0);
        GeneralizationRecipe recipe = GeneralizationRecipe.fromJson(new Gson(), record.getGeneralizationRecipe());
        Assert.assertEquals("boolean", recipe.getOracleExpressionType());
        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(testMethod, testMethod.getFactory().getModel().getRootPackage());
        Assert.assertTrue(resolved.getOracleExpression().toString().contains("intCompare(4, 1) > 0"));
        Assert.assertEquals(2, resolved.getInputs().size());
        assertExpressionInput(resolved.getInputs().get(0), "site0", "int", "4");
        assertExpressionInput(resolved.getInputs().get(1), "site1", "int", "1");
    }

    @Example
    void assertEqualsDirectCallStoresPathBasedMethodArgumentSitesAndSemanticType() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  static int add(int left, int right) { return left + right; }\n"
                + "  public void t() {\n"
                + "    org.junit.Assert.assertEquals(7, add(3, 4));\n"
                + "  }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

        AssertionRecord record = store.assertions.get(0);
        GeneralizationRecipe recipe = GeneralizationRecipe.fromJson(new Gson(), record.getGeneralizationRecipe());
        Assert.assertEquals("int", recipe.getOracleExpressionType());
        GeneralizationRecipe.Resolved resolved = recipe.resolveAgainst(testMethod, testMethod.getFactory().getModel().getRootPackage());
        Assert.assertTrue(resolved.getOracleExpression() instanceof CtInvocation<?>);
        Assert.assertEquals("add", ((CtInvocation<?>) resolved.getOracleExpression()).getExecutable().getSimpleName());
        Assert.assertEquals(2, resolved.getInputs().size());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, resolved.getInputs().get(0).getKind());
        Assert.assertTrue(resolved.getInputs().get(0).getSourceExpression().getPath().toString().contains("argument[index=0]"));
        Assert.assertEquals("left", resolved.getInputs().get(0).toMethodParameter().getName());
        Assert.assertEquals("3", resolved.getInputs().get(0).toMethodArgument().getValue());
        Assert.assertEquals(GeneralizationRecipe.InputKind.METHOD_ARG, resolved.getInputs().get(1).getKind());
        Assert.assertTrue(resolved.getInputs().get(1).getSourceExpression().getPath().toString().contains("argument[index=1]"));
        Assert.assertEquals("right", resolved.getInputs().get(1).toMethodParameter().getName());
        Assert.assertEquals("4", resolved.getInputs().get(1).toMethodArgument().getValue());
    }

    @Example
    void storesSemanticRecordForEachAssertion() {
        CtMethod<?> testMethod = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.assertNotNull;\n"
                + "public class SubjectTest {\n"
                + "  static Object value() { return new Object(); }\n"
                + "  public void t() { assertNotNull(value()); }\n"
                + "}\n"
        );
        RecordingStore store = new RecordingStore();

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

        Assert.assertEquals(1, store.semantics.size());
        Assert.assertEquals(store.assertions.get(0).getId(), store.semantics.get(0).getAssertionId());
        Assert.assertEquals(AssertionSemanticCodes.NULLNESS_NOT_NULL, store.semantics.get(0).getSemanticKind());
        Assert.assertEquals(AssertionSemanticCodes.ARGUMENT_SHAPE_METHOD_CALL, store.semantics.get(0).getArgumentShape());
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

        task().createAssertionRecords(testMethod, store.dsl(), new Gson(), new FocalTypeResolver());

        Assert.assertEquals(2, store.assertionInsertAttempts);
        Assert.assertEquals(1, store.assertions.size());
        Assert.assertEquals(1, store.observationCount);
        Assert.assertTrue(store.assertions.get(0).getAssertionSourceCode().contains("id(2)"));
    }

    private static void assertExpressionInput(GeneralizableInput input, String name, String type, String value) {
        Assert.assertTrue(input.isExpressionSite());
        Assert.assertEquals(name, input.toMethodParameter().getName());
        Assert.assertEquals(type, input.toMethodParameter().getType());
        Assert.assertEquals(value, input.toMethodArgument().getValue());
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
        private final List<AssertionSemanticsRecord> semantics = new ArrayList<>();
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

            if (sql.startsWith("insert") && sql.contains("assertion_semantics")) {
                AssertionSemanticsRecord record = this.records.newRecord(Tables.ASSERTION_SEMANTICS);
                bindRecord(record, Tables.ASSERTION_SEMANTICS, context.sql(), context.bindings());
                this.semantics.add(record);
                Result<AssertionSemanticsRecord> result = this.records.newResult(Tables.ASSERTION_SEMANTICS);
                result.add(record);
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
