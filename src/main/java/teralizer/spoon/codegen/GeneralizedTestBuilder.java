package teralizer.spoon.codegen;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.CapturedInput;
import teralizer.domain.CapturedOutput;
import teralizer.domain.MethodCapabilities;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.spoon.SpoonUtils;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.analysis.TestShape;
import teralizer.spoon.generalization.BaselineTestParametersSupplierFactory;
import teralizer.spoon.generalization.ImprovedTestParametersSupplierFactory;
import teralizer.spoon.generalization.NaiveTestParametersSupplierFactory;
import teralizer.spoon.generalization.ParsePredicatesFactory;
import teralizer.spoon.generalization.TestParametersFactory;
import teralizer.transformer.VariableDescriptorCollector;
import teralizer.util.Configuration;

public final class GeneralizedTestBuilder {
    private static final String JUNIT_RUN_WITH = "org.junit.runner.RunWith";

    public CtClass<?> build(Factory factory, GeneralizationRecipe clonedRecipe, Names names, Plan plan) {
        CtClass<?> generalizedClassDeclaration = SpoonUtils.cloneClass(
            factory,
            factory.Class().get(names.getSourceTestClassQualifiedName()),
            names.getSourceTestPackageName(),
            names.getGeneralizedPackageName(),
            names.getSourceTestClassName(),
            names.getGeneralizedClassName(),
            names.getSourceTestClassQualifiedName(),
            names.getGeneralizedClassQualifiedName()
        );

        generalizedClassDeclaration.addComment(factory.createInlineComment("Test: " + names.getTestMethodQualifiedName()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Input values: " + names.getInputValuesPath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Input specification: " + names.getInputSpecificationPath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Output value: " + names.getOutputValuePath()));
        generalizedClassDeclaration.addComment(factory.createInlineComment("Output specification: " + names.getOutputSpecificationPath()));

        CtPath testMethodPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                names.getSourceTestMethodRelativePath(),
                names.getSourceTestClassQualifiedName(),
                names.getGeneralizedClassQualifiedName()));
        CtMethod<?> testMethod = (CtMethod<?>) testMethodPath.evaluateOn(generalizedClassDeclaration).get(0);
        SpoonUtils.deleteOtherTestMethodsInClass(generalizedClassDeclaration, testMethod);
        removeJUnitRunnerAnnotation(generalizedClassDeclaration);
        rewriteLifecycleAnnotations(factory, generalizedClassDeclaration);
        // This runs after sibling removal, so it rewrites only the retained code. It also runs
        // after lifecycle rewriting, so it sees an inherited fixture call in its final form.
        // InheritedTestCaseFilter already showed that this class can lose its ancestry.
        TestCaseDetachment.detach(generalizedClassDeclaration);

        CtPath assertionPath = new CtPathStringBuilder().fromString(
            GeneralizationRecipe.rewriteCtPathForClone(
                names.getSourceAssertionRelativePath(),
                names.getSourceTestClassQualifiedName(),
                names.getGeneralizedClassQualifiedName()));
        CtInvocation<?> assertion = (CtInvocation<?>) assertionPath.evaluateOn(testMethod).get(0);
        GeneralizationRecipe.Resolved recipe = clonedRecipe.resolveAgainst(testMethod, factory.getModel().getRootPackage());

        CtClass<?> testParametersClassDeclaration = TestParametersFactory.createParametersClass(factory, plan.getParameters());
        CtClass<?> testParametersSupplierClassDeclaration = createSupplierClass(factory, plan);
        rewriteExpectedOutput(factory, assertion, plan);

        if (plan.getFirstValueArbitraryClass() != null) {
            generalizedClassDeclaration.addNestedType(plan.getFirstValueArbitraryClass());
        }
        generalizedClassDeclaration.addNestedType(testParametersClassDeclaration);
        generalizedClassDeclaration.addNestedType(testParametersSupplierClassDeclaration);
        generalizedClassDeclaration.addNestedType(plan.getJqwikValueRecorderClass());
        if (usesParsePredicates(plan)) {
            generalizedClassDeclaration.addNestedType(ParsePredicatesFactory.createParsePredicatesClass());
        }
        addRecorderResetMethod(factory, generalizedClassDeclaration);

        List<CtAnnotation<?>> testMethodAnnotations = new ArrayList<>(testMethod.getAnnotations());
        testMethodAnnotations.forEach(testMethod::removeAnnotation);
        addPropertyAnnotations(factory, testMethod, plan.getJqwikTries());
        addForAllParameter(factory, testMethod);

        recipe.replaceInputSitesWithParameterReads(
            testMethod,
            factory,
            input -> "_p_." + input.toMethodParameter().getName()
        );
        testMethod.getBody().insertBegin(factory.Code().createCodeSnippetStatement("JqwikValueRecorder.record(_p_)"));
        rewriteBooleanActualOutput(factory, assertion, plan);
        SpoonUtils.deleteOtherAssertionsInMethod(testMethod, assertion);

        return generalizedClassDeclaration;
    }

