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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class TestGeneralizationTask implements Task {

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
    }


    private GeneralizationRecord createGeneralizationRecord(DSLContext create) {
        GeneralizationRecord generalizationRecord = create.newRecord(Tables.GENERALIZATION);
        generalizationRecord.setTestId(this.testRecord.getId());
        generalizationRecord.setTool(this.tool);

        String generalizedClassName = "_" + this.testRecord.getTestClassName() + "_Generalized_" + this.testRecord.getTestMethodName();
        Path generalizedClasspath = Paths.get(this.testRecord.getTestClassPath()).getParent().resolve(Paths.get("teralizer", this.tool, generalizedClassName + ".java"));

        generalizationRecord.setGeneralizedClassPath(generalizedClasspath.toString());
        generalizationRecord.setGeneralizedClassPackage(this.testRecord.getTestClassPackage() + ".teralizer." + this.tool);
        generalizationRecord.setGeneralizedClassName(generalizedClassName);

        generalizationRecord.store();

        return generalizationRecord;
    }

    private void generalizeTest(Gson gson, JavaParser javaParser, VelocityEngine velocityEngine) throws IOException {
        CompilationUnit compilationUnit = javaParser.parse(Paths.get(this.testRecord.getTestClassPath())).getResult().get();

        compilationUnit.setPackageDeclaration(this.generalizationRecord.getGeneralizedClassPackage());

        compilationUnit.addImport(this.testRecord.getTestClassPackage() + ".*");

        // @TODO: Read these additional imports from the Velocity templates.
        compilationUnit.addImport(net.jqwik.api.Arbitraries.class);
        compilationUnit.addImport(net.jqwik.api.Arbitrary.class);
        compilationUnit.addImport(net.jqwik.api.ArbitrarySupplier.class);
        compilationUnit.addImport(net.jqwik.api.Combinators.class);
        compilationUnit.addImport(net.jqwik.api.ForAll.class);

        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(this.testRecord.getTestClassName()).get();

        if (testClassDeclaration.getExtendedTypes().isNonEmpty()) {
            // @TODO: Add generalization support for classes that implement interfaces or extend other (abstract) classes.
            //   ---
            //   Test classes that implement interfaces or extend other (abstract) classes might cause
            //   problems because the implementation of the generalization task does not currently include
            //   all the code of the original test class in the generalized class.
            //   ---
            //   Consequently, implementations for abstract methods might be missing in the generalized
            //   class, thus causing build failures. Similarly, overrides of non-abstract parent methods
            //   might be missing, thus causing unintended behavior.
            String qualifiedTestClassName = this.testRecord.getTestClassPackage() + "." + this.testRecord.getTestClassName();
            throw new RuntimeException("Cannot safely generalize class " + qualifiedTestClassName + " because it extends / implements " + testClassDeclaration.getExtendedTypes() + ".");
        }

        testClassDeclaration.setName(this.generalizationRecord.getGeneralizedClassName());

        testClassDeclaration.getAllContainedComments().forEach(Comment::remove);

        for (MethodDeclaration testMethodDeclaration : compilationUnit.findAll(MethodDeclaration.class)) {
            if (!testMethodDeclaration.getNameAsString().equals(this.testRecord.getTestMethodName())) {
                testMethodDeclaration.remove();
                continue;
            }

            // @TODO: The MethodParameter.type needs to be the FULLY QUALIFIED name of the class.
            //   Otherwise, we will have issues mapping the class names to the correct Arbitraries.
            Type type = new TypeToken<List<MethodParameter>>() {
            }.getType();
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

            VelocityContext context = new VelocityContext();
            context.put("testParametersSupplierClassName", TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
            context.put("testParametersClassName", TEST_PARAMETERS_CLASS_NAME);
            context.put("methodParameters", testedMethodParameters);
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

            testMethodDeclaration
                .getAnnotationByName("Test")
                .ifPresent(a -> a.setName(net.jqwik.api.Property.class.getName()));

            // ------------------------------------------------------------------------------------------------------ //
            // Add `@ForAll(...) TestParameters testParameters` to the test method signature.                         //
            // ------------------------------------------------------------------------------------------------------ //

            ClassOrInterfaceType testParametersClassType = new ClassOrInterfaceType(null, testParametersClassDeclaration.getNameAsString());
            ClassOrInterfaceType testParametersSupplierClassType = new ClassOrInterfaceType(null, testParametersSupplierClassDeclaration.getNameAsString());

            Parameter testParametersParameter = new Parameter(testParametersClassType, "testParameters");
            NormalAnnotationExpr forAllAnnotation = new NormalAnnotationExpr(new Name(net.jqwik.api.ForAll.class.getSimpleName()), new NodeList<>(new MemberValuePair("supplier", new ClassExpr(testParametersSupplierClassType))));
            testParametersParameter.addAnnotation(forAllAnnotation);

            testMethodDeclaration.addParameter(testParametersParameter);

            // ------------------------------------------------------------------------------------------------------ //
            // Replace tested method arguments with values from `testParameters`.                                     //
            // ------------------------------------------------------------------------------------------------------ //

            MethodCallExpr testedMethodCall = TestDetectionTask.findTestedMethodCall(testMethodDeclaration);

            for (int i = 0; i < testedMethodParameters.size(); i++) {
                // @TODO: Add support for non-int types.
                MethodParameter methodParameter = testedMethodParameters.get(i);
                if (methodParameter.getType().equals("int")) {
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
