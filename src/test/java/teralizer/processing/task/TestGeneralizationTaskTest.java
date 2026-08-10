package teralizer.processing.task;

import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import net.jqwik.api.Example;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationLifecycleRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TaskRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.domain.CapturedInput;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.ReferenceValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
import teralizer.generalization.WideningLicense;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.codegen.GeneralizedTestBuilder;
import teralizer.transformer.ModelToJsonTransformer;
import teralizer.transformer.SpecificationGson;

public class TestGeneralizationTaskTest {
    @Example
    void errorDuringTaskExecutionClearsAttachedGeneralization() {
        RecordingGeneralizations store = new RecordingGeneralizations();
        GeneralizationRecord record = store.dsl().newRecord(Tables.GENERALIZATION);
        record.setId(101L);
        record.setIsIncluded(true);
        store.generalization = record;

        Assert.assertThrows(AssertionError.class,
            () -> new ErrorTask(record).execute(new TaskContext(), ignored -> {}, ignored -> {}));

        Assert.assertFalse(record.getIsIncluded());
        Assert.assertTrue(record.getExclusionInfo().contains("AssertionError"));
    }

    @Example
    void projectScopedFailureClearsFannedOutGeneralizations() {
        ProjectFailureStore store = new ProjectFailureStore();
        GeneralizationLifecycleRecord lifecycle = store.records.newRecord(Tables.GENERALIZATION_LIFECYCLE);
        lifecycle.setId(201L);
        lifecycle.setGeneralizationId(301L);
        lifecycle.setGeneratedSourceCreated(true);
        lifecycle.setGeneratedProjectCompiled(true);
        lifecycle.setGeneratedTestsExecuted(false);
        lifecycle.setGeneratedReportCollected(false);
        lifecycle.setGeneratedFilterPassed(false);
        lifecycle.setGeneratedPitCollected(false);
        lifecycle.setFinalUsable(false);
        store.lifecycle = lifecycle;

        GeneralizationRecord generalization = store.records.newRecord(Tables.GENERALIZATION);
        generalization.setId(301L);
        generalization.setIsIncluded(true);
        store.generalization = generalization;

        TaskRecord task = store.records.newRecord(Tables.TASK);
        task.setStage(ProcessingStage.EXECUTE_TESTS_GENERALIZED);
        task.setProjectId(7L);
        task.setVariant("IMPROVED_100_TRIES");

        GeneralizationLifecycleWriter.recordStageFailed(store.dsl(), task, "PROCESS_EXECUTION_FAILED");

        Assert.assertFalse(store.generalization.getIsIncluded());
        Assert.assertTrue(store.generalization.getExclusionInfo().contains(ProcessingStage.EXECUTE_TESTS_GENERALIZED.name()));
        Assert.assertTrue(store.generalization.getExclusionInfo().contains("PROCESS_EXECUTION_FAILED"));
    }

