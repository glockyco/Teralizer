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
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
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
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.generalization.WideningLicense;
import teralizer.transformer.ModelToJsonTransformer;
import teralizer.transformer.SpecificationGson;

public class TestGeneralizationTaskTest {
    @Example
    void skipsConcreteReceiverWhenMappingInstanceMethodArguments() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));
        // SPF stores the instance receiver as the first input value. It is an opaque, unrenderable
        // ReferenceValue that must be offset-skipped so the declared parameter maps to the real
        // argument, never to the receiver.
        List<Value> values = Arrays.asList(
            new ReferenceValue("org.example.Subject"),
            new PrimitiveValue("double", 2.0)
        );

        Map<String, Value> mapped = TestGeneralizationTask.mapTestedMethodArguments(parameters, values);

        Assert.assertEquals(1, mapped.size());
        Assert.assertEquals("double", mapped.get("x").getJavaType());
        Assert.assertEquals(Double.valueOf(2.0), ((PrimitiveValue) mapped.get("x")).getValue());
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

        List<MethodParameter> recovered = TestGeneralizationTask.collectTemporaryParameters(input, output, declared);

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
        Assert.assertFalse("refused generalization must not write a doomed artifact", Files.exists(result.generatedPath));
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
            specificationGson.toJson(Collections.singletonList(new PrimitiveValue("int", 2)), inputValuesType()).getBytes(StandardCharsets.UTF_8)
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
        assertionRecord.setInputValuesPath(inputValues.toString());
        assertionRecord.setInputSpecificationPath(inputSpecification.toString());
        assertionRecord.setOutputValuePath(outputValue.toString());
        assertionRecord.setOutputSpecificationPath(outputSpecification.toString());
        assertionRecord.setConcretizationEvents(concretizationEvents);
        assertionRecord.setIsIncluded(true);

        return new Scenario(project, test, assertionRecord, launcher, testFile);
    }

    private static Type inputValuesType() {
        return new com.google.gson.reflect.TypeToken<List<Value>>() {}.getType();
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
            if (sql.startsWith("insert") && sql.contains("generalization")) {
                GeneralizationRecord record = this.records.newRecord(Tables.GENERALIZATION);
                bindInsert(record, Tables.GENERALIZATION, context.sql(), context.bindings());
                record.setId(this.nextGeneralizationId++);
                this.generalization = record;

                Result<GeneralizationRecord> result = this.records.newResult(Tables.GENERALIZATION);
                result.add(record);
                return new MockResult[] {new MockResult(1, result)};
            }
            if (sql.startsWith("update") && sql.contains("generalization")) {
                bindUpdate(this.generalization, Tables.GENERALIZATION, context.sql(), context.bindings());
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

        private static String columnName(String sqlName) {
            String name = sqlName.replace("\"", "").trim();
            return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
        }
    }
}
