package teralizer.processing.task;

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
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizableInput;
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

        this.updateAssertionRecord();

        CtClass<?> instrumentedClass = this.createInstrumentedClass(factory);
        CtInvocation<?> testedMethodCall = this.getTestedMethodCall(instrumentedClass);
        CtPath testedMethodPath = new CtPathStringBuilder().fromString(this.assertionRecord.getTestedMethodAbsolutePath());
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodPath.evaluateOn(factory.getModel().getRootPackage()).get(0);
        CtMethod<?> instrumentedMethod = this.createInstrumentedMethod(factory, instrumentedClass, testedMethod, testedMethodCall);
        CtInvocation<?> instrumentedMethodCall = this.createInstrumentedMethodCall(factory, instrumentedClass, instrumentedMethod, testedMethod, testedMethodCall);
        testedMethodCall.replace(instrumentedMethodCall);

        CtMethod<?> testMethod = instrumentedClass.getMethod(this.testRecord.getTestMethodName());
        CtPath targetAssertionPath = new CtPathStringBuilder().fromString(
            this.assertionRecord.getAssertionRelativePath().replace(
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
            this.testRecord.getTestMethodRelativePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName()));
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(instrumentedClass).get(0);

        SpoonUtils.deleteOtherTestMethodsInClass(instrumentedClass, testMethod);

        return instrumentedClass;
    }

    private CtInvocation<?> getTestedMethodCall(CtClass<?> instrumentedClass) {
        CtMethod<?> testMethod = instrumentedClass.getMethod(this.testRecord.getTestMethodName());
        CtPath testedMethodCallPath = new CtPathStringBuilder().fromString(
            this.assertionRecord.getTestedMethodCallRelativePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.assertionRecord.getInstrumentedClassQualifiedName()));
        return (CtInvocation<?>) testedMethodCallPath.evaluateOn(testMethod).get(0);
    }

    private CtMethod<?> createInstrumentedMethod(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> testedMethod,
        CtInvocation<?> testedMethodCall
    ) {
        List<GeneralizableInput> generalizableInputs = GeneralizableInput.derive(testedMethod, testedMethodCall);
        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);

        List<CtParameter<?>> instrumentedParameters = new ArrayList<>();
        if (!testedMethod.isStatic() && !hasReceiverConstructorInputs) {
            CtExpression<?> target = testedMethodCall.getTarget();
            CtTypeReference<?> targetType = target instanceof CtThisAccess
                ? factory.Type().get(this.assertionRecord.getInstrumentedClassQualifiedName()).getReference()
                : target.getType();
            if (targetType instanceof CtTypeParameterReference) {
                throw new RuntimeException(
                    "Failed to identify valid type for parameter _target_"
                        + " of tested method " + this.assertionRecord.getTestedMethodQualifiedName()
                        + " in test method " + this.testRecord.getTestMethodQualifiedName() + "."
                );
            } else {
                CtParameter<?> parameter = factory.createParameter(null, targetType, "_target_");
                instrumentedParameters.add(parameter);
            }
        }
        for (GeneralizableInput input : generalizableInputs) {
            CtTypeReference<?> type = factory.Type().createReference(input.toMethodParameter().getType());
            type.setSimplyQualified(false);
            type.setImplicit(false);

            CtParameter<?> parameter = factory.createParameter(null, type, input.toMethodParameter().getName());
            instrumentedParameters.add(parameter);
        }

        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(testedMethod, testedMethodCall, generalizableInputs);
        Map<CtVariableReference<?>, String> liftedNames = liftedParameterNames(liftedLocals.keySet());
        for (Map.Entry<CtVariableReference<?>, CtTypeReference<?>> lifted : liftedLocals.entrySet()) {
            CtTypeReference<?> type = lifted.getValue().clone();
            type.setSimplyQualified(false);
            type.setImplicit(false);
            instrumentedParameters.add(factory.createParameter(null, type, liftedNames.get(lifted.getKey())));
        }

        CtInvocation<?> instrumentedTestedMethodCall = testedMethodCall.clone();
        List<CtExpression<?>> arguments = new ArrayList<>();
        for (int i = 0; i < testedMethodCall.getArguments().size(); i++) {
            final int argumentIndex = i;
            List<GeneralizableInput> inputsForArgument = generalizableInputs.stream()
                .filter(input -> input.getMethodArgumentIndex() == argumentIndex)
                .collect(Collectors.toList());
            if (inputsForArgument.isEmpty()) {
                arguments.add(testedMethodCall.getArguments().get(i).clone());
            } else if (inputsForArgument.get(0).isConstructorArgument()) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) testedMethodCall.getArguments().get(i).clone();
                List<CtExpression<?>> constructorArguments = new ArrayList<>(constructorCall.getArguments());
                for (GeneralizableInput input : inputsForArgument) {
                    constructorArguments.set(
                        input.getConstructorArgumentIndex(),
                        factory.createCodeSnippetExpression(input.toMethodParameter().getName())
                    );
                }
                constructorCall.setArguments(constructorArguments);
                arguments.add(constructorCall);
            } else {
                arguments.add(factory.createCodeSnippetExpression(inputsForArgument.get(0).toMethodParameter().getName()));
            }
        }
        instrumentedTestedMethodCall.setArguments(arguments);
        liftLocalReads(instrumentedTestedMethodCall, liftedNames, factory);
        if (!testedMethod.isStatic()) {
            if (hasReceiverConstructorInputs) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) testedMethodCall.getTarget().clone();
                List<CtExpression<?>> constructorArguments = new ArrayList<>(constructorCall.getArguments());
                generalizableInputs.stream()
                    .filter(GeneralizableInput::isReceiverConstructorArgument)
                    .forEach(input -> constructorArguments.set(
                        input.getConstructorArgumentIndex(),
                        factory.createCodeSnippetExpression(input.toMethodParameter().getName())
                    ));
                constructorCall.setArguments(constructorArguments);
                instrumentedTestedMethodCall.setTarget(constructorCall);
            } else {
                instrumentedTestedMethodCall.setTarget(factory.createCodeSnippetExpression(instrumentedParameters.get(0).getSimpleName()));
            }
        }

        CtBlock<?> instrumentedBody = factory.createBlock();
        instrumentedBody.addStatement(factory.Code().createCodeSnippetStatement("return " + instrumentedTestedMethodCall));
        CtTypeReference<?> returnType = this.inferExpectedType(testedMethodCall);
        returnType.setSimplyQualified(false);
        returnType.setImplicit(false);

        Set<CtTypeReference<? extends Throwable>> thrownTypes = collectThrownTypes(testedMethod, testedMethodCall);

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
        Set<CtTypeReference<? extends Throwable>> thrownTypes = new HashSet<>(testedMethod.getThrownTypes());
        for (CtElement element : testedMethodCall.getElements(CtAbstractInvocation.class::isInstance)) {
            CtExecutableReference<?> executable = ((CtAbstractInvocation<?>) element).getExecutable();
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
     * Finds every test-method local (or test-method parameter) that a CLONED part of the wrapper
     * body would still reference. Generalizable argument positions are replaced by wrapper
     * parameters and contribute nothing; everything cloned verbatim -- unsupported-type arguments,
     * non-lifted constructor arguments -- may carry reads of variables that only exist in the test
     * method's scope. Left alone, those reads make the generated wrapper uncompilable, and one bad
     * wrapper fails BUILD_PROJECT_INSTRUMENTED for the whole project. Each such variable becomes an
     * additional wrapper parameter, passed concretely from the call site (the same environment-
     * carrying pattern as the {@code _target_} receiver parameter).
     */
    static LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> collectLiftableLocals(
        CtMethod<?> testedMethod,
        CtInvocation<?> testedMethodCall,
        List<GeneralizableInput> generalizableInputs
    ) {
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted = new LinkedHashMap<>();
        boolean hasReceiverConstructorInputs = generalizableInputs.stream()
            .anyMatch(GeneralizableInput::isReceiverConstructorArgument);

        for (int i = 0; i < testedMethodCall.getArguments().size(); i++) {
            final int argumentIndex = i;
            List<GeneralizableInput> inputsForArgument = generalizableInputs.stream()
                .filter(input -> input.getMethodArgumentIndex() == argumentIndex)
                .collect(Collectors.toList());
            CtExpression<?> argument = testedMethodCall.getArguments().get(i);
            if (inputsForArgument.isEmpty()) {
                collectOutOfScopeReads(argument, testedMethodCall, lifted);
            } else if (inputsForArgument.get(0).isConstructorArgument()) {
                collectFromConstructorCall((CtConstructorCall<?>) argument, inputsForArgument, testedMethodCall, lifted);
            }
            // Fully-replaced generalizable arguments contribute nothing.
        }
        if (!testedMethod.isStatic() && hasReceiverConstructorInputs) {
            List<GeneralizableInput> receiverInputs = generalizableInputs.stream()
                .filter(GeneralizableInput::isReceiverConstructorArgument)
                .collect(Collectors.toList());
            collectFromConstructorCall((CtConstructorCall<?>) testedMethodCall.getTarget(), receiverInputs, testedMethodCall, lifted);
        }
        // A plain (non-constructor) receiver is replaced wholesale by _target_, never cloned.
        return lifted;
    }

    /** Scans the constructor arguments that input lifting did NOT replace with wrapper parameters. */
    private static void collectFromConstructorCall(
        CtConstructorCall<?> constructorCall,
        List<GeneralizableInput> liftedInputs,
        CtInvocation<?> testedMethodCall,
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted
    ) {
        Set<Integer> replaced = liftedInputs.stream()
            .map(GeneralizableInput::getConstructorArgumentIndex)
            .collect(Collectors.toSet());
        List<CtExpression<?>> constructorArguments = constructorCall.getArguments();
        for (int i = 0; i < constructorArguments.size(); i++) {
            if (!replaced.contains(i)) {
                collectOutOfScopeReads(constructorArguments.get(i), testedMethodCall, lifted);
            }
        }
    }

    /**
     * Collects reads of variables declared OUTSIDE the tested call expression: test-method locals,
     * test-method parameters, and catch variables. Field reads are excluded (they resolve against
     * the instrumented class, which retains the test class's fields), as are reads of variables the
     * expression itself declares (lambda parameters, locals inside the argument expression).
     */
    private static void collectOutOfScopeReads(
        CtExpression<?> expression,
        CtInvocation<?> testedMethodCall,
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted
    ) {
        for (CtElement element : expression.getElements(CtVariableRead.class::isInstance)) {
            CtVariableRead<?> read = (CtVariableRead<?>) element;
            if (read instanceof CtFieldRead) {
                continue;
            }
            CtVariableReference<?> reference = read.getVariable();
            CtElement declaration = reference.getDeclaration();
            if (declaration != null && declaration.hasParent(testedMethodCall)) {
                continue; // declared inside the expression itself -- already in wrapper scope
            }
            CtTypeReference<?> type = reference.getType();
            if (type == null || type instanceof CtTypeParameterReference) {
                throw new RuntimeException(
                    "Cannot lift test-local variable '" + reference.getSimpleName()
                        + "' (unresolvable type) referenced by tested call " + testedMethodCall);
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
        CtInvocation<?> clonedCall,
        Map<CtVariableReference<?>, String> liftedNames,
        Factory factory
    ) {
        Map<String, String> bySimpleName = new HashMap<>();
        for (Map.Entry<CtVariableReference<?>, String> entry : liftedNames.entrySet()) {
            bySimpleName.put(entry.getKey().getSimpleName(), entry.getValue());
        }
        for (CtElement element : clonedCall.getElements(CtVariableRead.class::isInstance)) {
            CtVariableRead<?> read = (CtVariableRead<?>) element;
            if (read instanceof CtFieldRead) {
                continue;
            }
            String liftedName = bySimpleName.get(read.getVariable().getSimpleName());
            if (liftedName != null) {
                read.replace(factory.createCodeSnippetExpression(liftedName));
            }
        }
    }

    CtTypeReference<?> inferExpectedType(CtInvocation<?> call) {
        CtElement parent = call.getParent();
        Factory factory = call.getFactory();

        // Assignment: x = foo();
        if (parent instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
            return this.eraseGenerics(assignment.getAssigned().getType(), factory);
        }

        // Variable declaration: Type x = foo();
        if (parent instanceof CtVariable) {
            CtVariable<?> variable = (CtVariable<?>) parent;
            return this.eraseGenerics(variable.getType(), factory);
        }

        // Method argument: bar(foo());
        if (parent instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) parent;
            int argIndex = invocation.getArguments().indexOf(call);
            if (argIndex >= 0) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                List<CtTypeReference<?>> paramTypes = execRef.getParameters();
                if (argIndex < paramTypes.size()) {
                    return this.eraseGenerics(paramTypes.get(argIndex), factory);
                } else if (!paramTypes.isEmpty()) {
                    return this.eraseGenerics(paramTypes.get(paramTypes.size() - 1), factory);
                }
            }
        }

        // Return statement: return foo();
        if (parent instanceof CtReturn) {
            CtMethod<?> enclosingMethod = call.getParent(CtMethod.class);
            if (enclosingMethod != null) {
                return this.eraseGenerics(enclosingMethod.getType(), factory);
            }
        }

        // Conditional expression: foo() ? ... : ...
        if (parent instanceof CtConditional) {
            return factory.Type().BOOLEAN_PRIMITIVE;
        }

        // Fallback: type of the called method
        return this.eraseGenerics(call.getType(), factory);
    }

    private CtTypeReference<?> eraseGenerics(CtTypeReference<?> type, Factory factory) {
        if (type == null || type.isGenerics() || type instanceof CtTypeParameterReference) {
            return factory.Type().OBJECT;
        }
        return type;
    }

    private CtInvocation<?> createInstrumentedMethodCall(
        Factory factory,
        CtClass<?> instrumentedClass,
        CtMethod<?> instrumentedMethod,
        CtMethod<?> testedMethod,
        CtInvocation<?> testedMethodCall
    ) {
        CtInvocation<?> instrumentedMethodCall = factory.createInvocation(factory.createThisAccess(instrumentedClass.getReference()), instrumentedMethod.getReference());
        List<GeneralizableInput> generalizableInputs = GeneralizableInput.derive(testedMethod, testedMethodCall);
        boolean hasReceiverConstructorInputs = generalizableInputs.stream().anyMatch(GeneralizableInput::isReceiverConstructorArgument);
        if (!testedMethod.isStatic() && !hasReceiverConstructorInputs) {
            CtExpression<?> target = testedMethodCall.getTarget();
            if (target instanceof CtThisAccess) {
                instrumentedMethodCall.addArgument(factory.createThisAccess(target.getType(), false));
            } else {
                instrumentedMethodCall.addArgument(target);
            }
        }
        for (GeneralizableInput input : generalizableInputs) {
            instrumentedMethodCall.addArgument(input.getSourceExpression());
        }
        // Lifted test-locals are passed by name: the call site sits inside the test method,
        // where those locals are in scope. Order matches the wrapper's parameter list because
        // both sides derive it from the same collectLiftableLocals result.
        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> liftedLocals =
            collectLiftableLocals(testedMethod, testedMethodCall, generalizableInputs);
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
            // @TODO: How to handle methods (without parameters) that depend on object state?
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
