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
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.SpfSymbolicConfigSelector;
import teralizer.util.Configuration;
import teralizer.util.SpfSymbolicConfig;
import teralizer.util.TypeCapability;


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
        Gson gson = context.get(TaskContext.GSON);

        this.updateAssertionRecord();

        CtClass<?> instrumentedClass = this.createInstrumentedClass(factory);
        GeneralizationRecipe clonedRecipe = GeneralizationRecipe
            .fromJson(gson, this.assertionRecord.getGeneralizationRecipe())
            .rewriteForClone(
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName()
            );
        GeneralizationRecipe.Resolved recipe = clonedRecipe
            .resolveAgainst(instrumentedClass.getMethod(this.testRecord.getTestMethodName()), factory.getModel().getRootPackage());
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        CtMethod<?> instrumentedMethod = this.createInstrumentedMethod(
            factory,
            instrumentedClass,
            recipe,
            clonedRecipe.getOracleExpressionType()
        );
        CtInvocation<?> instrumentedMethodCall = this.createInstrumentedMethodCall(
            factory,
            instrumentedClass,
            instrumentedMethod,
            recipe
        );
        oracleExpression.replace(instrumentedMethodCall);

        CtMethod<?> testMethod = instrumentedClass.getMethod(this.testRecord.getTestMethodName());
        CtPath targetAssertionPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                this.assertionRecord.getAssertionRelativePath(),
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName()));
        CtInvocation<?> targetAssertion = (CtInvocation<?>) targetAssertionPath.evaluateOn(testMethod).get(0);

        SpoonUtils.deleteOtherAssertionsInMethod(testMethod, targetAssertion);

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

    private CtClass<?> createInstrumentedClass(Factory factory) throws IOException {
        CtClass<?> instrumentedClass = SpoonUtils.cloneClass(
            factory,
            factory.Class().get(this.testRecord.getTestClassQualifiedName()),
            this.testRecord.getTestPackageName(),
            this.assertionRecord.getInstrumentedPackageName(),
            this.testRecord.getTestClassName(),
            this.assertionRecord.getInstrumentedClassName(),
            this.testRecord.getTestClassQualifiedName(),
            this.assertionRecord.getInstrumentedClassQualifiedName()
        );

        CtPath testMethodPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                this.testRecord.getTestMethodRelativePath(),
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName()));
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(instrumentedClass).get(0);

        SpoonUtils.deleteOtherTestMethodsInClass(instrumentedClass, testMethod);

        return instrumentedClass;
    }



    private CtMethod<?> createInstrumentedMethod(
        Factory factory,
        CtClass<?> instrumentedClass,
        GeneralizationRecipe.Resolved recipe,
        String oracleExpressionType
    ) {
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        List<GeneralizableInput> generalizableInputs = recipe.getInputs();
        CtExpression<?> rewrittenExpression = oracleExpression.clone();
        recipe.replaceInputSitesWithParameterReads(
            rewrittenExpression,
            factory,
            input -> input.toMethodParameter().getName()
        );

        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);
        boolean needsTarget = !testedMethod.isStatic() && !hasReceiverConstructorInputs;
        List<CtParameter<?>> instrumentedParameters = new ArrayList<>();
        if (needsTarget) {
            CtInvocation<?> oracleCall = findOracleInvocation(oracleExpression, testedMethod);
            CtTypeReference<?> targetType = oracleCall.getTarget() instanceof CtThisAccess
                ? instrumentedClass.getReference()
                : oracleCall.getTarget().getType();
            CtTypeReference<?> resolvedType = resolveTargetType(
                factory, targetType, this.assertionRecord.getTestedClassQualifiedName());
            instrumentedParameters.add(factory.createParameter(null, resolvedType, "_target_"));
            findOracleInvocation(rewrittenExpression, testedMethod)
                .setTarget(factory.createCodeSnippetExpression("_target_"));
        }
        for (GeneralizableInput input : generalizableInputs) {
            CtTypeReference<?> type = factory.Type().createReference(input.toMethodParameter().getType());
            type.setSimplyQualified(false);
            type.setImplicit(false);
            instrumentedParameters.add(factory.createParameter(null, type, input.toMethodParameter().getName()));
        }

        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(rewrittenExpression, oracleExpression, generalizableInputs);
        Map<CtVariableReference<?>, String> liftedNames = liftedParameterNames(liftedLocals.keySet());
        for (Map.Entry<CtVariableReference<?>, CtTypeReference<?>> lifted : liftedLocals.entrySet()) {
            CtTypeReference<?> type = lifted.getValue().clone();
            type.setSimplyQualified(false);
            type.setImplicit(false);
            instrumentedParameters.add(factory.createParameter(null, type, liftedNames.get(lifted.getKey())));
        }
        liftLocalReads(rewrittenExpression, liftedNames, factory);

        CtReturn returnStatement = factory.Core().createReturn();
        returnStatement.setReturnedExpression(rewrittenExpression);
        CtBlock<?> instrumentedBody = factory.createBlock();
        instrumentedBody.addStatement(returnStatement);

        CtTypeReference<?> returnType = factory.Type().createReference(oracleExpressionType);
        returnType.setSimplyQualified(false);
        returnType.setImplicit(false);

        Set<CtTypeReference<? extends Throwable>> thrownTypes = collectThrownTypes(testedMethod, oracleExpression);

        return factory.createMethod(
            instrumentedClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            returnType,
            this.assertionRecord.getInstrumentedMethodName(),
            instrumentedParameters,
            thrownTypes,
            instrumentedBody
        );
    }


    /**
     * Collects every checked-exception type the instrumented wrapper must declare: the tested
     * method's own {@code throws}, plus the declared {@code throws} of every call and constructor
     * cloned into the wrapper body (e.g. a lifted receiver/argument constructor such as
     * {@code new URL(String)}, which throws {@code MalformedURLException}). The original test
     * method compiled with those calls inline, so its call site already handles these exceptions;
     * omitting them from the wrapper breaks BUILD_PROJECT_INSTRUMENTED instead.
     */
    static Set<CtTypeReference<? extends Throwable>> collectThrownTypes(CtMethod<?> testedMethod, CtInvocation<?> testedMethodCall) {
        return collectThrownTypes(testedMethod, (CtElement) testedMethodCall);
    }

    private static Set<CtTypeReference<? extends Throwable>> collectThrownTypes(CtMethod<?> testedMethod, CtElement wrapperBodyExpression) {
        Set<CtTypeReference<? extends Throwable>> thrownTypes = new HashSet<>(testedMethod.getThrownTypes());
        for (CtAbstractInvocation<?> invocation : wrapperBodyExpression.getElements(new TypeFilter<>(CtAbstractInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                continue;
            }
            CtExecutable<?> declaration = executable.getExecutableDeclaration();
            if (declaration != null) {
                thrownTypes.addAll(declaration.getThrownTypes());
            }
        }
        thrownTypes.forEach(t -> {
            t.setSimplyQualified(false);
            t.setImplicit(false);
        });
        return thrownTypes;
    }

    /**
     * Determines the concrete type for the {@code _target_} receiver parameter. Normally the
     * receiver expression's static type is a real class, but when the receiver is referenced
     * through a generic type variable -- e.g. a field of type {@code C extends AbstractMqttChannel}
     * in an abstract test base whose concrete subclass binds {@code C} to a real channel type --
     * Spoon reports the erased type-parameter bound, which is not a usable parameter type. In that
     * case fall back to the concrete declaring class the resolver already pinned for the tested
     * method, so the generated wrapper receives a compilable, concrete receiver.
     */
    static CtTypeReference<?> resolveTargetType(
        Factory factory, CtTypeReference<?> receiverType, String testedClassQualifiedName
    ) {
        if (!(receiverType instanceof CtTypeParameterReference)) {
            return receiverType;
        }
        CtTypeReference<?> concreteType = factory.Type().createReference(testedClassQualifiedName);
        concreteType.setSimplyQualified(false);
        concreteType.setImplicit(false);
        return concreteType;
    }

    /**
     * Finds every test-method local (or test-method parameter) that a CLONED part of the wrapper
     * body would still reference. Generalizable argument positions are replaced by wrapper
     * parameters and contribute nothing; everything cloned verbatim -- unsupported-type arguments,
     * non-lifted constructor arguments -- may carry reads of variables that only exist in the test
     * method's scope. Left alone, those reads make the generated wrapper uncompilable, and one bad
     * wrapper fails BUILD_PROJECT_INSTRUMENTED for the whole project. Each such variable becomes an
     * additional wrapper parameter, passed concretely from the call site (the same environment-
     * carrying pattern as the {@code _target_} receiver parameter).
     */
    private static CtInvocation<?> findOracleInvocation(CtExpression<?> expression, CtMethod<?> oracleMethod) {
        if (expression instanceof CtInvocation<?>
            && ((CtInvocation<?>) expression).getExecutable().getSimpleName().equals(oracleMethod.getSimpleName())) {
            return (CtInvocation<?>) expression;
        }
        return expression.getElements(CtInvocation.class::isInstance).stream()
            .map(CtInvocation.class::cast)
            .filter(invocation -> invocation.getExecutable().getSimpleName().equals(oracleMethod.getSimpleName()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Could not find oracle invocation in expression " + expression));
    }

    static LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> collectLiftableLocals(
        CtExpression<?> rewrittenExpression,
        CtExpression<?> originalExpression,
        List<GeneralizableInput> generalizableInputs
    ) {
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted = new LinkedHashMap<>();
        collectOutOfScopeReads(
            rewrittenExpression,
            originalExpression,
            generalizableInputs.stream()
                .map(input -> input.toMethodParameter().getName())
                .collect(Collectors.toSet()),
            lifted
        );
        return lifted;
    }


    /**
     * Collects reads of variables declared OUTSIDE the tested call expression: test-method locals,
     * test-method parameters, and catch variables. Field reads are excluded (they resolve against
     * the instrumented class, which retains the test class's fields), as are reads of variables the
     * expression itself declares (lambda parameters, locals inside the argument expression).
     */
    private static void collectOutOfScopeReads(
        CtExpression<?> expression,
        CtExpression<?> originalExpression,
        Set<String> parameterNames,
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted
    ) {
        for (CtVariableRead<?> read : expression.getElements(new TypeFilter<>(CtVariableRead.class))) {
            if (read instanceof CtFieldRead) {
                continue;
            }
            CtVariableReference<?> reference = read.getVariable();
            if (parameterNames.contains(reference.getSimpleName())) {
                continue;
            }
            CtElement declaration = reference.getDeclaration();
            if (declaration != null && declaration.hasParent(originalExpression)) {
                continue; // declared inside the expression itself -- already in wrapper scope
            }
            CtTypeReference<?> type = reference.getType();
            if (type == null || type instanceof CtTypeParameterReference) {
                throw new RuntimeException(
                    "Cannot lift test-local variable '" + reference.getSimpleName()
                        + "' (unresolvable type) referenced by oracle expression " + originalExpression);
            }
            lifted.putIfAbsent(reference, type);
        }
    }

    /**
     * Deterministic wrapper-parameter names for lifted locals: {@code _local_<name>}, with an
     * ordinal suffix when distinct variables share a simple name (shadowing across blocks).
     */
    static Map<CtVariableReference<?>, String> liftedParameterNames(Set<CtVariableReference<?>> references) {
        Map<CtVariableReference<?>, String> names = new LinkedHashMap<>();
        Map<String, Integer> seen = new HashMap<>();
        for (CtVariableReference<?> reference : references) {
            String base = "_local_" + reference.getSimpleName();
            int ordinal = seen.merge(reference.getSimpleName(), 1, Integer::sum);
            names.put(reference, ordinal == 1 ? base : base + "_" + ordinal);
        }
        return names;
    }

    /**
     * Rewrites reads of lifted variables inside the CLONED wrapper call to the lifted parameter
     * names. Matching is by variable simple name: within one Java method, a simple name denotes at
     * most one visible local at the tested call's position, and cloned reads keep that name.
     */
    private static void liftLocalReads(
        CtExpression<?> clonedExpression,
        Map<CtVariableReference<?>, String> liftedNames,
        Factory factory
    ) {
        Map<String, String> bySimpleName = new HashMap<>();
        for (Map.Entry<CtVariableReference<?>, String> entry : liftedNames.entrySet()) {
            bySimpleName.put(entry.getKey().getSimpleName(), entry.getValue());
        }
        for (CtVariableRead<?> read : clonedExpression.getElements(new TypeFilter<>(CtVariableRead.class))) {
            if (read instanceof CtFieldRead) {
                continue;
            }
            String liftedName = bySimpleName.get(read.getVariable().getSimpleName());
            if (liftedName != null) {
                read.replace(factory.createCodeSnippetExpression(liftedName));
            }
        }
    }


    private CtInvocation<?> createInstrumentedMethodCall(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> instrumentedMethod,
        GeneralizationRecipe.Resolved recipe
    ) {
        CtMethod<?> testedMethod = recipe.getOracleMethod();
        CtExpression<?> oracleExpression = recipe.getOracleExpression();
        List<GeneralizableInput> generalizableInputs = recipe.getInputs();
        CtInvocation<?> instrumentedMethodCall = factory.createInvocation(factory.createThisAccess(instrumentedClass.getReference()), instrumentedMethod.getReference());
        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);
        if (!testedMethod.isStatic() && !hasReceiverConstructorInputs) {
            CtExpression<?> target = findOracleInvocation(oracleExpression, testedMethod).getTarget();
            if (target instanceof CtThisAccess) {
                instrumentedMethodCall.addArgument(factory.createThisAccess(target.getType(), false));
            } else {
                instrumentedMethodCall.addArgument(target);
            }
        }
        for (GeneralizableInput input : generalizableInputs) {
            instrumentedMethodCall.addArgument(input.getSourceExpression());
        }
        CtExpression<?> rewrittenExpression = oracleExpression.clone();
        recipe.replaceInputSitesWithParameterReads(
            rewrittenExpression,
            factory,
            input -> input.toMethodParameter().getName()
        );
        if (!testedMethod.isStatic() && !hasReceiverConstructorInputs) {
            findOracleInvocation(rewrittenExpression, testedMethod).setTarget(factory.createCodeSnippetExpression("_target_"));
        }
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(rewrittenExpression, oracleExpression, generalizableInputs);
        for (CtVariableReference<?> reference : liftedLocals.keySet()) {
            instrumentedMethodCall.addArgument(factory.createCodeSnippetExpression(reference.getSimpleName()));
        }
        return instrumentedMethodCall;
    }

    private void createInstrumentedClassFile(Launcher spoonLauncher, CtClass<?> instrumentedClass) throws IOException {
        CtCompilationUnit cu = spoonLauncher.getFactory().CompilationUnit().getOrCreate(this.assertionRecord.getInstrumentedFilePath());
        cu.setImports(instrumentedClass.getPosition().getCompilationUnit().getImports().stream().map(CtImport::clone).collect(Collectors.toList()));
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

    private void createJpfConfigFile(VelocityEngine velocityEngine, CtMethod<?> instrumentedMethod, CtMethod<?> testedMethod) throws IOException {
        String symbolicParams = instrumentedMethod.getParameters().stream().map(JpfInstrumentationTask::symbolicMarker).collect(Collectors.joining("#"));
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

        context.put("maxExecutionTime", Configuration.getJpfMaxExecutionTime());
        context.put("maxPathConditionSize", Configuration.getJpfMaxPathConditionSize());
        context.put("maxSearchDepth", Configuration.getJpfMaxSearchDepth());

        context.put("driverClassQualifiedName", this.assertionRecord.getDriverClassQualifiedName());
        context.put("testClassQualifiedName", this.testRecord.getTestClassQualifiedName());
        context.put("testMethodQualifiedName", this.testRecord.getTestMethodQualifiedName());
        context.put("testedClassQualifiedName", this.assertionRecord.getTestedClassQualifiedName());
        context.put("testedMethodQualifiedName", this.assertionRecord.getTestedMethodQualifiedName());
        context.put("instrumentedClassQualifiedName", this.assertionRecord.getInstrumentedClassQualifiedName());
        context.put("instrumentedMethodQualifiedName", this.assertionRecord.getInstrumentedMethodQualifiedName());

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

    /**
     * Decides whether a wrapper parameter is symbolized ({@code sym}) or stays concrete
     * ({@code con}) in the generated {@code symbolic.method} spec. The {@code _target_} receiver
     * and {@code _local_*} lifted test-locals carry the test's fixed environment -- symbolizing
     * them would let SPF vary state the test does not control (a String receiver was previously
     * symbolized purely because String is an input-generatable type). Everything else is a
     * genuine input site and is symbolized when its type has a generator.
     */
    static String symbolicMarker(CtParameter<?> parameter) {
        String name = parameter.getSimpleName();
        if ("_target_".equals(name) || name.startsWith("_local_")) {
            return "con";
        }
        return TypeCapability.supportsGeneratedInput(parameter.getType().getSimpleName()) ? "sym" : "con";
    }
}