    @Example
    void mapsTestedMethodArgumentsByNameSkippingWrapperExtras() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));
        // The wrapper signature is [_target_?][inputs][lifted locals][scope-bound constructions].
        // Only the generalizable inputs carry tested-parameter names, so mapping resolves by
        // name and never by position.
        List<CapturedInput> values = Arrays.asList(
            new CapturedInput("_target_", new ReferenceValue("org.example.Subject")),
            new CapturedInput("x", new PrimitiveValue("double", 2.0)),
            new CapturedInput("_local_new_Marker_1", new ReferenceValue("java.lang.Object"))
        );

        Map<String, Value> mapped = GeneralizedTestBuilder.mapTestedMethodArguments(parameters, values);

        Assert.assertEquals(1, mapped.size());
        Assert.assertEquals("double", mapped.get("x").getJavaType());
        Assert.assertEquals(Double.valueOf(2.0), ((PrimitiveValue) mapped.get("x")).getValue());
    }

    @Example
    void missingCapturedNameFailsLoud() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("int", "y"));
        List<CapturedInput> values = Arrays.asList(
            new CapturedInput("x", new PrimitiveValue("int", 1))
        );

        Assert.assertThrows(IllegalArgumentException.class,
            () -> GeneralizedTestBuilder.mapTestedMethodArguments(parameters, values));
    }

    @Example
    void recoversTypedTemporaryParametersFromInputAndOutputModels() {
        List<MethodParameter> declared = Arrays.asList(new MethodParameter("int", "x"));
        Model input = new Operation(
            new Variable("INT_1", TypeDomain.INTEGER),
            Operator.GT,
            new Constant(0L, TypeDomain.INTEGER));
        Model output = new Invocation(
            new Variable("STR_2", TypeDomain.STRING),
            null,
            "trim",
            java.util.Collections.emptyList());

        List<MethodParameter> recovered = GeneralizedTestBuilder.collectTemporaryParameters(input, output, declared);

        Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("INT_1") && p.getType().equals("int")));
        Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("STR_2") && p.getType().equals("java.lang.String")));
        Assert.assertFalse(recovered.stream().anyMatch(p -> p.getName().equals("x")));
    }

    @Example
    void licensedSymbolicGeneralizationKeepsRenderedOracle() throws Exception {
        Scenario scenario = scenario(
            "returnsInput",
            "id",
            new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER)),
            new Variable("x", TypeDomain.INTEGER),
            CapturedOutput.ofReturnValue(new PrimitiveValue("int", 2)),
            0
        );

        ExecutedGeneralization result = executeGeneralization(scenario);

        Assert.assertTrue(result.record.getIsIncluded());
        Assert.assertNull(result.record.getExclusionInfo());
        String rendered = new String(Files.readAllBytes(result.generatedPath), StandardCharsets.UTF_8);
        Assert.assertTrue(
            rendered,
            rendered.contains("org.junit.Assert.assertEquals((int) (_p_.x), new Subject().id(_p_.x))")
        );
    }

    @Example
    void unlicensedNullConcreteGeneralizationIsRecordedAsTypedExclusion() throws Exception {
        Scenario scenario = scenario(
            "positiveInput",
            "positive",
            null,
            null,
            CapturedOutput.ofReturnValue(new PrimitiveValue("boolean", true)),
            0
        );

        ExecutedGeneralization result = executeGeneralization(scenario);

        Assert.assertFalse(result.record.getIsIncluded());
        Assert.assertEquals(WideningLicense.ORACLE_NOT_WIDENABLE, result.record.getExclusionInfo());
        Assert.assertEquals(WideningLicense.NULL_CONCRETE_PARAMETERS_EMPTY, result.record.getWideningRefusalCode());
        Assert.assertFalse("refused generalization must not write a doomed artifact", Files.exists(result.generatedPath));
    }

    @Example
    void compositeRecipeLicensesNullConcreteBooleanOracleByExpressionType() throws Exception {
        Scenario scenario = expressionScenario(
            new Operation(new Variable("site0", TypeDomain.INTEGER), Operator.GT, new Variable("site1", TypeDomain.INTEGER)),
            null,
            CapturedOutput.ofReturnValue(new PrimitiveValue("boolean", true)),
            0
        );

        ExecutedGeneralization result = executeGeneralization(scenario);

        Assert.assertTrue(result.record.getIsIncluded());
        Assert.assertNull(result.record.getExclusionInfo());
        Assert.assertTrue(Files.exists(result.generatedPath));
    }

    @Example
    void plainCallRecipeLicensesNullConcreteBooleanOracleByExpressionType() throws Exception {
        Scenario scenario = scenario(
            "returnsInput",
            "id",
            new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER)),
            null,
            CapturedOutput.ofReturnValue(new PrimitiveValue("boolean", true)),
            0,
            "boolean"
        );

        ExecutedGeneralization result = executeGeneralization(scenario);

        Assert.assertTrue(result.record.getIsIncluded());
        Assert.assertNull(result.record.getExclusionInfo());
        Assert.assertTrue(Files.exists(result.generatedPath));
    }

    private static ExecutedGeneralization executeGeneralization(Scenario scenario) throws Exception {
        // The IMPROVED_100_TRIES variant definition comes from src/test/resources/reference.conf
        // (merged into defaultReference()), which is immune to Configuration's static-init order.
        RecordingGeneralizations store = new RecordingGeneralizations();
        TaskContext context = new TaskContext();
        context.put(TaskContext.DSL_CONTEXT, store.dsl());
        context.put(TaskContext.GSON, new Gson());
        context.put(TaskContext.VELOCITY_ENGINE, velocityEngine());
        context.put(scenario.project.getId(), TaskContext.SPOON_LAUNCHER, scenario.launcher);

        new TestGeneralizationTask(
            ProcessingStage.GENERALIZE_TESTS,
            "IMPROVED_100_TRIES",
            scenario.project,
            scenario.test,
            scenario.assertion
        ).execute(context, ignored -> {}, ignored -> {});

        Path generated = scenario.testFile.getParent().resolve(store.generalization.getClassName() + ".java");
        return new ExecutedGeneralization(store.generalization, generated);
    }

    private static Scenario scenario(
        String testMethodName,
        String testedMethodName,
        Model inputModel,
        Model outputModel,
        CapturedOutput output,
        Integer concretizationEvents
    ) throws IOException {
        return scenario(testMethodName, testedMethodName, inputModel, outputModel, output, concretizationEvents, null);
    }

    private static Scenario scenario(
        String testMethodName,
        String testedMethodName,
        Model inputModel,
        Model outputModel,
        CapturedOutput output,
        Integer concretizationEvents,
        String oracleExpressionTypeOverride
    ) throws IOException {
        Path root = Files.createTempDirectory("teralizer-generalization-test");
        Path testSourceRoot = root.resolve("src/test/java");
        Path packageDirectory = testSourceRoot.resolve("example");
        Files.createDirectories(packageDirectory);
        Path testFile = packageDirectory.resolve("SubjectTest.java");
        Files.write(testFile, SOURCE.getBytes(StandardCharsets.UTF_8));

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(SOURCE, testFile.toString()));
        launcher.buildModel();

        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtClass<?> subjectClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "Subject"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName(testMethodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtInvocation<?> testedCall = testMethod.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(call -> call.getExecutable().getSimpleName().equals(testedMethodName))
            .findFirst()
            .get();
        CtMethod<?> testedMethod = subjectClass.getMethodsByName(testedMethodName).get(0);

        Gson gson = new Gson();
        Gson specificationGson = SpecificationGson.create();
        Path data = root.resolve("data");
        Path inputValues = root.resolve("input-values.json");
        Path inputSpecification = root.resolve("input-specification.json");
        Path outputValue = root.resolve("output-value.json");
        Path outputSpecification = root.resolve("output-specification.json");
        Files.write(
            inputValues,
            specificationGson.toJson(Collections.singletonList(new CapturedInput("x", new PrimitiveValue("int", 2))), inputValuesType()).getBytes(StandardCharsets.UTF_8)
        );
        ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();
        Files.write(inputSpecification, modelToJsonTransformer.transform(inputModel).getBytes(StandardCharsets.UTF_8));
        Files.write(outputValue, specificationGson.toJson(output).getBytes(StandardCharsets.UTF_8));
        Files.write(outputSpecification, modelToJsonTransformer.transform(outputModel).getBytes(StandardCharsets.UTF_8));

        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setDataPath(data);
        project.setTestSourcePath(testSourceRoot);

        TestRecord test = new TestRecord();
        test.setId(11L);
        test.setProjectId(project.getId());
        test.setTestFilePath(testFile.toString());
        test.setTestClassQualifiedName("example.SubjectTest");
        test.setTestMethodQualifiedName("example.SubjectTest." + testMethodName);
        test.setTestPackageName("example");
        test.setTestClassName("SubjectTest");
        test.setTestMethodName(testMethodName);
        test.setTestMethodRelativePath(testMethod.getPath().relativePath(testClass).toString());
        test.setIsIncluded(true);

        AssertionRecord assertionRecord = new AssertionRecord();
        assertionRecord.setId(13L);
        assertionRecord.setProjectId(project.getId());
        assertionRecord.setTestId(test.getId());
        assertionRecord.setAssertionName(assertion.getExecutable().getSimpleName());
        assertionRecord.setAssertionRelativePath(assertion.getPath().relativePath(testMethod).toString());
        assertionRecord.setTestedMethodName(testedMethodName);
        assertionRecord.setTestedMethodParameters(gson.toJson(Collections.singletonList(new MethodParameter("int", "x"))));
        assertionRecord.setTestedMethodReturnType(TestAnalysisTask.typeNameOf(testedMethod.getType()));
        assertionRecord.setTestedMethodAbsolutePath(testedMethod.getPath().toString());
        assertionRecord.setTestedMethodRelativePath(testedMethod.getPath().relativePath(subjectClass).toString());
        assertionRecord.setTestedMethodCallRelativePath(testedCall.getPath().relativePath(testMethod).toString());
        String oracleExpressionType = oracleExpressionTypeOverride == null
            ? testedMethod.getType().getQualifiedName()
            : oracleExpressionTypeOverride;
        assertionRecord.setGeneralizationRecipe(
            GeneralizationRecipe.from(
                testedMethod,
                testedCall,
                GeneralizableInput.derive(testedMethod, testedCall),
                oracleExpressionType
            ).toJson(gson)
        );
        assertionRecord.setInputValuesPath(inputValues.toString());
        assertionRecord.setInputSpecificationPath(inputSpecification.toString());
        assertionRecord.setOutputValuePath(outputValue.toString());
        assertionRecord.setOutputSpecificationPath(outputSpecification.toString());
        assertionRecord.setConcretizationEvents(concretizationEvents);
        assertionRecord.setOutputIsLiteral(true);
        assertionRecord.setIsIncluded(true);

        return new Scenario(project, test, assertionRecord, launcher, testFile);
    }

    private static Scenario expressionScenario(
        Model inputModel,
        Model outputModel,
        CapturedOutput output,
        Integer concretizationEvents
    ) throws IOException {
        Path root = Files.createTempDirectory("teralizer-generalization-expression-test");
        Path testSourceRoot = root.resolve("src/test/java");
        Path packageDirectory = testSourceRoot.resolve("example");
        Files.createDirectories(packageDirectory);
        Path testFile = packageDirectory.resolve("SubjectTest.java");
        Files.write(testFile, EXPRESSION_SOURCE.getBytes(StandardCharsets.UTF_8));

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(EXPRESSION_SOURCE, testFile.toString()));
        launcher.buildModel();

        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtClass<?> subjectClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "Subject"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("intComparisonIsPositive").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtExpression<?> oracleExpression = assertion.getArguments().get(0);
        CtInvocation<?> testedCall = oracleExpression.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(call -> call.getExecutable().getSimpleName().equals("compare"))
            .findFirst()
            .get();
        CtMethod<?> testedMethod = subjectClass.getMethodsByName("compare").get(0);
        List<GeneralizableInput> inputs = GeneralizableInput.deriveFromExpression(oracleExpression);

        Gson gson = new Gson();
        Gson specificationGson = SpecificationGson.create();
        Path data = root.resolve("data");
        Path inputValues = root.resolve("input-values.json");
        Path inputSpecification = root.resolve("input-specification.json");
        Path outputValue = root.resolve("output-value.json");
        Path outputSpecification = root.resolve("output-specification.json");
        Files.write(
            inputValues,
            specificationGson.toJson(Arrays.asList(
                new CapturedInput(inputs.get(0).toMethodParameter().getName(), new PrimitiveValue("int", 4)),
                new CapturedInput(inputs.get(1).toMethodParameter().getName(), new PrimitiveValue("int", 1))
            ), inputValuesType()).getBytes(StandardCharsets.UTF_8)
        );
        ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();
        Files.write(inputSpecification, modelToJsonTransformer.transform(inputModel).getBytes(StandardCharsets.UTF_8));
        Files.write(outputValue, specificationGson.toJson(output).getBytes(StandardCharsets.UTF_8));
        Files.write(outputSpecification, modelToJsonTransformer.transform(outputModel).getBytes(StandardCharsets.UTF_8));

        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setDataPath(data);
        project.setTestSourcePath(testSourceRoot);

        TestRecord test = new TestRecord();
        test.setId(11L);
        test.setProjectId(project.getId());
        test.setTestFilePath(testFile.toString());
        test.setTestClassQualifiedName("example.SubjectTest");
        test.setTestMethodQualifiedName("example.SubjectTest.intComparisonIsPositive");
        test.setTestPackageName("example");
        test.setTestClassName("SubjectTest");
        test.setTestMethodName("intComparisonIsPositive");
        test.setTestMethodRelativePath(testMethod.getPath().relativePath(testClass).toString());
        test.setIsIncluded(true);

        AssertionRecord assertionRecord = new AssertionRecord();
        assertionRecord.setId(13L);
        assertionRecord.setProjectId(project.getId());
        assertionRecord.setTestId(test.getId());
        assertionRecord.setAssertionName(assertion.getExecutable().getSimpleName());
        assertionRecord.setAssertionRelativePath(assertion.getPath().relativePath(testMethod).toString());
        assertionRecord.setTestedMethodName("compare");
        assertionRecord.setTestedMethodParameters(gson.toJson(Arrays.asList(inputs.get(0).toMethodParameter(), inputs.get(1).toMethodParameter())));
        assertionRecord.setTestedMethodReturnType(TestAnalysisTask.typeNameOf(testedMethod.getType()));
        assertionRecord.setTestedMethodAbsolutePath(testedMethod.getPath().toString());
        assertionRecord.setTestedMethodRelativePath(testedMethod.getPath().relativePath(subjectClass).toString());
        assertionRecord.setTestedMethodCallRelativePath(testedCall.getPath().relativePath(testMethod).toString());
        assertionRecord.setGeneralizationRecipe(
            GeneralizationRecipe.from(
                testedMethod,
                oracleExpression,
                inputs,
                oracleExpression.getType().getQualifiedName()
            ).toJson(gson)
        );
        assertionRecord.setInputValuesPath(inputValues.toString());
        assertionRecord.setInputSpecificationPath(inputSpecification.toString());
        assertionRecord.setOutputValuePath(outputValue.toString());
        assertionRecord.setOutputSpecificationPath(outputSpecification.toString());
        assertionRecord.setConcretizationEvents(concretizationEvents);
        assertionRecord.setOutputIsLiteral(true);
        assertionRecord.setIsIncluded(true);

        return new Scenario(project, test, assertionRecord, launcher, testFile);
    }

    private static Type inputValuesType() {
        return new com.google.gson.reflect.TypeToken<List<CapturedInput>>() {}.getType();
    }


    private static VelocityEngine velocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");
        VelocityEngine velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
        return velocityEngine;
    }

    private static final String SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "  @org.junit.Test public void positiveInput() {\n"
        + "    org.junit.Assert.assertTrue(new Subject().positive(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "  boolean positive(int x) { return x > 0; }\n"
        + "}\n";

    private static final String EXPRESSION_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void intComparisonIsPositive() {\n"
        + "    org.junit.Assert.assertTrue(new Subject().compare(4, 1) > 0);\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int compare(int left, int right) { return java.lang.Integer.compare(left, right); }\n"
        + "}\n";

    private static final class ProjectFailureStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private GeneralizationLifecycleRecord lifecycle;
        private GeneralizationRecord generalization;

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("select") && sql.contains("\"generalization_lifecycle\"")) {
                Result<GeneralizationLifecycleRecord> result = this.records.newResult(Tables.GENERALIZATION_LIFECYCLE);
                result.add(this.lifecycle);
                return new MockResult[] {new MockResult(1, result)};
            }
            if (sql.startsWith("select") && sql.contains("\"generalization\"")) {
                Result<GeneralizationRecord> result = this.records.newResult(Tables.GENERALIZATION);
                result.add(this.generalization);
                return new MockResult[] {new MockResult(1, result)};
            }
            if (sql.startsWith("update") && sql.contains("\"generalization_lifecycle\"")) {
                return new MockResult[] {new MockResult(1, this.records.newResult(Tables.GENERALIZATION_LIFECYCLE))};
            }
            if (sql.startsWith("update") && sql.contains("\"generalization\"")) {
                bindUpdate(this.generalization, Tables.GENERALIZATION, context.sql(), context.bindings());
            }
            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.GENERALIZATION))};
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void bindUpdate(org.jooq.Record record, Table<?> table, String sql, Object[] bindings) {
            String lower = sql.toLowerCase(Locale.ROOT);
            String assignments = sql.substring(lower.indexOf(" set ") + 5, lower.indexOf(" where "));
            String[] names = assignments.split(",");
            for (int i = 0; i < names.length && i < bindings.length; i++) {
                Field field = table.field(DSL.name(columnName(names[i].substring(0, names[i].indexOf('=')))));
                if (field != null) {
                    record.set(field, bindings[i]);
                }
            }
        }

        private static String columnName(String sqlName) {
            String name = sqlName.replace("\"", "").trim();
            return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        }
    }

    private static final class ErrorTask extends AbstractTask {
        private ErrorTask(GeneralizationRecord record) {
            this.generalizationRecord = record;
        }

        @Override
        protected void executeInternal(TaskContext context, java.util.function.Consumer<String> reportInfo, java.util.function.Consumer<Task> scheduleTask) {
            throw new AssertionError("boom");
        }
    }

    private static final class Scenario {
        private final ProjectRecord project;
        private final TestRecord test;
        private final AssertionRecord assertion;
        private final Launcher launcher;
        private final Path testFile;

        private Scenario(ProjectRecord project, TestRecord test, AssertionRecord assertion, Launcher launcher, Path testFile) {
            this.project = project;
            this.test = test;
            this.assertion = assertion;
            this.launcher = launcher;
            this.testFile = testFile;
        }
    }

    private static final class ExecutedGeneralization {
        private final GeneralizationRecord record;
        private final Path generatedPath;

        private ExecutedGeneralization(GeneralizationRecord record, Path generatedPath) {
            this.record = record;
            this.generatedPath = generatedPath;
        }
    }

    private static final class RecordingGeneralizations implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private GeneralizationRecord generalization;
        private long nextGeneralizationId = 101L;

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("insert") && targetsTable(sql, "generalization")) {
                GeneralizationRecord record = this.records.newRecord(Tables.GENERALIZATION);
                bindInsert(record, Tables.GENERALIZATION, context.sql(), context.bindings());
                record.setId(this.nextGeneralizationId++);
                this.generalization = record;

                Result<GeneralizationRecord> result = this.records.newResult(Tables.GENERALIZATION);
                result.add(record);
                return new MockResult[] {new MockResult(1, result)};
            }
            if (sql.startsWith("update") && targetsTable(sql, "generalization")) {
                bindUpdate(this.generalization, Tables.GENERALIZATION, context.sql(), context.bindings());
                return new MockResult[] {new MockResult(1, this.records.newResult(Tables.GENERALIZATION))};
            }
            if (sql.startsWith("insert") && (targetsTable(sql, "generation_clause") || targetsTable(sql, "generation_parameter"))) {
                return new MockResult[] {new MockResult(1, this.records.newResult(Tables.GENERALIZATION))};
            }
            return new MockResult[] {new MockResult(0, this.records.newResult(Tables.GENERALIZATION))};
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void bindInsert(org.jooq.Record record, Table<?> table, String sql, Object[] bindings) {
            String lower = sql.toLowerCase(Locale.ROOT);
            String columns = sql.substring(sql.indexOf('(') + 1, lower.indexOf(") values"));
            String[] names = columns.split(",");
            for (int i = 0; i < names.length && i < bindings.length; i++) {
                Field field = table.field(DSL.name(columnName(names[i])));
                if (field != null) {
                    record.set(field, bindings[i]);
                }
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static void bindUpdate(org.jooq.Record record, Table<?> table, String sql, Object[] bindings) {
            String lower = sql.toLowerCase(Locale.ROOT);
            String assignments = sql.substring(lower.indexOf(" set ") + 5, lower.indexOf(" where "));
            String[] names = assignments.split(",");
            for (int i = 0; i < names.length && i < bindings.length; i++) {
                Field field = table.field(DSL.name(columnName(names[i].substring(0, names[i].indexOf('=')))));
                if (field != null) {
                    record.set(field, bindings[i]);
                }
            }
        }

        private static boolean targetsTable(String sql, String tableName) {
            return sql.contains("\"" + tableName + "\"");
        }

        private static String columnName(String sqlName) {
            String name = sqlName.replace("\"", "").trim();
            return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        }
    }
}
