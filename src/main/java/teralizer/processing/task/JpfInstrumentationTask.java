package teralizer.processing.task;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.PipelineQueries;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.ExpectedTypeInference;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.SpfSymbolicConfigSelector;
import teralizer.spoon.analysis.TestMethodResolver;
import teralizer.spoon.analysis.TestShape;
import teralizer.spoon.codegen.InstrumentedClassBuilder;
import teralizer.util.Configuration;
import teralizer.util.SpfSymbolicConfig;


public class JpfInstrumentationTask extends AbstractTask {

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null, null);
    }

    public JpfInstrumentationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.testRecord == null) {
            this.scheduleTasks(context, scheduleTask);
        } else {
            this.executeTask(context);
        }
    }

    private void scheduleTasks(TaskContext context, Consumer<Task> scheduleTask) {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        Result<Record> records = PipelineQueries.fetchIncludedAssertions(create, this.getProjectId());
        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfInstrumentationTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);
        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);
        Factory factory = spoonLauncher.getFactory();
        Gson gson = context.get(TaskContext.GSON);

        this.updateAssertionRecord();

        GeneralizationRecipe originalRecipe = GeneralizationRecipe
            .fromJson(gson, this.assertionRecord.getGeneralizationRecipe());
        CtMethod<?> originalTestMethod = TestMethodResolver.resolve(factory, this.testRecord);
        GeneralizationRecipe.Resolved recipe = originalRecipe.resolveAgainst(
            originalTestMethod,
            factory.getModel().getRootPackage()
        );
        GeneralizationRecipe clonedRecipe = originalRecipe.rewriteForClone(
            this.testRecord.getTestClassQualifiedName(),
            this.assertionRecord.getInstrumentedClassQualifiedName()
        );
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        boolean plainCallRecipe = isPlainCallRecipe(recipe);
        String wrapperReturnType = plainCallRecipe
            ? TestAnalysisTask.typeNameOf(ExpectedTypeInference.inferExpectedType((CtInvocation<?>) oracleExpression))
            : clonedRecipe.getOracleExpressionType();

        CtClass<?> instrumentedClass = new InstrumentedClassBuilder().build(
            factory,
            clonedRecipe,
            new InstrumentedClassBuilder.Names(
                this.testRecord.getTestPackageName(),
                this.assertionRecord.getInstrumentedPackageName(),
                this.testRecord.getTestClassName(),
                this.assertionRecord.getInstrumentedClassName(),
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName(),
                this.testRecord.getTestMethodRelativePath(),
                this.assertionRecord.getAssertionRelativePath(),
                this.assertionRecord.getInstrumentedMethodName(),
                this.assertionRecord.getTestedClassQualifiedName(),
                wrapperReturnType
            )
        );
        CtMethod<?> instrumentedMethod = instrumentedClass.getMethodsByName(this.assertionRecord.getInstrumentedMethodName()).get(0);

        this.createInstrumentedClassFile(spoonLauncher, instrumentedClass);

        this.createDriverClassFile(velocityEngine, spoonLauncher);
        this.createJpfConfigFile(velocityEngine, instrumentedMethod, testedMethod);
    }

    private void updateAssertionRecord() {
        Path testFilePath = Paths.get(this.testRecord.getTestFilePath());
        String testPackageName = this.testRecord.getTestPackageName();
        String packagePrefix = testPackageName.isEmpty() ? "" : testPackageName + ".";
        String testClassName = this.testRecord.getTestClassName();
        String testMethodName = this.testRecord.getTestMethodName();
        String testedMethodName = this.assertionRecord.getTestedMethodName();

        String instrumentedClassName = String.format("_%s_Instrumented_%s_%s_Test", testClassName, testMethodName, this.getAssertionId());
        String instrumentedMethodName = String.format("%s_%s", testedMethodName, this.getAssertionId());
        Path instrumentedFilePath = testFilePath.getParent().resolve(instrumentedClassName + ".java");

        this.assertionRecord.setInstrumentedFilePath(instrumentedFilePath.toString());
        this.assertionRecord.setInstrumentedClassQualifiedName(packagePrefix + instrumentedClassName);
        this.assertionRecord.setInstrumentedMethodQualifiedName(packagePrefix + instrumentedClassName + "." + instrumentedMethodName);
        this.assertionRecord.setInstrumentedPackageName(testPackageName);
        this.assertionRecord.setInstrumentedClassName(instrumentedClassName);
        this.assertionRecord.setInstrumentedMethodName(instrumentedMethodName);

        String driverClassName = String.format("_%s_Driver_%s", testClassName, instrumentedMethodName);
        Path driverFilePath = testFilePath.getParent().resolve(driverClassName + ".java");

        this.assertionRecord.setDriverFilePath(driverFilePath.toString());
        this.assertionRecord.setDriverClassQualifiedName(packagePrefix + driverClassName);
        this.assertionRecord.setDriverPackageName(testPackageName);
        this.assertionRecord.setDriverClassName(driverClassName);

        Path jpfDataPath = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/jpf-data/specs");
        String baseName = this.testRecord.getTestMethodQualifiedName() + "." + this.getAssertionId();
        Path jpfConfigPath = jpfDataPath.resolve(baseName + ".jpf");
        Path inputValuesPath = jpfDataPath.resolve(baseName + ".jpf.concrete.input.json");
        Path outputValuePath = jpfDataPath.resolve(baseName + ".jpf.concrete.output.json");
        Path inputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.symbolic.input.json");
        Path outputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.symbolic.output.json");

        this.assertionRecord.setJpfConfigPath(jpfConfigPath.toString());
        this.assertionRecord.setInputValuesPath(inputValuesPath.toString());
        this.assertionRecord.setOutputValuePath(outputValuePath.toString());
        this.assertionRecord.setInputSpecificationPath(inputSpecificationPath.toString());
        this.assertionRecord.setOutputSpecificationPath(outputSpecificationPath.toString());

        this.assertionRecord.store();
    }


    private void createInstrumentedClassFile(Launcher spoonLauncher, CtClass<?> instrumentedClass) throws IOException {
        CtCompilationUnit cu = spoonLauncher.getFactory().CompilationUnit().getOrCreate(this.assertionRecord.getInstrumentedFilePath());
        cu.setImports(SpoonUtils.importsForGeneratedClass(instrumentedClass));
        cu.setDeclaredTypes(Collections.singletonList(instrumentedClass));

        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(spoonLauncher.getEnvironment());
        printer.setIgnoreImplicit(false);
        printer.calculate(cu, Collections.singletonList(instrumentedClass));
        byte[] instrumentedFileBytes = printer.getResult().getBytes();

        Path instrumentedFilePath = Paths.get(this.assertionRecord.getInstrumentedFilePath());

        // Write the instrumented file to the project directory for further use in this run:
        instrumentedFilePath.toFile().getParentFile().mkdirs();
        Files.write(instrumentedFilePath, instrumentedFileBytes);

        // Copy the instrumented file to the data directory for cross-run storage:
        Path relativizedFilePath = this.projectRecord.getTestSourcePath().relativize(instrumentedFilePath);
        Path dataFilePath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve("jpf-data")
            .resolve("test")
            .resolve(relativizedFilePath);
        dataFilePath.getParent().toFile().mkdirs();
        Files.copy(instrumentedFilePath, dataFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private void createDriverClassFile(VelocityEngine velocityEngine, Launcher spoonLauncher) throws IOException {
        VelocityContext context = new VelocityContext();
        context.put("driverPackageName", this.assertionRecord.getDriverPackageName());
        context.put("driverClassName", this.assertionRecord.getDriverClassName());
        context.put("instrumentedClassQualifiedName", this.assertionRecord.getInstrumentedClassQualifiedName());
        context.put("instrumentedClassName", this.assertionRecord.getInstrumentedClassName());
        context.put("testMethodName", this.testRecord.getTestMethodName());

        Set<CtMethod<?>> beforeMethods = this.getBeforeMethods(spoonLauncher);
        context.put("beforeMethods", beforeMethods);
        for (CtMethod<?> method : beforeMethods) {
            if (!method.getParameters().isEmpty()) {
                throw new RuntimeException("Setup method " + method.getSimpleName() + " has parameters.");
            }
        }

        Set<CtMethod<?>> afterMethods = this.getAfterMethods(spoonLauncher);
        context.put("afterMethods", afterMethods);
        for (CtMethod<?> method : afterMethods) {
            if (!method.getParameters().isEmpty()) {
                throw new RuntimeException("Teardown method " + method.getSimpleName() + " has parameters.");
            }
        }

        File driverClassFile = new File(this.assertionRecord.getDriverFilePath());
        driverClassFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(driverClassFile)) {
            Template template = velocityEngine.getTemplate("driver-class.vm");
            template.merge(context, fileWriter);
        }

        // Copy the driver file to the data directory for cross-run storage:
        Path relativizedFilePath = this.projectRecord.getTestSourcePath().relativize(driverClassFile.toPath());
        Path dataFilePath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve("jpf-data")
            .resolve("test")
            .resolve(relativizedFilePath);
        dataFilePath.getParent().toFile().mkdirs();
        Files.copy(driverClassFile.toPath(), dataFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    private Set<CtMethod<?>> getBeforeMethods(Launcher spoonLauncher) {
        CtClass<?> testClass = spoonLauncher.getFactory().Class()
            .get(this.testRecord.getTestClassQualifiedName());

        return beforeMethodsFor(testClass);
    }

    private Set<CtMethod<?>> getAfterMethods(Launcher spoonLauncher) {
        CtClass<?> testClass = spoonLauncher.getFactory().Class()
            .get(this.testRecord.getTestClassQualifiedName());

        return afterMethodsFor(testClass);
    }

    static Set<CtMethod<?>> beforeMethodsFor(CtClass<?> testClass) {
        return fixtureMethodsFor(hierarchyOf(testClass), testClass, true);
    }

    static Set<CtMethod<?>> afterMethodsFor(CtClass<?> testClass) {
        return fixtureMethodsFor(hierarchyOf(testClass), testClass, false);
    }

    /**
     * Fixtures of the requested direction, ordered so the symbolic driver reproduces the
     * framework's own order. The hierarchy is walked parent first, so an inherited fixture runs
     * before its override; teardown reverses that, running the override before its parent.
     */
    private static Set<CtMethod<?>> fixtureMethodsFor(List<CtClass<?>> hierarchy, CtClass<?> testClass, boolean before) {
        Set<CtMethod<?>> fixtures = new LinkedHashSet<>();
        for (CtClass<?> currentClass : hierarchy) {
            for (CtMethod<?> method : currentClass.getMethods()) {
                TestShape.LifecyclePhase phase = TestShape.lifecyclePhaseOf(method, currentClass);
                if (phase != null && phase.isBefore() == before) {
                    fixtures.add(method);
                }
            }
        }
        if (!before) {
            List<CtMethod<?>> reversed = new ArrayList<>(fixtures);
            Collections.reverse(reversed);
            return new LinkedHashSet<>(reversed);
        }
        return fixtures;
    }

    private static List<CtClass<?>> hierarchyOf(CtClass<?> testClass) {
        List<CtClass<?>> hierarchy = new ArrayList<>();
        CtType<?> current = testClass;
        while (current instanceof CtClass<?>) {
            CtClass<?> currentClass = (CtClass<?>) current;
            hierarchy.add(0, currentClass);
            current = currentClass.getSuperclass() != null
                ? currentClass.getSuperclass().getDeclaration()
                : null;
        }
        return hierarchy;
    }

    private void createJpfConfigFile(VelocityEngine velocityEngine, CtMethod<?> instrumentedMethod, CtMethod<?> testedMethod) throws IOException {
        String symbolicParams = instrumentedMethod.getParameters().stream().map(InstrumentedClassBuilder::symbolicMarker).collect(Collectors.joining("#"));
        String symbolicMethod = this.assertionRecord.getInstrumentedMethodQualifiedName() + "(" + symbolicParams + ")";

        VelocityContext context = new VelocityContext();
        context.put("jpfSymbcModelClasspath", Configuration.JPF_SYMBC_MODEL_CLASSPATH);
        context.put("pathSeparator", File.pathSeparator);
        context.put("classpath", this.projectRecord.getClasspath());
        context.put("symbolicMethod", symbolicMethod);

        SpfSymbolicConfig symbolicConfig = SpfSymbolicConfigSelector.select(testedMethod);
        context.put("symbolicDp", symbolicConfig.getDp());
        context.put("symbolicFp", symbolicConfig.isFp());
        context.put("symbolicBvLength", symbolicConfig.getBvLength());
        context.put("symbolicStrings", symbolicConfig.isStrings());

        context.put("maxExecutionTime", Configuration.getJpfTimeoutPerAssertion());
        context.put("maxPathConditionSize", Configuration.getJpfMaxPathConditionSize());
        context.put("maxSearchDepth", Configuration.getJpfMaxSearchDepth());

        context.put("driverClassQualifiedName", this.assertionRecord.getDriverClassQualifiedName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testMethodQualifiedName", this.testRecord.getTestMethodQualifiedName());
        context.put("testedClassQualifiedName", this.assertionRecord.getTestedClassQualifiedName());
        context.put("testedMethodQualifiedName", this.assertionRecord.getTestedMethodQualifiedName());
        context.put("instrumentedClassQualifiedName", this.assertionRecord.getInstrumentedClassQualifiedName());
        context.put("instrumentedMethodQualifiedName", this.assertionRecord.getInstrumentedMethodQualifiedName());
        context.put("instrumentedParameterNames", instrumentedMethod.getParameters().stream()
            .map(CtParameter::getSimpleName).collect(Collectors.joining(",")));

        context.put("inputValuesPath", this.assertionRecord.getInputValuesPath());
        context.put("outputValuePath", this.assertionRecord.getOutputValuePath());
        context.put("inputSpecificationPath", this.assertionRecord.getInputSpecificationPath());
        context.put("outputSpecificationPath", this.assertionRecord.getOutputSpecificationPath());

        Path jpfOutputPath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId() + "/jpf-data/reports")
            .resolve(this.testRecord.getTestMethodQualifiedName() + "." + this.getAssertionId() + ".output.txt");
        context.put("reportPath", jpfOutputPath.toString());

        jpfOutputPath.getParent().toFile().mkdirs();
        jpfOutputPath.toFile().createNewFile();

        File jpfConfigFile = new File(this.assertionRecord.getJpfConfigPath());
        jpfConfigFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(jpfConfigFile)) {
            Template template = velocityEngine.getTemplate("jpf-config.vm");
            template.merge(context, fileWriter);
        }
    }

    private static boolean isPlainCallRecipe(GeneralizationRecipe.Resolved recipe) {
        return recipe.getInputs().stream().noneMatch(GeneralizableInput::isExpressionSite);
    }

}
