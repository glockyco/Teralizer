package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.jqwik.VariableConstraintExtractionResult;
import teralizer.jqwik.VariableConstraintExtractor;
import teralizer.jqwik.VariableConstraints;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.generalization.*;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;
import teralizer.util.Configuration;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class TestGeneralizationTask extends AbstractTask {

    public TestGeneralizationTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null);
    }

    public TestGeneralizationTask(ProcessingStage stage, String variant, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
    }

    static Map<String, MethodArgument> mapTestedMethodArguments(
        List<MethodParameter> testedMethodParameters,
        List<MethodArgument> inputValues
    ) {
        // For instance-method assertions, SPF stores the concrete receiver as the first input value;
        // the tested method parameter list contains only declared arguments, so skip that receiver.
        int offset = inputValues.size() == testedMethodParameters.size() + 1 ? 1 : 0;
        if (inputValues.size() - offset != testedMethodParameters.size()) {
            throw new IllegalArgumentException(
                "Cannot map " + inputValues.size() + " concrete values to " + testedMethodParameters.size() + " tested method parameters."
            );
        }

        return IntStream
            .range(0, testedMethodParameters.size())
            .boxed()
            .collect(Collectors.toMap(
                i -> testedMethodParameters.get(i).getName(),
                i -> inputValues.get(i + offset)
            ));
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

        if (records.isEmpty()) {
            throw new RuntimeException(
                "UNEXPECTED: No assertions available for test generalization. " +
                "This should have been caught by JpfAnalysisTask. " +
                "Indicates a pipeline bug or unexpected modification."
            );
        }

        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new TestGeneralizationTask(this.stage, this.variant, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);

        Launcher spoonLauncher = context.get(this.getProjectId(), TaskContext.SPOON_LAUNCHER);

        this.generalizationRecord = this.createGeneralizationRecord(create);
        this.generalizeTest(gson, spoonLauncher);
    }

    private GeneralizationRecord createGeneralizationRecord(DSLContext create) {
        GeneralizationRecord record = create.newRecord(Tables.GENERALIZATION);
        record.setProjectId(this.getProjectId());
        record.setTestId(this.getTestId());
        record.setAssertionId(this.getAssertionId());
        record.setVariant(this.getVariant());
        record.setFilePath("");
        record.setClassQualifiedName("");
        record.setMethodQualifiedName("");
        record.setPackageName("");
        record.setClassName("");
        record.setMethodName("");
        record.setLineCount(0);
        record.setIsIncluded(true);
        record.store();

        String packageName = this.testRecord.getTestPackageName();
        String className = "_" + this.testRecord.getTestClassName() + "_Generalized_" + this.testRecord.getTestMethodName() + "_" + record.getId() + "_Test";
        String methodName = this.testRecord.getTestMethodName();
        Path fileDirectory = Paths.get(this.testRecord.getTestFilePath()).getParent();
        Path filePath = fileDirectory.resolve(className + ".java");

        String qualifiedNamePrefix = packageName.isEmpty() ? "" : (packageName + ".");

        record.setFilePath(filePath.toString());
        record.setClassQualifiedName(qualifiedNamePrefix + className);
        record.setMethodQualifiedName(qualifiedNamePrefix + className + "." + methodName);
        record.setPackageName(packageName);
        record.setClassName(className);
        record.setMethodName(methodName);
        record.store();

        return record;
    }

    private void generalizeTest(Gson gson, Launcher spoonLauncher) throws IOException {
        Factory factory = spoonLauncher.getFactory();

        CtClass<?> generalizedClassDeclaration = SpoonUtils.cloneClass(
            factory,
            factory.Class().get(this.testRecord.getTestClassQualifiedName()),
            this.testRecord.getTestPackageName(),
            this.generalizationRecord.getPackageName(),
            this.testRecord.getTestClassName(),
            this.generalizationRecord.getClassName(),
            this.testRecord.getTestClassQualifiedName(),
            this.generalizationRecord.getClassQualifiedName()
        );

        generalizedClassDeclaration.addComment(factory.createInlineComment("Test: " + this.testRecord.getTestMethodQualifiedName()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Input values: " + this.assertionRecord.getInputValuesPath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Input specification: " + this.assertionRecord.getInputSpecificationPath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Output value: " + this.assertionRecord.getOutputValuePath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Output specification: " + this.assertionRecord.getOutputSpecificationPath()));

        CtPath testMethodPath = new CtPathStringBuilder().fromString(
            this.testRecord.getTestMethodRelativePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.generalizationRecord.getClassQualifiedName()));
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(generalizedClassDeclaration).get(0);

        SpoonUtils.deleteOtherTestMethodsInClass(generalizedClassDeclaration, testMethod);

        for (CtMethod<?> method : generalizedClassDeclaration.getMethods()) {
            List<CtAnnotation<? extends Annotation>> annotations = new ArrayList<>(method.getAnnotations());
            for (CtAnnotation<? extends Annotation> annotation : annotations) {
                String annotationType = annotation.getAnnotationType().getQualifiedName();

                if (annotationType.equals("org.junit.Before") || annotationType.equals("org.junit.jupiter.api.BeforeEach")) {
                    method.removeAnnotation(annotation);
                    CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
                    jqwikAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.BeforeProperty"));
                    method.addAnnotation(jqwikAnnotation);
                }

                if (annotationType.equals("org.junit.After") || annotationType.equals("org.junit.jupiter.api.AfterEach")) {
                    method.removeAnnotation(annotation);
                    CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
                    jqwikAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.AfterProperty"));
                    method.addAnnotation(jqwikAnnotation);
                }

                if (annotationType.equals("org.junit.BeforeClass") || annotationType.equals("org.junit.jupiter.api.BeforeAll")) {
                    method.removeAnnotation(annotation);
                    CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
                    jqwikAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.BeforeContainer"));
                    method.addAnnotation(jqwikAnnotation);
                    method.addModifier(ModifierKind.STATIC);
                }

                if (annotationType.equals("org.junit.AfterClass") || annotationType.equals("org.junit.jupiter.api.AfterAll")) {
                    method.removeAnnotation(annotation);
                    CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
                    jqwikAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.AfterContainer"));
                    method.addAnnotation(jqwikAnnotation);
                    method.addModifier(ModifierKind.STATIC);
                }
            }
        }

        CtPath assertionPath = new CtPathStringBuilder().fromString(
            this.assertionRecord.getAssertionRelativePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.generalizationRecord.getClassQualifiedName()));
        CtInvocation<?> assertion = (CtInvocation<?>) assertionPath.evaluateOn(testMethod).get(0);

        // @TODO: The MethodParameter.type needs to be the FULLY QUALIFIED name of the class.
        //   Otherwise, we will have issues mapping the class names to the correct Arbitraries.
        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.assertionRecord.getTestedMethodParameters(), type);

        String inputValuesString = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getInputValuesPath())));
        Type inputValuesType = new TypeToken<List<MethodArgument>>() {}.getType();
        List<MethodArgument> inputValues = gson.fromJson(inputValuesString, inputValuesType);

        Map<String, MethodArgument> testedMethodArguments = mapTestedMethodArguments(testedMethodParameters, inputValues);

        CtClass<?> testParametersClassDeclaration;
        CtClass<?> testParametersSupplierClassDeclaration;
        Path jqwikValueLogPath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve("jqwik-data")
            .resolve(this.getGeneralizationId() + "." + this.getVariant() + ".tsv");
        String pendingBooleanOutputExpression = null;
        String pendingBooleanOutputOperator = null;

        if (Configuration.getGeneralizationAlgorithm(this.getVariant()) == GeneralizationAlgorithm.BASELINE) {
            List<MethodParameter> allParameters = new ArrayList<>(testedMethodParameters);
            allParameters.removeIf(parameter -> !Configuration.SUPPORTED_TYPES.contains(parameter.getType()));

            testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, allParameters);
            testParametersSupplierClassDeclaration = BaselineTestParametersSupplierFactory.createSupplierClass(factory, allParameters, testedMethodArguments);
        } else {
            String inputSpecification = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getInputSpecificationPath())));
            String outputSpecification = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getOutputSpecificationPath())));

            // @TODO: Check if we can avoid the Model->JSON->Model conversion.
            //   We don't HAVE to store the model as JSON after the JPF execution step. However, if we don't store it,
            //   we cannot re-execute the later steps without also re-executing the JPF execution step. Of course, we
            //   can "simply" skip the JSON reading for any runs that have executed the JPF execution before, but that
            //   then makes it tricky to compare the runtimes of runs WITH vs. WITHOUT reading of JSON files.
            JsonToModelTransformer jsonToModelTransformer = new JsonToModelTransformer();
            Model inputModel = jsonToModelTransformer.transform(inputSpecification);
            Model outputModel = jsonToModelTransformer.transform(outputSpecification);

            Map<String, String> testedMethodParameterTypes = testedMethodParameters.stream().collect(Collectors.toMap(MethodParameter::getName, MethodParameter::getType));
            ModelToJavaTransformer modelToJavaTransformer = new ModelToJavaTransformer(testedMethodParameterTypes);
            String inputJava = modelToJavaTransformer.transform(inputModel);
            String outputJava = modelToJavaTransformer.transform(outputModel);

            // The maximum allowed bytecode size of a Java method is 65535 Bytes.
            // See: https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7, "code_length".
            // Having a method that is larger than this causes a "code too large" compiler error. To ensure we are not
            // generating such incompilable code, we fail the generalization for any cases with a "large" input/output
            // specification. "Large", in this case, is a very rough estimate that is only based on observed cases that
            // have caused compilation errors. A (more) exact estimate is hard to get since there is no straightforward
            // relationship between source code size and bytecode size.
            // @TODO: Use a more reliable approach to check whether "code too large" errors (might) occur.
            //   The most (and only?) reliable solution is probably to actually create the file and try to compile
            //   it => if the error occurs, delete the created file again and mark the generalization as failed.
            boolean isInputJavaTooLarge = inputJava != null && inputJava.length() > Configuration.MAX_SPECIFICATION_SIZE;
            boolean isOutputJavaTooLarge = outputJava != null && outputJava.length() > Configuration.MAX_SPECIFICATION_SIZE;
            if (isInputJavaTooLarge || isOutputJavaTooLarge) {
                throw new RuntimeException("Failing generalization to avoid potential 'code too large' compilation errors.");
            }

            String regex = "\"name\": \"((?>INT|REAL)_[0-9]+)\"";
            Pattern pattern = Pattern.compile(regex);
            Matcher inputMatcher = pattern.matcher(inputSpecification);
            Matcher outputMatcher = pattern.matcher(outputSpecification);

            Set<String> distinctMatches = new HashSet<>();

            while (inputMatcher.find()) {
                String match = inputMatcher.group(1);
                distinctMatches.add(match);
            }

            while (outputMatcher.find()) {
                String match = outputMatcher.group(1);
                distinctMatches.add(match);
            }

            List<MethodParameter> temporaryParameters = distinctMatches.stream().map(m -> new MethodParameter(m.startsWith("INT") ? "int" : "double", m)).collect(Collectors.toList());

            List<MethodParameter> allParameters = new ArrayList<>();
            allParameters.addAll(testedMethodParameters);
            allParameters.addAll(temporaryParameters);
            allParameters.removeIf(parameter -> !Configuration.SUPPORTED_TYPES.contains(parameter.getType()));

            switch (Configuration.getGeneralizationAlgorithm(this.getVariant())) {
                case NAIVE: {
                    testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, allParameters);
                    testParametersSupplierClassDeclaration = NaiveTestParametersSupplierFactory.createSupplierClass(factory, allParameters, testedMethodArguments, inputJava);
                    break;
                }
                case IMPROVED: {
                    VariableConstraintExtractor extractor = new VariableConstraintExtractor();
                    VariableConstraintExtractionResult extractionResult = extractor.process(inputModel, allParameters);
                    Map<String, VariableConstraints> constraints = extractionResult.getConstraints();
                    testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, allParameters);
                    testParametersSupplierClassDeclaration = ImprovedTestParametersSupplierFactory.createSupplierClass(factory, allParameters, testedMethodArguments, constraints, inputJava);

                    this.generalizationRecord.setTotalConstraintCount(extractionResult.getTotalConstraintCount());
                    this.generalizationRecord.setUsedConstraintCount(extractionResult.getUsedConstraintCount());
                    this.generalizationRecord.store();
                    break;
                }
                default:
                    throw new RuntimeException("Unsupported variant " + this.getVariant() + ".");
            }

            // ------------------------------------------------------------------------------------------------------ //
            // Replace expected values in asserts with generalized values.                                            //
            // ------------------------------------------------------------------------------------------------------ //

            String outputValueString = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getOutputValuePath())));
            MethodArgument outputValue = gson.fromJson(outputValueString, MethodArgument.class);

            if (outputJava != null) {
                boolean isBooleanOutput = outputValue.getType().equals("boolean") || outputValue.getType().equals("java.lang.Boolean");
                String expectedExpression = isBooleanOutput
                    ? "((" + outputJava + ") != 0)"
                    : "(" + outputValue.getType() + ") (" + outputJava + ")";

                Optional<Integer> expectedParameterIndex = TestAnalysis.getExpectedParameterIndex(assertion);
                if (expectedParameterIndex.isPresent()) {
                    List<CtExpression<?>> assertArguments = assertion.getArguments();
                    assertArguments.set(expectedParameterIndex.get(), factory.Code().createCodeSnippetExpression(expectedExpression));
                } else if (isBooleanOutput && TestAnalysis.getActualParameterIndex(assertion).isPresent()) {
                    pendingBooleanOutputExpression = expectedExpression;
                    pendingBooleanOutputOperator = assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_FALSE) ? "!=" : "==";
                }
            }

            // ------------------------------------------------------------------------------------------------------ //
            // Add FirstValueArbitrary class.                                                                         //
            // ------------------------------------------------------------------------------------------------------ //

            CtClass<?> firstValueArbitraryClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass();
            generalizedClassDeclaration.addNestedType(firstValueArbitraryClass);
        }

        CtClass<?> jqwikValueRecorderClass = JqwikValueRecorderFactory.createRecorderClass(factory, jqwikValueLogPath);
        generalizedClassDeclaration.addNestedType(testParametersClassDeclaration);
        generalizedClassDeclaration.addNestedType(testParametersSupplierClassDeclaration);
        generalizedClassDeclaration.addNestedType(jqwikValueRecorderClass);

        // ------------------------------------------------------------------------------------------------------ //
        // Remove all existing annotations.                                                                       //
        // ------------------------------------------------------------------------------------------------------ //

        List<CtAnnotation<?>> testMethodAnnotations = new ArrayList<>(testMethod.getAnnotations());
        testMethodAnnotations.forEach(testMethod::removeAnnotation);

        // ------------------------------------------------------------------------------------------------------ //
        // Add a jqwik @Property annotation.                                                                      //
        // ------------------------------------------------------------------------------------------------------ //

        CtAnnotation<Annotation> propertyAnnotation = factory.Core().createAnnotation();
        propertyAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.Property"));
        propertyAnnotation.addValue("tries", factory.Code().createLiteral(Configuration.getGeneralizationJqwikTries(this.getVariant())));
        propertyAnnotation.addValue("seed", factory.Code().createLiteral("0"));
        propertyAnnotation.addValue("shrinking", factory.Code().createCodeSnippetExpression("net.jqwik.api.ShrinkingMode.OFF"));
        propertyAnnotation.addValue("edgeCases", factory.Code().createCodeSnippetExpression("net.jqwik.api.EdgeCasesMode.FIRST"));
        testMethod.addAnnotation(propertyAnnotation);

        // ------------------------------------------------------------------------------------------------------ //
        // Add `@ForAll(...) TestParameters testParameters` to the test method signature.                         //
        // ------------------------------------------------------------------------------------------------------ //

        CtAnnotation<?> forAllAnnotation = factory.Core().createAnnotation();
        forAllAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.ForAll"));
        forAllAnnotation.addValue("supplier", factory.Code().createClassAccess(factory.Type().createReference("TestParametersSupplier")));
        //forAllAnnotation.addValue("supplier", factory.Code().createCodeSnippetExpression("TestParametersSupplier.class"));

        CtParameter<Object> parameter = factory.Core().createParameter();
        CtTypeReference<Object> parameterType = factory.Type().createReference("TestParameters");
        parameter.setType(parameterType);
        parameter.setSimpleName("_p_");
        parameter.addAnnotation(forAllAnnotation);

        testMethod.addParameter(parameter);

        // ------------------------------------------------------------------------------------------------------ //
        // Replace tested method arguments with values from `testParameters`.                                     //
        // ------------------------------------------------------------------------------------------------------ //

        CtPath testedMethodCallPath = new CtPathStringBuilder().fromString(
            this.assertionRecord.getTestedMethodCallRelativePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.generalizationRecord.getClassQualifiedName()));
        CtInvocation<?> testedMethodCall = (CtInvocation<?>) testedMethodCallPath.evaluateOn(testMethod).get(0);

        CtPath testedMethodPath = new CtPathStringBuilder().fromString(
            this.assertionRecord.getTestedMethodAbsolutePath().replace(
                this.testRecord.getTestClassQualifiedName(),
                this.generalizationRecord.getClassQualifiedName()));
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodPath.evaluateOn(factory.getModel().getRootPackage()).get(0);

        List<CtExpression<?>> args = testedMethodCall.getArguments();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, testedMethodCall);

        for (int i = 0; i < args.size(); i++) {
            final int argumentIndex = i;
            List<GeneralizableInput> inputsForArgument = inputs.stream()
                .filter(input -> input.getMethodArgumentIndex() == argumentIndex)
                .collect(Collectors.toList());
            if (inputsForArgument.isEmpty()) {
                continue;
            }

            if (inputsForArgument.get(0).isConstructorArgument()) {
                CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) args.get(i);
                List<CtExpression<?>> constructorArguments = new ArrayList<>(constructorCall.getArguments());
                for (GeneralizableInput input : inputsForArgument) {
                    constructorArguments.set(
                        input.getConstructorArgumentIndex(),
                        factory.Code().createCodeSnippetExpression("_p_." + input.toMethodParameter().getName())
                    );
                }
                constructorCall.setArguments(constructorArguments);
            } else {
                args.set(i, factory.Code().createCodeSnippetExpression("_p_." + inputsForArgument.get(0).toMethodParameter().getName()));
            }
        }

        testMethod.getBody().insertBegin(factory.Code().createCodeSnippetStatement("JqwikValueRecorder.record(_p_)"));

        if (pendingBooleanOutputExpression != null) {
            List<CtExpression<?>> assertArguments = assertion.getArguments();
            int index = TestAnalysis.getActualParameterIndex(assertion).get();
            CtExpression<?> actualArgument = assertArguments.get(index);
            assertArguments.set(index, factory.Code().createCodeSnippetExpression("(" + actualArgument + ") " + pendingBooleanOutputOperator + " " + pendingBooleanOutputExpression));
        }

        // ------------------------------------------------------------------------------------------------------ //
        // Remove other assertions in the test method.                                                            //
        // ------------------------------------------------------------------------------------------------------ //

        SpoonUtils.deleteOtherAssertionsInMethod(testMethod, assertion);

        // ------------------------------------------------------------------------------------------------------ //

        CtCompilationUnit cu = spoonLauncher.getFactory().CompilationUnit().getOrCreate(this.generalizationRecord.getFilePath());
        cu.setImports(generalizedClassDeclaration.getPosition().getCompilationUnit().getImports().stream().map(CtImport::clone).collect(Collectors.toList()));
        cu.setDeclaredTypes(Collections.singletonList(generalizedClassDeclaration));

        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(spoonLauncher.getEnvironment());
        printer.setIgnoreImplicit(false);
        printer.calculate(cu, Collections.singletonList(generalizedClassDeclaration));
        byte[] generalizedFileBytes = printer.getResult().getBytes();

        Path generalizedFilePath = Paths.get(this.generalizationRecord.getFilePath());

        // Write the generalized file to the project directory for further use in this run:
        generalizedFilePath.toFile().getParentFile().mkdirs();
        Files.write(generalizedFilePath, generalizedFileBytes);

        try (Stream<String> lines = Files.lines(generalizedFilePath)) {
            this.generalizationRecord.setLineCount((int) lines.count());
            this.generalizationRecord.store();
        }

        // Copy the generalized file to the data directory for cross-run storage:
        Path relativizedFilePath = this.projectRecord.getTestSourcePath().relativize(generalizedFilePath);
        Path dataFilePath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve(Configuration.TOOL_NAME.toLowerCase() + "-data")
            .resolve("tests")
            .resolve(this.getVariant())
            .resolve(relativizedFilePath);
        dataFilePath.getParent().toFile().mkdirs();
        Files.copy(generalizedFilePath, dataFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
}
