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
import org.jooq.generated.tables.records.TestRecord;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class TestGeneralizationTask extends AbstractTask {

    private final DSLContext create;
    private final VelocityEngine velocityEngine;
    private final JavaParser javaParser;
    private final Gson gson;

    public static String TEST_PARAMETERS_CLASS_NAME = "TestParameters";
    public static String TEST_PARAMETERS_SUPPLIER_CLASS_NAME = "TestParametersSupplier";

    public TestGeneralizationTask(DSLContext create, VelocityEngine velocityEngine, JavaParser javaParser, Gson gson) {
        this.create = create;
        this.velocityEngine = velocityEngine;
        this.javaParser = javaParser;
        this.gson = gson;
    }

    public TaskCallable<Void> create(TestRecord testRecord, String tool) throws IOException {
        this.setProjectId(testRecord.getProjectId());
        this.setTestId(testRecord.getId());

        return new TaskCallable<>(this, () -> {
            GeneralizationRecord generalizationRecord = this.createGeneralizationRecord(testRecord, tool);
            this.setGeneralizationId(generalizationRecord.getId());
            this.generalizeTest(testRecord, generalizationRecord);
            return null;
        });
    }

    private GeneralizationRecord createGeneralizationRecord(TestRecord testRecord, String tool) {
        GeneralizationRecord generalizationRecord = this.create.newRecord(Tables.GENERALIZATION);
        generalizationRecord.setTestId(testRecord.getId());
        generalizationRecord.setTool(tool);

        String generalizedClassName = "_" + testRecord.getTestClassName() + "_Generalized_" + testRecord.getTestMethodName();
        Path generalizedClasspath = Paths.get(testRecord.getTestClassPath()).getParent().resolve(Paths.get("teralizer", tool, generalizedClassName + ".java"));

        generalizationRecord.setGeneralizedClassPath(generalizedClasspath.toAbsolutePath().toString());
        generalizationRecord.setGeneralizedClassPackage(testRecord.getTestClassPackage() + ".teralizer." + tool);
        generalizationRecord.setGeneralizedClassName(generalizedClassName);

        generalizationRecord.store();

        return generalizationRecord;
    }

    private void generalizeTest(TestRecord testRecord, GeneralizationRecord generalizationRecord) throws IOException {
        CompilationUnit compilationUnit = this.javaParser.parse(Paths.get(testRecord.getTestClassPath())).getResult().get();

        compilationUnit.setPackageDeclaration(generalizationRecord.getGeneralizedClassPackage());

        compilationUnit.addImport(testRecord.getTestedClassPackage() + "." + testRecord.getTestedClassName());

        // @TODO: Read these additional imports from the Velocity templates.
        compilationUnit.addImport(net.jqwik.api.Arbitraries.class);
        compilationUnit.addImport(net.jqwik.api.Arbitrary.class);
        compilationUnit.addImport(net.jqwik.api.ArbitrarySupplier.class);
        compilationUnit.addImport(net.jqwik.api.Combinators.class);
        compilationUnit.addImport(net.jqwik.api.ForAll.class);

        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(testRecord.getTestClassName()).get();
        testClassDeclaration.setName(generalizationRecord.getGeneralizedClassName());

        testClassDeclaration.getAllContainedComments().forEach(Comment::remove);

        for (MethodDeclaration testMethodDeclaration : compilationUnit.findAll(MethodDeclaration.class)) {
            if (!testMethodDeclaration.getNameAsString().equals(testRecord.getTestMethodName())) {
                testMethodDeclaration.remove();
                continue;
            }

            // @TODO: The MethodParameter.type needs to be the FULLY QUALIFIED name of the class.
            //   Otherwise, we will have issues mapping the class names to the correct Arbitraries.
            Type type = new TypeToken<List<MethodParameter>>() {}.getType();
            List<MethodParameter> testedMethodParameters = gson.fromJson(testRecord.getTestedMethodParamTypes(), type);

            String inputSpecification = new String(Files.readAllBytes(Paths.get(testRecord.getInputSpecificationPath())));
            String outputSpecification = new String(Files.readAllBytes(Paths.get(testRecord.getOutputSpecificationPath())));

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
            NormalAnnotationExpr forAllAnnotation = new NormalAnnotationExpr(new Name(net.jqwik.api.ForAll.class.getSimpleName()),  new NodeList<>(new MemberValuePair("supplier", new ClassExpr(testParametersSupplierClassType))));
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

        Paths.get(generalizationRecord.getGeneralizedClassPath()).toFile().getParentFile().mkdirs();
        Files.write(Paths.get(generalizationRecord.getGeneralizedClassPath()), compilationUnit.toString().getBytes());
    }
}
