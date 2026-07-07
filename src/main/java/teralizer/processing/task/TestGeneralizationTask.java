package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import teralizer.domain.CapturedInput;
import teralizer.domain.CapturedOutput;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.SeedSpecConsistency;
import teralizer.domain.Value;
import teralizer.generalization.WideningLicense;
import teralizer.jpf.OutputSpecClassifier;
import teralizer.jqwik.planning.ConstraintClauses;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.processing.diagnostics.GenerationCoverageWriter;
import teralizer.repository.PipelineQueries;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.codegen.GeneralizedTestBuilder;
import teralizer.spoon.generalization.*;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;
import teralizer.transformer.SpecificationGson;
import teralizer.util.Configuration;
import teralizer.util.TypeCapability;

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
        VelocityEngine velocityEngine = context.get(TaskContext.VELOCITY_ENGINE);

        this.generalizationRecord = this.createGeneralizationRecord(create);
        this.generalizeTest(create, gson, spoonLauncher, velocityEngine);
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

    private void generalizeTest(DSLContext create, Gson gson, Launcher spoonLauncher, VelocityEngine velocityEngine) throws IOException {
        Factory factory = spoonLauncher.getFactory();
        GeneralizationRecipe originalRecipe = GeneralizationRecipe
            .fromJson(gson, this.assertionRecord.getGeneralizationRecipe());
        GeneralizationRecipe clonedRecipe = originalRecipe.rewriteForClone(
            this.testRecord.getTestClassQualifiedName(),
            this.generalizationRecord.getClassQualifiedName()
        );

        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> testedMethodParameters = gson.fromJson(this.assertionRecord.getTestedMethodParameters(), type);

        String inputValuesString = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getInputValuesPath())));
        Gson specificationGson = SpecificationGson.create();
        Type inputValuesType = new TypeToken<List<CapturedInput>>() {}.getType();
        List<CapturedInput> inputValues = specificationGson.fromJson(inputValuesString, inputValuesType);
        Map<String, Value> testedMethodArguments = GeneralizedTestBuilder.mapTestedMethodArguments(testedMethodParameters, inputValues);

        GeneralizedTestBuilder.Plan plan = this.createBuilderPlan(create, velocityEngine, clonedRecipe, testedMethodParameters, testedMethodArguments, specificationGson);
        if (plan == null) {
            return;
        }

        CtClass<?> generalizedClassDeclaration = new GeneralizedTestBuilder().build(
            factory,
            clonedRecipe,
            new GeneralizedTestBuilder.Names(this.testRecord.getTestPackageName(), this.generalizationRecord.getPackageName(), this.testRecord.getTestClassName(), this.generalizationRecord.getClassName(), this.testRecord.getTestClassQualifiedName(), this.generalizationRecord.getClassQualifiedName(), this.testRecord.getTestMethodRelativePath(), this.assertionRecord.getAssertionRelativePath(), this.testRecord.getTestMethodQualifiedName(), this.assertionRecord.getInputValuesPath(), this.assertionRecord.getInputSpecificationPath(), this.assertionRecord.getOutputValuePath(), this.assertionRecord.getOutputSpecificationPath()),
            plan);
        this.writeGeneralizedClass(spoonLauncher, generalizedClassDeclaration);
        GeneralizationLifecycleWriter.recordGeneratedSourceCreated(create, this.generalizationRecord);
    }

    private GeneralizedTestBuilder.Plan createBuilderPlan(
        DSLContext create,
        VelocityEngine velocityEngine,
        GeneralizationRecipe clonedRecipe,
        List<MethodParameter> testedMethodParameters,
        Map<String, Value> testedMethodArguments,
        Gson specificationGson
    ) throws IOException {
        GeneralizationAlgorithm algorithm = Configuration.getGeneralizationAlgorithm(this.getVariant());
        InputGenerationPlan inputGenerationPlan = null;
        String inputJava = null;
        String outputJava = null;
        CapturedOutput output = null;
        CtClass<?> firstValueArbitraryClass = null;
        List<MethodParameter> allParameters;

        if (algorithm == GeneralizationAlgorithm.BASELINE) {
            allParameters = new ArrayList<>(testedMethodParameters);
            allParameters.removeIf(parameter -> !TypeCapability.supportsGeneratedInput(parameter.getType()));
        } else {
            String inputSpecification = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getInputSpecificationPath())));
            String outputSpecification = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getOutputSpecificationPath())));

            JsonToModelTransformer jsonToModelTransformer = new JsonToModelTransformer();
            Model inputModel = jsonToModelTransformer.transform(inputSpecification);
            SeedSpecConsistency.Verdict seedSpecConsistency = SeedSpecConsistency.evaluate(
                inputModel,
                testedMethodArguments,
                testedMethodParameters
            );
            // A path condition collected on a concrete path must hold for that input.
            // A proven violation means the extracted spec is unsound.
            if (seedSpecConsistency == SeedSpecConsistency.Verdict.VIOLATED) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo(SeedSpecConsistency.INPUT_SPEC_NOT_SATISFIED_BY_SEED);
                this.generalizationRecord.store();
                return null;
            }
            Model outputModel = jsonToModelTransformer.transform(outputSpecification);

            Map<String, String> testedMethodParameterTypes = testedMethodParameters.stream().collect(Collectors.toMap(MethodParameter::getName, MethodParameter::getType));
            ModelToJavaTransformer modelToJavaTransformer = new ModelToJavaTransformer(testedMethodParameterTypes);

            List<MethodParameter> temporaryParameters = GeneralizedTestBuilder.collectTemporaryParameters(inputModel, outputModel, testedMethodParameters);
            allParameters = new ArrayList<>();
            allParameters.addAll(testedMethodParameters);
            allParameters.addAll(temporaryParameters);
            allParameters.removeIf(parameter -> !TypeCapability.supportsGeneratedInput(parameter.getType()));

            Set<String> generalizableParameterNames = allParameters.stream().map(MethodParameter::getName).collect(Collectors.toSet());
            inputJava = modelToJavaTransformer.transformPredicate(inputModel, generalizableParameterNames);
            outputJava = modelToJavaTransformer.transform(outputModel);
            boolean isInputJavaTooLarge = inputJava != null && inputJava.length() > Configuration.MAX_SPECIFICATION_SIZE;
            boolean isOutputJavaTooLarge = outputJava != null && outputJava.length() > Configuration.MAX_SPECIFICATION_SIZE;
            if (isInputJavaTooLarge || isOutputJavaTooLarge) {
                throw new RuntimeException("Failing generalization to avoid potential 'code too large' compilation errors.");
            }

            Set<String> pathConditionParameterNames;
            switch (algorithm) {
                case NAIVE:
                    pathConditionParameterNames = WideningLicense.referencedParameterNames(
                        ConstraintClauses.from(inputModel, testedMethodParameterTypes, generalizableParameterNames));
                    break;
                case IMPROVED:
                    inputGenerationPlan = new InputGenerationPlanner().plan(allParameters, testedMethodArguments, inputModel);
                    pathConditionParameterNames = WideningLicense.referencedParameterNames(inputGenerationPlan.getClauses());
                    break;
                default:
                    throw new RuntimeException("Unsupported variant " + this.getVariant() + ".");
            }

            String outputValueString = new String(Files.readAllBytes(Paths.get(this.assertionRecord.getOutputValuePath())));
            output = specificationGson.fromJson(outputValueString, CapturedOutput.class);
            WideningLicense.Verdict wideningLicense = WideningLicense.evaluate(
                OutputSpecClassifier.classify(output.getKind(), outputModel),
                clonedRecipe.getOracleExpressionType(),
                generalizableParameterNames,
                pathConditionParameterNames,
                this.assertionRecord.getConcretizationEvents(),
                this.assertionRecord.getPostConcretizationDivergenceRisk()
            );
            if (!wideningLicense.allowsWidening()) {
                this.generalizationRecord.setIsIncluded(false);
                this.generalizationRecord.setExclusionInfo(wideningLicense.getExclusionInfo());
                this.generalizationRecord.store();
                return null;
            }

            if (algorithm == GeneralizationAlgorithm.IMPROVED) {
                this.generalizationRecord.setTotalConstraintCount(inputGenerationPlan.getTotalConstraintCount());
                this.generalizationRecord.setUsedConstraintCount(inputGenerationPlan.getUsedConstraintCount());
                this.generalizationRecord.store();
                GenerationCoverageWriter.write(create, this.getGeneralizationId(), inputGenerationPlan, allParameters);
            }
            firstValueArbitraryClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine);
        }

        Path jqwikDataDirectory = this.projectRecord.getDataPath()
            .resolve("project-id-" + this.getProjectId())
            .resolve("jqwik-data");
        CtClass<?> jqwikValueRecorderClass = JqwikValueRecorderFactory.createRecorderClass(
            velocityEngine,
            jqwikDataDirectory,
            this.getProjectId(),
            this.getGeneralizationId(),
            this.getVariant(),
            this.generalizationRecord.getMethodName()
        );
        return new GeneralizedTestBuilder.Plan(algorithm, allParameters, testedMethodArguments, inputJava, inputGenerationPlan, output, outputJava, Configuration.getGeneralizationJqwikTries(this.getVariant()), firstValueArbitraryClass, jqwikValueRecorderClass);
    }

    private void writeGeneralizedClass(Launcher spoonLauncher, CtClass<?> generalizedClassDeclaration) throws IOException {
        CtCompilationUnit cu = spoonLauncher.getFactory().CompilationUnit().getOrCreate(this.generalizationRecord.getFilePath());
        cu.setImports(SpoonUtils.importsForGeneratedClass(generalizedClassDeclaration));
        cu.setDeclaredTypes(Collections.singletonList(generalizedClassDeclaration));

        DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(spoonLauncher.getEnvironment());
        printer.setIgnoreImplicit(false);
        printer.calculate(cu, Collections.singletonList(generalizedClassDeclaration));
        byte[] generalizedFileBytes = printer.getResult().getBytes();

        Path generalizedFilePath = Paths.get(this.generalizationRecord.getFilePath());
        generalizedFilePath.toFile().getParentFile().mkdirs();
        Files.write(generalizedFilePath, generalizedFileBytes);

        try (Stream<String> lines = Files.lines(generalizedFilePath)) {
            this.generalizationRecord.setLineCount((int) lines.count());
            this.generalizationRecord.store();
        }

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
