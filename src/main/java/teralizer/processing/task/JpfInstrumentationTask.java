package teralizer.processing.task;

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
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.analysis.TestAnalysis;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.SUPPORTED_TYPES;

public class JpfInstrumentationTask extends AbstractTask {

    private static final List<String> BEFORE_ANNOTATIONS = Arrays.asList(
        "org.junit.Before",
        "org.junit.BeforeClass",
        "org.junit.jupiter.api.BeforeAll",
        "org.junit.jupiter.api.BeforeEach"
    );

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

        Result<Record> records = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId());
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

        this.updateAssertionRecord();

        CtClass<?> instrumentedClass = this.createInstrumentedClass(factory);
        CtInvocation<?> testedMethodCall = this.getTestedMethodCall(instrumentedClass);
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();
        CtMethod<?> instrumentedMethod = this.createInstrumentedMethod(factory, instrumentedClass, testedMethod, testedMethodCall);
        CtInvocation<?> instrumentedMethodCall = this.createInstrumentedMethodCall(factory, instrumentedClass, instrumentedMethod, testedMethod, testedMethodCall);
        testedMethodCall.replace(instrumentedMethodCall);

        CtMethod<?> testMethod = instrumentedClass.getMethod(this.testRecord.getTestMethodName());
        CtPath targetAssertionPath = new CtPathStringBuilder().fromString(this.assertionRecord.getAssertionRelativePath());
        CtInvocation<?> targetAssertion = (CtInvocation<?>) targetAssertionPath.evaluateOn(testMethod).get(0);
        List<CtInvocation> otherAssertions = testMethod.getElements(new TypeFilter<>(CtInvocation.class)).stream().filter(i -> i != targetAssertion && (TestAnalysis.isJUnit4Assertion(i) || TestAnalysis.isJUnit5Assertion(i))).collect(Collectors.toList());
        otherAssertions.forEach(CtElement::delete);

        this.createInstrumentedClassFile(spoonLauncher, instrumentedClass);

        this.createDriverClassFile(velocityEngine, spoonLauncher);
        this.createJpfConfigFile(velocityEngine, instrumentedMethod);
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
        Path inputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.input.json");
        Path outputSpecificationPath = jpfDataPath.resolve(baseName + ".jpf.output.json");

        this.assertionRecord.setJpfConfigPath(jpfConfigPath.toString());
        this.assertionRecord.setInputSpecificationPath(inputSpecificationPath.toString());
        this.assertionRecord.setOutputSpecificationPath(outputSpecificationPath.toString());

        this.assertionRecord.store();
    }

    private CtClass<?> createInstrumentedClass(Factory factory) {
        CtClass<?> testClassDeclaration = factory.Class().get(this.testRecord.getTestClassQualifiedName());

        CtClass<?> instrumentedClass = testClassDeclaration.clone();
        instrumentedClass.setSimpleName(this.assertionRecord.getInstrumentedClassName());

        CtPackage instrumentedClassPackage = factory.Package().getOrCreate(this.assertionRecord.getInstrumentedPackageName());
        instrumentedClassPackage.addType(instrumentedClass);

        Predicate<CtMethod<?>> isTestMethod = (decl) -> decl.getSimpleName().equals(this.testRecord.getTestMethodName());
        Predicate<CtMethod<?>> hasTestAnnotation = (decl) -> decl.getAnnotations().stream().anyMatch(a -> a.getType().getSimpleName().equals("Test"));
        List<CtMethod<?>> otherTestMethods = instrumentedClass.getMethods().stream().filter(m -> !isTestMethod.test(m) && hasTestAnnotation.test(m)).collect(Collectors.toList());
        otherTestMethods.forEach(instrumentedClass::removeMethod);

        return instrumentedClass;
    }

    private CtInvocation<?> getTestedMethodCall(CtClass<?> instrumentedClass) {
        CtMethod<?> testMethod = instrumentedClass.getMethod(this.testRecord.getTestMethodName());
        CtPath testedMethodCallPath = new CtPathStringBuilder().fromString(this.assertionRecord.getTestedMethodCallRelativePath());
        return (CtInvocation<?>) testedMethodCallPath.evaluateOn(testMethod).get(0);
    }

    private CtMethod<?> createInstrumentedMethod(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> testedMethod,
        CtInvocation<?> testedMethodCall
    ) {
        List<CtParameter<?>> instrumentedParameters = new ArrayList<>();
        if (!testedMethod.isStatic()) {
            CtExpression<?> target = testedMethodCall.getTarget();
            CtTypeReference<?> targetType = target instanceof CtThisAccess
                ? factory.Type().get(this.assertionRecord.getInstrumentedClassQualifiedName()).getReference()
                : target.getType();
            CtParameter<?> parameter = factory.createParameter(null, targetType, "_target_");
            instrumentedParameters.add(parameter);
        }

        for (int i = 0; i < testedMethod.getParameters().size(); i++) {
            CtTypeReference<?> parameterType = testedMethod.getParameters().get(i).getType();
            CtTypeReference<?> argumentType = testedMethodCall.getArguments().get(i).getType();

            CtTypeReference<?> type;
            if (!parameterType.isGenerics()) {
                type = parameterType;
            } else if (!(argumentType == null) && !(argumentType.toString().equals("<nulltype>"))) {
                type = argumentType;
            } else {
                throw new RuntimeException(
                    "Failed to identify valid type for parameter " + testedMethod.getParameters().get(i)
                        + " of tested method " + this.assertionRecord.getTestedMethodQualifiedName()
                        + " in test method " + this.testRecord.getTestMethodQualifiedName() + "."
                );
            }

            CtParameter<?> parameter = factory.createParameter(null, type, testedMethod.getParameters().get(i).getSimpleName());
            instrumentedParameters.add(parameter);
        }

        CtInvocation<?> instrumentedTestedMethodCall = testedMethodCall.clone();
        List<CtExpression<?>> arguments = testedMethod.getParameters().stream().map(p -> factory.createCodeSnippetExpression(p.getSimpleName())).collect(Collectors.toList());
        instrumentedTestedMethodCall.setArguments(arguments);
        if (!testedMethod.isStatic()) {
            instrumentedTestedMethodCall.setTarget(factory.createCodeSnippetExpression(instrumentedParameters.get(0).getSimpleName()));
        }

        CtBlock<?> instrumentedBody = factory.createBlock();
        instrumentedBody.addStatement(factory.Code().createCodeSnippetStatement("return " + instrumentedTestedMethodCall));

        return factory.createMethod(
            instrumentedClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            !testedMethod.getType().isGenerics() ? testedMethod.getType() : factory.Type().objectType(),
            this.assertionRecord.getInstrumentedMethodName(),
            instrumentedParameters,
            testedMethod.getThrownTypes(),
            instrumentedBody
        );
    }

    private CtInvocation<?> createInstrumentedMethodCall(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> instrumentedMethod,
        CtMethod<?> testedMethod,
        CtInvocation<?> testedMethodCall
    ) {
        CtInvocation<?> instrumentedMethodCall = factory.createInvocation(factory.createThisAccess(instrumentedClass.getReference()), instrumentedMethod.getReference());
        if (!testedMethod.isStatic()) {
            CtExpression<?> target = testedMethodCall.getTarget();
            if (target instanceof CtThisAccess) {
                instrumentedMethodCall.addArgument(factory.createThisAccess(target.getType(), false));
            } else {
                instrumentedMethodCall.addArgument(target);
            }
        }
        for (CtExpression<?> argument : testedMethodCall.getArguments()) {
            instrumentedMethodCall.addArgument(argument);
        }
        return instrumentedMethodCall;
    }

    private void createInstrumentedClassFile(Launcher spoonLauncher, CtClass<?> instrumentedClass) throws IOException {
        Path instrumentedFilePath = Paths.get(this.assertionRecord.getInstrumentedFilePath());
        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(spoonLauncher.getEnvironment());
        printer.calculate(instrumentedClass.getPosition().getCompilationUnit(), Collections.singletonList(instrumentedClass));
        byte[] instrumentedFileBytes = printer.getResult().getBytes();

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

        return BEFORE_ANNOTATIONS.stream()
            .map(spoonLauncher.getFactory().Annotation()::createReference)
            .flatMap(annotation -> testClass.getMethodsAnnotatedWith(annotation).stream())
            .collect(Collectors.toSet());
    }

    private void createJpfConfigFile(VelocityEngine velocityEngine, CtMethod<?> instrumentedMethod) throws IOException {
        String symbolicParams = instrumentedMethod.getParameters().stream().map(p -> SUPPORTED_TYPES.contains(p.getType().getSimpleName()) ? "sym" : "con").collect(Collectors.joining("#"));
        String symbolicMethod = this.assertionRecord.getInstrumentedMethodQualifiedName() + "(" + symbolicParams + ")";

        VelocityContext context = new VelocityContext();
        context.put("classpath", this.projectRecord.getClasspath());
        context.put("symbolicMethod", symbolicMethod);

        context.put("maxExecutionTime", TestGeneralizationRunner.JPF_MAX_EXECUTION_TIME);
        context.put("maxPathConditionSize", TestGeneralizationRunner.JPF_MAX_PATH_CONDITION_SIZE);

        context.put("driverClassQualifiedName", this.assertionRecord.getDriverClassQualifiedName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testMethodQualifiedName", this.testRecord.getTestMethodQualifiedName());
        context.put("testedClassQualifiedName", this.assertionRecord.getTestedClassQualifiedName());
        context.put("testedMethodQualifiedName", this.assertionRecord.getTestedMethodQualifiedName());
        context.put("instrumentedClassQualifiedName", this.assertionRecord.getInstrumentedClassQualifiedName());
        context.put("instrumentedMethodQualifiedName", this.assertionRecord.getInstrumentedMethodQualifiedName());

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
            // @TODO: Add execution of @BeforeAll, @Before, @After, @AfterAll to the template.
            // @TODO: How to handle methods (without parameters) that depend on object state?
            Template template = velocityEngine.getTemplate("jpf-config.vm");
            template.merge(context, fileWriter);
        }
    }
}
