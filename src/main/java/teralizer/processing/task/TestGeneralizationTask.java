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
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import teralizer.TestGeneralizationRunner;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.jqwik.VariableConstraintExtractor;
import teralizer.jqwik.VariableConstraintExtractor.VariableConstraints;
import teralizer.processing.GeneralizationVariant;
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

    public TestGeneralizationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this(stage, variant, projectRecord, null);
    }

    public TestGeneralizationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord, TestRecord testRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
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

        Result<TestRecord> testRecords = create.selectFrom(Tables.TEST)
            .where(Tables.TEST.PROJECT_ID.eq(this.projectRecord.getId()))
            .and(Tables.TEST.IS_INCLUDED.eq(true))
            .fetch();

        for (TestRecord testRecord : testRecords) {
            scheduleTask.accept(new TestGeneralizationTask(this.stage, this.variant, this.projectRecord, testRecord));
        }
    }

    private void executeTask(TaskContext context) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        Gson gson = context.get(TaskContext.GSON);
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);

        this.generalizationRecord = this.createGeneralizationRecord(create);
        this.generalizeTest(gson, javaParser, velocityEngine);
    }

    private GeneralizationRecord createGeneralizationRecord(DSLContext create) {
        GeneralizationRecord record = create.newRecord(Tables.GENERALIZATION);
        record.setProjectId(this.getProjectId());
        record.setTestId(this.getTestId());
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

    private void generalizeTest(Gson gson, JavaParser javaParser, VelocityEngine velocityEngine) throws IOException {
        CompilationUnit compilationUnit = javaParser.parse(Paths.get(this.testRecord.getTestFilePath())).getResult().get();

        compilationUnit.setPackageDeclaration(this.generalizationRecord.getPackageName());
        compilationUnit.addImport(this.testRecord.getTestPackageName() + ".*");

        ClassOrInterfaceDeclaration testClassDeclaration = compilationUnit.getClassByName(this.testRecord.getTestClassName()).get();
        testClassDeclaration.setName(this.generalizationRecord.getClassName());

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
            Template template;
            StringWriter stringWriter = new StringWriter();

            switch (this.getVariant()) {
                case NAIVE:
                    context.put("testParametersSupplierClassName", TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
                    context.put("testParametersClassName", TEST_PARAMETERS_CLASS_NAME);
                    context.put("methodParameters", allParameters);
                    context.put("precondition", inputJava);

                    template = velocityEngine.getTemplate("test-parameters-classes-naive.vm");
                    template.merge(context, stringWriter);
                    break;
                case IMPROVED:
                    VariableConstraintExtractor extractor = new VariableConstraintExtractor();

                    Map<String, VariableConstraints> constraints = extractor.process(inputModel, allParameters);

                    Map<String, String> equalities = new HashMap<>();
                    constraints.forEach((name, constraint) -> {
                        if (constraint.getEquality() != null) {
                            equalities.put(name, constraint.getEquality());
                        }
                    });

                    Map<String, String> lowerBounds = new HashMap<>();
                    constraints.forEach((name, constraint) -> {
                        if (constraint.getLowerBound() != null) {
                            lowerBounds.put(name, constraint.getLowerBound());
                        }
                    });

                    Map<String, String> upperBounds = new HashMap<>();
                    constraints.forEach((name, constraint) -> {
                        if (constraint.getUpperBound() != null) {
                            upperBounds.put(name, constraint.getUpperBound());
                        }
                    });

                    context.put("testParametersSupplierClassName", TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
                    context.put("testParametersClassName", TEST_PARAMETERS_CLASS_NAME);
                    context.put("methodParameters", allParameters);
                    context.put("precondition", inputJava);
                    context.put("equalities", equalities);
                    context.put("lowerBounds", lowerBounds);
                    context.put("upperBounds", upperBounds);

                    template = velocityEngine.getTemplate("test-parameters-classes-improved.vm");
                    template.merge(context, stringWriter);
                    break;
                default:
                    throw new RuntimeException("Unsupported variant " + this.getVariant() + ".");
            }

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
            // Remove all non-@Test annotations.                                                                      //
            // ------------------------------------------------------------------------------------------------------ //

            testMethodDeclaration.getAnnotations().removeIf(a -> !a.getNameAsString().equals("Test"));

            // ------------------------------------------------------------------------------------------------------ //
            // Replace JUnit @Test annotations with jqwik @Property annotations.                                      //
            // ------------------------------------------------------------------------------------------------------ //

            testMethodDeclaration.getAnnotationByName("Test").ifPresent(annotation -> {
                testMethodDeclaration.remove(annotation);
                testMethodDeclaration.addAnnotation(
                    new NormalAnnotationExpr(new Name(net.jqwik.api.Property.class.getName()),
                        new NodeList<>(
                            new MemberValuePair("tries", new IntegerLiteralExpr(Integer.toString(MAX_TRIES_JQWIK))),
                            new MemberValuePair("seed", new StringLiteralExpr("0"))
                        )
                    )
                );
            });

            // ------------------------------------------------------------------------------------------------------ //
            // Add `@ForAll(...) TestParameters testParameters` to the test method signature.                         //
            // ------------------------------------------------------------------------------------------------------ //

            ClassOrInterfaceType testParametersClassType = new ClassOrInterfaceType(null, testParametersClassDeclaration.getNameAsString());
            ClassOrInterfaceType testParametersSupplierClassType = new ClassOrInterfaceType(null, testParametersSupplierClassDeclaration.getNameAsString());

            Parameter testParametersParameter = new Parameter(testParametersClassType, "_p_");
            NormalAnnotationExpr forAllAnnotation = new NormalAnnotationExpr(new Name(net.jqwik.api.ForAll.class.getName()), new NodeList<>(new MemberValuePair("supplier", new ClassExpr(testParametersSupplierClassType))));
            testParametersParameter.addAnnotation(forAllAnnotation);

            testMethodDeclaration.addParameter(testParametersParameter);

            // ------------------------------------------------------------------------------------------------------ //
            // Replace tested method arguments with values from `testParameters`.                                     //
            // ------------------------------------------------------------------------------------------------------ //

            MethodCallExpr testedMethodCall = TestAnalysisTask.findTestedMethodCall(testMethodDeclaration);

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
                MethodCallExpr assertEqualsCall = TestAnalysisTask.findAssertEqualsCall(testMethodDeclaration);
                assertEqualsCall.getArguments().set(0, javaParser.parseExpression(outputJava).getResult().get());
            }

            // ------------------------------------------------------------------------------------------------------ //
            // Remove parts of the test setup code that are no longer needed after generalization.                    //
            // ------------------------------------------------------------------------------------------------------ //

            // @TODO: Remove setup code that is not needed anymore after generalization.
        }

        Path generalizedFilePath = Paths.get(this.generalizationRecord.getFilePath());
        byte[] generalizedFileBytes = compilationUnit.toString().getBytes();

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