    /*
     * Wrapper arguments map onto tested-method parameters BY NAME: the wrapper signature is
     * [_target_?][generalizable inputs][lifted locals][scope-bound constructions], the inputs
     * carry the tested parameters' names, and the extras carry reserved names (_target_,
     * _local_*). Positional mapping is unsound the moment any extra exists.
     */
    public static Map<String, Value> mapTestedMethodArguments(
        List<MethodParameter> testedMethodParameters,
        List<CapturedInput> inputValues
    ) {
        Map<String, Value> byName = new LinkedHashMap<>();
        for (CapturedInput input : inputValues) {
            byName.put(input.getName(), input.getValue());
        }
        Map<String, Value> arguments = new LinkedHashMap<>();
        for (MethodParameter parameter : testedMethodParameters) {
            Value value = byName.get(parameter.getName());
            if (value == null) {
                throw new IllegalArgumentException("No captured wrapper argument named '"
                    + parameter.getName() + "'. Captured names: " + byName.keySet() + ".");
            }
            arguments.put(parameter.getName(), value);
        }
        return arguments;
    }

    public static List<MethodParameter> collectTemporaryParameters(
        Model inputModel,
        Model outputModel,
        List<MethodParameter> testedMethodParameters
    ) {
        Set<String> declared = testedMethodParameters.stream()
            .map(MethodParameter::getName)
            .collect(Collectors.toSet());
        return VariableDescriptorCollector.collect(inputModel, outputModel).entrySet().stream()
            .filter(entry -> !declared.contains(entry.getKey()))
            .map(entry -> new MethodParameter(javaTypeForTemporary(entry.getValue()), entry.getKey()))
            .collect(Collectors.toList());
    }

    private static String javaTypeForTemporary(TypeDomain domain) {
        switch (domain) {
            case INTEGER:
                return "int";
            case REAL:
                return "double";
            case STRING:
                return "java.lang.String";
            default:
                throw new IllegalArgumentException("Unsupported temporary domain " + domain);
        }
    }


    private static void removeJUnitRunnerAnnotation(CtClass<?> generalizedClassDeclaration) {
        new ArrayList<>(generalizedClassDeclaration.getAnnotations()).stream()
            .filter(annotation -> JUNIT_RUN_WITH.equals(annotation.getAnnotationType().getQualifiedName()))
            .forEach(generalizedClassDeclaration::removeAnnotation);
    }

    private static void rewriteLifecycleAnnotations(Factory factory, CtClass<?> generalizedClassDeclaration) {
        for (CtMethod<?> method : generalizedClassDeclaration.getMethods()) {
            TestShape.LifecyclePhase phase = TestShape.lifecyclePhaseOf(method, generalizedClassDeclaration);
            if (phase == null) {
                continue;
            }

            new ArrayList<>(method.getAnnotations()).stream()
                .filter(annotation -> TestShape.phaseForLifecycleAnnotation(
                    annotation.getAnnotationType().getQualifiedName()) != null)
                .forEach(method::removeAnnotation);

            CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
            jqwikAnnotation.setAnnotationType(factory.Type().createReference(phase.jqwikAnnotation()));
            method.addAnnotation(jqwikAnnotation);
            if (phase.requiresStatic()) {
                method.addModifier(ModifierKind.STATIC);
            }
        }
    }

