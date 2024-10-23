package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestGeneralizationTask implements Task {

    private static final int MAX_TRIES_JQWIK = 20;
    private static final int MAX_SPECIFICATION_SIZE = 200000;

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final TestRecord testRecord;
    private final String tool;

    private GeneralizationRecord generalizationRecord;

    public static String TEST_PARAMETERS_CLASS_NAME = "TestParameters";
    public static String TEST_PARAMETERS_SUPPLIER_CLASS_NAME = "TestParametersSupplier";

    public TestGeneralizationTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, String tool) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.tool = tool;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);

        this.generalizationRecord = this.createGeneralizationRecord(create);
        this.generalizeTest(gson, javaParser, velocityEngine);

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_GENERALIZED, this.projectRecord));
        scheduleTask.accept(new TestExecutionTask(ProcessingStage.TEST_EXECUTION_GENERALIZED, this.projectRecord));
        scheduleTask.accept(new CoverageDataCollectionTask(ProcessingStage.COVERAGE_DATA_COLLECTION_GENERALIZED, this.projectRecord, this.tool));
        scheduleTask.accept(new MutationDataCollectionTask(ProcessingStage.MUTATION_DATA_COLLECTION_GENERALIZED, this.projectRecord, this.tool));
        scheduleTask.accept(new TestDataCollectionTask(ProcessingStage.TEST_DATA_COLLECTION_GENERALIZED, this.projectRecord, this.testRecord, this.generalizationRecord));
    }

    private GeneralizationRecord createGeneralizationRecord(DSLContext create) {
        GeneralizationRecord generalizationRecord = create.newRecord(Tables.GENERALIZATION);
        generalizationRecord.setTestId(this.testRecord.getId());
        generalizationRecord.setTool(this.tool);
        generalizationRecord.setGeneralizedClassPath("");
        generalizationRecord.setGeneralizedClassPackage("");
        generalizationRecord.setGeneralizedClassName("");
        generalizationRecord.store();

        String generalizedClassName = "_" + this.testRecord.getTestClassName() + "_Generalized_" + this.testRecord.getTestMethodName() + "_" + generalizationRecord.getId() + "_Test";
        Path generalizedClassDirectory = Paths.get(this.testRecord.getTestClassPath()).getParent().resolve(Paths.get(TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated", this.tool));
        Path generalizedClassPath = generalizedClassDirectory.resolve(generalizedClassName + ".java");

        generalizationRecord.setGeneralizedClassPath(generalizedClassPath.toString());
        generalizationRecord.setGeneralizedClassPackage(this.testRecord.getTestClassPackage() + "." + TestGeneralizationRunner.TOOL_NAME.toLowerCase() + "_generated." + this.tool);
        generalizationRecord.setGeneralizedClassName(generalizedClassName);
        generalizationRecord.store();

        return generalizationRecord;
    }

    private void generalizeTest(Gson gson, JavaParser javaParser, VelocityEngine velocityEngine) throws IOException {
        CompilationUnit compilationUnit = javaParser.parse(Paths.get(this.testRecord.getTestClassPath())).getResult().get();

        compilationUnit.setPackageDeclaration(this.generalizationRecord.getGeneralizedClassPackage());
        compilationUnit.addImport(this.testRecord.getTestClassPackage() + ".*");

        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(this.testRecord.getTestClassName()).get();
        testClassDeclaration.setName(this.generalizationRecord.getGeneralizedClassName());

        List<AnnotationExpr> evoSuiteAnnotations = new ArrayList<>();
        for (AnnotationExpr annotation : testClassDeclaration.getAnnotations()) {
            if (annotation.toString().equals("@RunWith(EvoRunner.class)") || annotation.toString().startsWith("@EvoRunnerParameters")) {
                evoSuiteAnnotations.add(annotation);
            }
        }
        if (!evoSuiteAnnotations.isEmpty()) {
            testClassDeclaration.setExtendedTypes(new NodeList<>());
        }
        for (AnnotationExpr annotation : evoSuiteAnnotations) {
            annotation.remove();
        }

        testClassDeclaration.getAllContainedComments().forEach(Comment::remove);

        Predicate<MethodDeclaration> isTestMethod = (decl) -> decl.getNameAsString().equals(this.testRecord.getTestMethodName());
        Predicate<MethodDeclaration> hasTestAnnotation = (decl) -> decl.isAnnotationPresent("Test");

        for (MethodDeclaration testMethodDeclaration : compilationUnit.findAll(MethodDeclaration.class)) {
            // Remove other @Test methods. Each one gets their own generalized test class.
            // All other methods should be kept in case they are required by the test method.
            if (!isTestMethod.test(testMethodDeclaration)) {
                if (hasTestAnnotation.test(testMethodDeclaration)) {
                    testMethodDeclaration.remove();
                }
                continue;
            }

            // @TODO: The MethodParameter.type needs to be the FULLY QUALIFIED name of the class.
            //   Otherwise, we will have issues mapping the class names to the correct Arbitraries.
            Type type = new TypeToken<List<MethodParameter>>() {}.getType();
            List<MethodParameter> testedMethodParameters = gson.fromJson(this.testRecord.getTestedMethodParamTypes(), type);

            String inputSpecification = new String(Files.readAllBytes(Paths.get(this.testRecord.getInputSpecificationPath())));
            String outputSpecification = new String(Files.readAllBytes(Paths.get(this.testRecord.getOutputSpecificationPath())));

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

            VelocityContext context = new VelocityContext();
            context.put("testParametersSupplierClassName", TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
            context.put("testParametersClassName", TEST_PARAMETERS_CLASS_NAME);
            context.put("methodParameters", allParameters);
            context.put("precondition", inputJava);

            StringWriter stringWriter = new StringWriter();
            Template template = velocityEngine.getTemplate("test-parameters-classes.vm");
            template.merge(context, stringWriter);

            CompilationUnit cu = javaParser.parse(stringWriter.toString()).getResult().get();
            ClassOrInterfaceDeclaration testParametersClassDeclaration = cu.getClassByName(TEST_PARAMETERS_CLASS_NAME).get();
            ClassOrInterfaceDeclaration testParametersSupplierClassDeclaration = cu.getClassByName(TEST_PARAMETERS_SUPPLIER_CLASS_NAME).get();

            testClassDeclaration.addMember(testParametersClassDeclaration);
            testClassDeclaration.addMember(testParametersSupplierClassDeclaration);

            // @TODO: Evaluate TestParameters generation with:
            //   - "naive" approach (filtering),
            //   - "manually improved" approach,
            //   - junit-quickcheck-based generation,
            //   - z3-simplified path conditions,
            //   - full z3-based generation.
            //   We could also evaluate fuzzer-based generation etc.,
            //   but every additional approach increases implementation effort.
            //   ---
            //   Naive approach:
            //   Arbitrary<Integer> ints = Arbitraries.integers();
            //   return Combinators.combine(ints, ints).as(TestParameters::new)
            //       .filter(testParameters -> testParameters.x < testParameters.y);
            //   ---
            //   Manually improved approach:
            //   return Arbitraries.integers().filter(x -> x < Integer.MAX_VALUE).flatMap(x ->
            //       // We need the filter above to prevent "x + 1" below from overflowing.
            //       Arbitraries.integers().between(x + 1, Integer.MAX_VALUE)
            //           .map(y -> new TestParameters(x, y)));
            //   ---
            //   The "manually improved" approach might quickly run into issues
            //   due to, e.g., cyclic dependencies between the variables.
            //   a >= b && b >= a
            //   a > b & b > c & c < a

            // ------------------------------------------------------------------------------------------------------ //
            // Replace JUnit @Test annotations with jqwik @Property annotations.                                      //
            // ------------------------------------------------------------------------------------------------------ //

            testMethodDeclaration.getAnnotationByName("Test").ifPresent(annotation -> {
                testMethodDeclaration.remove(annotation);
                testMethodDeclaration.addAnnotation(
                    new NormalAnnotationExpr(new Name(net.jqwik.api.Property.class.getName()),
                    new NodeList<>(new MemberValuePair("tries", new IntegerLiteralExpr(Integer.toString(MAX_TRIES_JQWIK)))))
                );
            });

            // ------------------------------------------------------------------------------------------------------ //
            // Add `@ForAll(...) TestParameters testParameters` to the test method signature.                         //
            // ------------------------------------------------------------------------------------------------------ //

            ClassOrInterfaceType testParametersClassType = new ClassOrInterfaceType(null, testParametersClassDeclaration.getNameAsString());
            ClassOrInterfaceType testParametersSupplierClassType = new ClassOrInterfaceType(null, testParametersSupplierClassDeclaration.getNameAsString());

            Parameter testParametersParameter = new Parameter(testParametersClassType, "testParameters");
            NormalAnnotationExpr forAllAnnotation = new NormalAnnotationExpr(new Name(net.jqwik.api.ForAll.class.getName()), new NodeList<>(new MemberValuePair("supplier", new ClassExpr(testParametersSupplierClassType))));
            testParametersParameter.addAnnotation(forAllAnnotation);

            testMethodDeclaration.addParameter(testParametersParameter);

            // ------------------------------------------------------------------------------------------------------ //
            // Replace tested method arguments with values from `testParameters`.                                     //
            // ------------------------------------------------------------------------------------------------------ //

            MethodCallExpr testedMethodCall = TestDetectionTask.findTestedMethodCall(testMethodDeclaration);

            for (int i = 0; i < testedMethodParameters.size(); i++) {
                // @TODO: Add support for non-int/-double types.
                MethodParameter methodParameter = testedMethodParameters.get(i);
                if (methodParameter.getType().equals("int") || methodParameter.getType().equals("double")) {
                    testedMethodCall.getArguments().set(i, new FieldAccessExpr(new NameExpr(testParametersParameter.getNameAsString()), methodParameter.getName()));
                }
            }

            // ------------------------------------------------------------------------------------------------------ //
            // Replace expected values in asserts with generalized values.                                            //
            // ------------------------------------------------------------------------------------------------------ //

            // @TODO: Remove all existing assertions.
            //   For now, generalization seems to be too much effort.
            //   There are more important things to take care of right now.
            // @TODO: Analyze which / how many assertions need generalization.
            //   This could be done by, for example, generating multiple variants of the generalized test classes / methods,
            //   each one with just a single assertion preserved, and seeing whether the assertion passes or fails.
            // @TODO: Add generalization for existing assertions.
            //   For assertions that fail, the oracle has to be modified in some way.
            //   It might be possible to (partially) automate this oracle generalization.

            if (outputJava != null) {
                MethodCallExpr assertEqualsCall = TestDetectionTask.findAssertEqualsCall(testMethodDeclaration);
                assertEqualsCall.getArguments().set(0, javaParser.parseExpression(outputJava).getResult().get());
            }

            // ------------------------------------------------------------------------------------------------------ //
            // Remove parts of the test setup code that are no longer needed after generalization.                    //
            // ------------------------------------------------------------------------------------------------------ //

            // @TODO: Remove setup code that is not needed anymore after generalization.
        }

        Paths.get(this.generalizationRecord.getGeneralizedClassPath()).toFile().getParentFile().mkdirs();
        Files.write(Paths.get(this.generalizationRecord.getGeneralizedClassPath()), compilationUnit.toString().getBytes());
    }

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return this.testRecord.getId();
    }

    @Override
    public Integer getGeneralizationId() {
        return this.generalizationRecord == null ? null : this.generalizationRecord.getId();
    }

    @Override
    public String toString() {
        Integer generalizationRecordId = this.generalizationRecord == null ? null : this.generalizationRecord.getId();
        return "TestGeneralizationTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", testRecord=" + this.testRecord.getId() +
            ", tool='" + this.tool + '\'' +
            ", generalizationRecord=" + generalizationRecordId +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestGeneralizationTask)) return false;
        TestGeneralizationTask that = (TestGeneralizationTask) o;
        Integer thisGeneralizationRecordId = this.generalizationRecord == null ? null : this.generalizationRecord.getId();
        Integer thatGeneralizationRecordId = that.generalizationRecord == null ? null : that.generalizationRecord.getId();
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.testRecord.getId(), that.testRecord.getId()) && Objects.equals(this.tool, that.tool) && Objects.equals(thisGeneralizationRecordId, thatGeneralizationRecordId);
    }

    @Override
    public int hashCode() {
        Integer generalizationRecordId = this.generalizationRecord == null ? null : this.generalizationRecord.getId();
        return Objects.hash(this.stage, this.projectRecord.getId(), this.testRecord.getId(), this.tool, generalizationRecordId);
    }
}
