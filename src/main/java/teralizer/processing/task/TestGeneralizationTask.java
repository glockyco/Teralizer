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
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.jqwik.VariableConstraintExtractor;
import teralizer.jqwik.VariableConstraintExtractor.VariableConstraints;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.generalization.ImprovedTestParametersSupplierFactory;
import teralizer.spoon.generalization.NaiveTestParametersSupplierFactory;
import teralizer.spoon.generalization.TestParametersFactory;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestGeneralizationTask extends AbstractTask {

    private static final int MAX_TRIES_JQWIK = 20;
    private static final int MAX_SPECIFICATION_SIZE = 200000;

    public static String TEST_PARAMETERS_CLASS_NAME = "TestParameters";
    public static String TEST_PARAMETERS_SUPPLIER_CLASS_NAME = "TestParametersSupplier";

    public static final List<String> SUPPORTED_TYPES = Arrays.asList("byte", "short", "int", "long", "float", "double");

    public TestGeneralizationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null, null);
    }

    public TestGeneralizationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.variant = variant;
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
        Factory factory =  spoonLauncher.getFactory();
        CtClass<?> testClassDeclaration = factory.Class().get(this.testRecord.getTestClassQualifiedName());

        CtClass<?> generalizedClassDeclaration = testClassDeclaration.clone();
        generalizedClassDeclaration.setSimpleName(this.generalizationRecord.getClassName());

        CtPackage generalizedClassPackage = generalizedClassDeclaration.getFactory().Package().getOrCreate(this.generalizationRecord.getPackageName());
        generalizedClassPackage.addType(generalizedClassDeclaration);

        List<CtAnnotation<?>> evoSuiteAnnotations = generalizedClassDeclaration.getAnnotations().stream()
            .filter(a -> a.toString().equals("@RunWith(EvoRunner.class)") || a.toString().startsWith("@EvoRunnerParameters"))
            .collect(Collectors.toList());
        evoSuiteAnnotations.forEach(generalizedClassDeclaration::removeAnnotation);

        if (!evoSuiteAnnotations.isEmpty()) {
            generalizedClassDeclaration.setSuperclass(null);
        }

        generalizedClassDeclaration.addComment(factory.createInlineComment("Test: " + this.testRecord.getTestMethodQualifiedName()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Input specification: " + this.assertionRecord.getInputSpecificationPath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Output specification: " + this.assertionRecord.getOutputSpecificationPath()));

        Predicate<CtMethod<?>> isTestMethod = (decl) -> decl.getSimpleName().equals(this.testRecord.getTestMethodName());
        Predicate<CtMethod<?>> hasTestAnnotation = (decl) -> decl.getAnnotations().stream().anyMatch(a -> a.getType().getSimpleName().equals("Test"));
        List<CtMethod<?>> otherTestMethods = generalizedClassDeclaration.getMethods().stream().filter(m -> !isTestMethod.test(m) && hasTestAnnotation.test(m)).collect(Collectors.toList());
        otherTestMethods.forEach(generalizedClassDeclaration::removeMethod);

        CtMethod<?> testMethod = generalizedClassDeclaration.getMethodsByName(this.testRecord.getTestMethodName()).get(0);

        // @TODO: The MethodParameter.type needs to be the FULLY QUALIFIED name of the class.
        //   Otherwise, we will have issues mapping the class names to the correct Arbitraries.
        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.assertionRecord.getTestedMethodParameters(), type);

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

        ModelToJavaTransformer modelToJavaTransformer = new ModelToJavaTransformer();
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
        boolean isInputJavaTooLarge = inputJava != null && inputJava.length() > MAX_SPECIFICATION_SIZE;
        boolean isOutputJavaTooLarge = outputJava != null && outputJava.length() > MAX_SPECIFICATION_SIZE;
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

        // Remove parameter types that cannot be generalized
        allParameters = allParameters.stream().filter(p -> {
            try {
                SpoonUtils.getTypeReference(factory, p.getType());
            } catch (NullPointerException e) {
                return false;
            }
            return true;
        }).collect(Collectors.toList());

        CtClass<?> testParametersClassDeclaration;
        CtClass<?> testParametersSupplierClassDeclaration;

        switch (this.getVariant()) {
            case NAIVE: {
                testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, allParameters);
                testParametersSupplierClassDeclaration = NaiveTestParametersSupplierFactory.createSupplierClass(factory, allParameters, inputJava);
                break;
            }
            case IMPROVED: {
                VariableConstraintExtractor extractor = new VariableConstraintExtractor();
                Map<String, VariableConstraints> constraints = extractor.process(inputModel, allParameters);

                testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, allParameters);
                testParametersSupplierClassDeclaration = ImprovedTestParametersSupplierFactory.createSupplierClass(factory, allParameters, constraints, inputJava);
                break;
            }
            default:
                throw new RuntimeException("Unsupported variant " + this.getVariant() + ".");
        }

        Set<CtType<?>> nestedTypes = generalizedClassDeclaration.getNestedTypes();
        nestedTypes.forEach(CtElement::delete);

        generalizedClassDeclaration.addNestedType(testParametersClassDeclaration);
        generalizedClassDeclaration.addNestedType(testParametersSupplierClassDeclaration);

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
        propertyAnnotation.addValue("tries", factory.Code().createLiteral(MAX_TRIES_JQWIK));
        propertyAnnotation.addValue("seed", factory.Code().createLiteral("0"));
        propertyAnnotation.addValue("shrinking", factory.Code().createCodeSnippetExpression("net.jqwik.api.ShrinkingMode.OFF"));
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

        CtPathStringBuilder pathBuilder = new CtPathStringBuilder();
        CtPath assertionPath = pathBuilder.fromString(this.assertionRecord.getAssertionRelativePath());
        CtPath testedMethodCallPath = pathBuilder.fromString(this.assertionRecord.getTestedMethodCallRelativePath());

        CtInvocation<?> assertion = (CtInvocation<?>) assertionPath.evaluateOn(testMethod).get(0);
        CtInvocation<?> testedMethodCall = (CtInvocation<?>) testedMethodCallPath.evaluateOn(testMethod).get(0);
        CtMethod<?> testedMethod = (CtMethod<?>) testedMethodCall.getExecutable().getDeclaration();

        List<CtExpression<?>> args = testedMethodCall.getArguments();
        List<CtParameter<?>> params = testedMethod.getParameters();

        for (int i = 0; i < args.size(); i++) {
            CtExpression<?> arg = args.get(i);
            CtParameter<?> param = params.get(i);
            if (SUPPORTED_TYPES.contains(arg.getType().getSimpleName())) {
                args.set(i, factory.Code().createCodeSnippetExpression("_p_." + param.getSimpleName()));
            }
        }

        // ------------------------------------------------------------------------------------------------------ //
        // Replace expected values in asserts with generalized values.                                            //
        // ------------------------------------------------------------------------------------------------------ //

        if (outputJava != null) {
            int index = TestAnalysis.getExpectedParameterIndex(assertion).get();
            List<CtExpression<?>> assertArguments = assertion.getArguments();
            assertArguments.set(index, factory.Code().createCodeSnippetExpression(outputJava));
        }

        // ------------------------------------------------------------------------------------------------------ //
        // Remove parts of the test code that are no longer needed after generalization.                          //
        // ------------------------------------------------------------------------------------------------------ //

        List<CtInvocation> otherAssertions = testMethod.getElements(new TypeFilter<>(CtInvocation.class)).stream().filter(i -> i != assertion && (TestAnalysis.isJUnit4Assertion(i) || TestAnalysis.isJUnit5Assertion(i))).collect(Collectors.toList());
        otherAssertions.forEach(CtElement::delete);

        // ------------------------------------------------------------------------------------------------------ //

        Path generalizedFilePath = Paths.get(this.generalizationRecord.getFilePath());
        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(spoonLauncher.getEnvironment());
        printer.calculate(generalizedClassDeclaration.getPosition().getCompilationUnit(), Collections.singletonList(generalizedClassDeclaration));
        byte[] generalizedFileBytes = printer.getResult().getBytes();

        // Write the generalized file to the project directory for further use in this run:
        generalizedFilePath.toFile().getParentFile().mkdirs();
        Files.write(generalizedFilePath, generalizedFileBytes);

        // Copy the generalized file to the data directory for cross-run storage:
        Path relativizedFilePath = this.projectRecord.getTestSourcePath().relativize(generalizedFilePath);
        Path dataFilePath = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve(TestGeneralizationRunner.TOOL_NAME.toLowerCase() +"-data")
            .resolve("tests")
            .resolve(this.getVariant().toString())
            .resolve(relativizedFilePath);
        dataFilePath.getParent().toFile().mkdirs();
        Files.copy(generalizedFilePath, dataFilePath, StandardCopyOption.REPLACE_EXISTING);
    }
}