    private static CtClass<?> createSupplierClass(Factory factory, Plan plan) {
        switch (plan.getAlgorithm()) {
            case BASELINE:
                return BaselineTestParametersSupplierFactory.createSupplierClass(
                    factory, plan.getParameters(), plan.getTestedMethodArguments());
            case NAIVE:
                return NaiveTestParametersSupplierFactory.createSupplierClass(
                    factory, plan.getParameters(), plan.getTestedMethodArguments(), plan.getInputJava());
            case IMPROVED:
                return ImprovedTestParametersSupplierFactory.createSupplierClass(
                    factory, plan.getParameters(), plan.getInputJava(), plan.getInputGenerationPlan());
            default:
                throw new RuntimeException("Unsupported variant algorithm " + plan.getAlgorithm() + ".");
        }
    }

    private static void rewriteExpectedOutput(Factory factory, CtInvocation<?> assertion, Plan plan) {
        if (plan.getOutputJava() == null || plan.getOutput().getKind() != CapturedOutput.Kind.RETURNED_VALUE) {
            return;
        }
        String outputType = plan.getOutput().getReturnValue().getJavaType();
        boolean isBooleanOutput = outputType.equals("boolean") || outputType.equals("java.lang.Boolean");
        String expectedExpression = isBooleanOutput
            ? "((" + plan.getOutputJava() + ") != 0)"
            : "(" + outputType + ") (" + plan.getOutputJava() + ")";

        Optional<TestAnalysis.NormalizedAssertion> assertionView = TestAnalysis.normalizedAssertion(assertion);
        if (assertionView.isPresent() && assertionView.get().hasReplaceableExpectedExpression()) {
            assertionView.get().replaceExpectedExpression(factory, expectedExpression);
        }
    }

    private static void rewriteBooleanActualOutput(Factory factory, CtInvocation<?> assertion, Plan plan) {
        if (plan.getOutputJava() == null || plan.getOutput().getKind() != CapturedOutput.Kind.RETURNED_VALUE) {
            return;
        }
        String outputType = plan.getOutput().getReturnValue().getJavaType();
        boolean isBooleanOutput = outputType.equals("boolean") || outputType.equals("java.lang.Boolean");
        Optional<TestAnalysis.NormalizedAssertion> assertionView = TestAnalysis.normalizedAssertion(assertion);
        if (!isBooleanOutput || (assertionView.isPresent() && assertionView.get().hasReplaceableExpectedExpression())) {
            return;
        }
        Optional<Integer> actualParameterIndex = TestAnalysis.getActualParameterIndex(assertion);
        if (!actualParameterIndex.isPresent() || !assertionView.isPresent() || assertionView.get().getActualExpression() == null) {
            return;
        }
        String expectedExpression = "((" + plan.getOutputJava() + ") != 0)";
        String operator = assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_FALSE) ? "!=" : "==";
        List<CtExpression<?>> assertArguments = assertion.getArguments();
        int index = actualParameterIndex.get();
        CtExpression<?> actualArgument = assertArguments.get(index);
        assertArguments.set(index, factory.Code().createCodeSnippetExpression("(" + actualArgument + ") " + operator + " " + expectedExpression));
    }

    private static void addRecorderResetMethod(Factory factory, CtClass<?> generalizedClassDeclaration) {
        CtMethod<?> jqwikValueRecorderResetMethod = factory.Method().create(
            generalizedClassDeclaration,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            factory.Type().VOID_PRIMITIVE,
            "resetJqwikValueRecorder",
            Collections.emptyList(),
            Collections.emptySet(),
            factory.Core().createBlock()
        );
        CtAnnotation<Annotation> beforePropertyAnnotation = factory.Core().createAnnotation();
        beforePropertyAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.BeforeProperty"));
        jqwikValueRecorderResetMethod.addAnnotation(beforePropertyAnnotation);
        jqwikValueRecorderResetMethod.getBody().addStatement(factory.Code().createCodeSnippetStatement("JqwikValueRecorder.reset()"));
        generalizedClassDeclaration.addMethod(jqwikValueRecorderResetMethod);
    }

    private static void addPropertyAnnotations(Factory factory, CtMethod<?> testMethod, int jqwikTries) {
        CtAnnotation<Annotation> propertyAnnotation = factory.Core().createAnnotation();
        propertyAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.Property"));
        propertyAnnotation.addValue("tries", factory.Code().createLiteral(jqwikTries));
        propertyAnnotation.addValue("seed", factory.Code().createLiteral("0"));
        propertyAnnotation.addValue("shrinking", factory.Code().createCodeSnippetExpression("net.jqwik.api.ShrinkingMode.OFF"));
        propertyAnnotation.addValue("edgeCases", factory.Code().createCodeSnippetExpression("net.jqwik.api.EdgeCasesMode.FIRST"));
        testMethod.addAnnotation(propertyAnnotation);

        CtAnnotation<Annotation> addLifecycleHookAnnotation = factory.Core().createAnnotation();
        addLifecycleHookAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.lifecycle.AddLifecycleHook"));
        addLifecycleHookAnnotation.addValue("value", factory.Code().createClassAccess(factory.Type().createReference("JqwikValueRecorder.LimitedFilterMissesHook")));
        testMethod.addAnnotation(addLifecycleHookAnnotation);
    }

    private static void addForAllParameter(Factory factory, CtMethod<?> testMethod) {
        CtAnnotation<?> forAllAnnotation = factory.Core().createAnnotation();
        forAllAnnotation.setAnnotationType(factory.Type().createReference("net.jqwik.api.ForAll"));
        forAllAnnotation.addValue("supplier", factory.Code().createClassAccess(factory.Type().createReference("TestParametersSupplier")));

        CtParameter<Object> parameter = factory.Core().createParameter();
        CtTypeReference<Object> parameterType = factory.Type().createReference("TestParameters");
        parameter.setType(parameterType);
        parameter.setSimpleName("_p_");
        parameter.addAnnotation(forAllAnnotation);
        testMethod.addParameter(parameter);
    }

    private static boolean usesParsePredicates(Plan plan) {
        return containsParsePredicates(plan.getInputJava())
            || containsParsePredicates(plan.getOutputJava())
            || (plan.getInputGenerationPlan() != null
                && containsParsePredicates(plan.getInputGenerationPlan().getFullPredicate()));
    }

    private static boolean containsParsePredicates(String javaExpression) {
        return javaExpression != null
            && javaExpression.contains(MethodCapabilities.PARSE_PREDICATES_QUALIFIER + ".");
    }

    public static final class Names {
        private final String sourceTestPackageName;
        private final String generalizedPackageName;
        private final String sourceTestClassName;
        private final String generalizedClassName;
        private final String sourceTestClassQualifiedName;
        private final String generalizedClassQualifiedName;
        private final String sourceTestMethodRelativePath;
        private final String sourceAssertionRelativePath;
        private final String testMethodQualifiedName;
        private final String inputValuesPath;
        private final String inputSpecificationPath;
        private final String outputValuePath;
        private final String outputSpecificationPath;

        public Names(
            String sourceTestPackageName,
            String generalizedPackageName,
            String sourceTestClassName,
            String generalizedClassName,
            String sourceTestClassQualifiedName,
            String generalizedClassQualifiedName,
            String sourceTestMethodRelativePath,
            String sourceAssertionRelativePath,
            String testMethodQualifiedName,
            String inputValuesPath,
            String inputSpecificationPath,
            String outputValuePath,
            String outputSpecificationPath
        ) {
            this.sourceTestPackageName = sourceTestPackageName;
            this.generalizedPackageName = generalizedPackageName;
            this.sourceTestClassName = sourceTestClassName;
            this.generalizedClassName = generalizedClassName;
            this.sourceTestClassQualifiedName = sourceTestClassQualifiedName;
            this.generalizedClassQualifiedName = generalizedClassQualifiedName;
            this.sourceTestMethodRelativePath = sourceTestMethodRelativePath;
            this.sourceAssertionRelativePath = sourceAssertionRelativePath;
            this.testMethodQualifiedName = testMethodQualifiedName;
            this.inputValuesPath = inputValuesPath;
            this.inputSpecificationPath = inputSpecificationPath;
            this.outputValuePath = outputValuePath;
            this.outputSpecificationPath = outputSpecificationPath;
        }

        public String getSourceTestPackageName() {
            return this.sourceTestPackageName;
        }

        public String getGeneralizedPackageName() {
            return this.generalizedPackageName;
        }

        public String getSourceTestClassName() {
            return this.sourceTestClassName;
        }

        public String getGeneralizedClassName() {
            return this.generalizedClassName;
        }

        public String getSourceTestClassQualifiedName() {
            return this.sourceTestClassQualifiedName;
        }

        public String getGeneralizedClassQualifiedName() {
            return this.generalizedClassQualifiedName;
        }

        public String getSourceTestMethodRelativePath() {
            return this.sourceTestMethodRelativePath;
        }

        public String getSourceAssertionRelativePath() {
            return this.sourceAssertionRelativePath;
        }

        public String getTestMethodQualifiedName() {
            return this.testMethodQualifiedName;
        }

        public String getInputValuesPath() {
            return this.inputValuesPath;
        }

        public String getInputSpecificationPath() {
            return this.inputSpecificationPath;
        }

        public String getOutputValuePath() {
            return this.outputValuePath;
        }

        public String getOutputSpecificationPath() {
            return this.outputSpecificationPath;
        }
    }

    public static final class Plan {
        private final GeneralizationAlgorithm algorithm;
        private final List<MethodParameter> parameters;
        private final Map<String, Value> testedMethodArguments;
        private final String inputJava;
        private final InputGenerationPlan inputGenerationPlan;
        private final CapturedOutput output;
        private final String outputJava;
        private final int jqwikTries;
        private final CtClass<?> firstValueArbitraryClass;
        private final CtClass<?> jqwikValueRecorderClass;

        public Plan(
            GeneralizationAlgorithm algorithm,
            List<MethodParameter> parameters,
            Map<String, Value> testedMethodArguments,
            String inputJava,
            InputGenerationPlan inputGenerationPlan,
            CapturedOutput output,
            String outputJava,
            int jqwikTries,
            CtClass<?> firstValueArbitraryClass,
            CtClass<?> jqwikValueRecorderClass
        ) {
            this.algorithm = algorithm;
            this.parameters = parameters;
            this.testedMethodArguments = testedMethodArguments;
            this.inputJava = inputJava;
            this.inputGenerationPlan = inputGenerationPlan;
            this.output = output;
            this.outputJava = outputJava;
            this.jqwikTries = jqwikTries;
            this.firstValueArbitraryClass = firstValueArbitraryClass;
            this.jqwikValueRecorderClass = jqwikValueRecorderClass;
        }

        public GeneralizationAlgorithm getAlgorithm() {
            return this.algorithm;
        }

        public List<MethodParameter> getParameters() {
            return this.parameters;
        }

        public Map<String, Value> getTestedMethodArguments() {
            return this.testedMethodArguments;
        }

        public String getInputJava() {
            return this.inputJava;
        }

        public InputGenerationPlan getInputGenerationPlan() {
            return this.inputGenerationPlan;
        }

        public CapturedOutput getOutput() {
            return this.output;
        }

        public String getOutputJava() {
            return this.outputJava;
        }

        public int getJqwikTries() {
            return this.jqwikTries;
        }

        public CtClass<?> getFirstValueArbitraryClass() {
            return this.firstValueArbitraryClass;
        }

        public CtClass<?> getJqwikValueRecorderClass() {
            return this.jqwikValueRecorderClass;
        }
    }
}
