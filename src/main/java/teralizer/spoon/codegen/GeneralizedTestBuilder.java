package teralizer.spoon.codegen;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
import teralizer.spoon.generalization.BaselineTestParametersSupplierFactory;
import teralizer.spoon.generalization.ImprovedTestParametersSupplierFactory;
import teralizer.spoon.generalization.NaiveTestParametersSupplierFactory;
import teralizer.spoon.generalization.ParsePredicatesFactory;
import teralizer.spoon.generalization.TestParametersFactory;
import teralizer.transformer.VariableDescriptorCollector;
import teralizer.util.Configuration;

public final class GeneralizedTestBuilder {
    private static final Map<String, String> JQWIK_LIFECYCLE_ANNOTATIONS =
        createJqwikLifecycleAnnotationMap();
    private static final Set<String> STATIC_LIFECYCLE_ANNOTATIONS =
        createStaticLifecycleAnnotationSet();

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
        rewriteLifecycleAnnotations(factory, generalizedClassDeclaration);

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

    private static Map<String, String> createJqwikLifecycleAnnotationMap() {
        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("org.junit.Before", "net.jqwik.api.lifecycle.BeforeProperty");
        annotations.put("org.junit.jupiter.api.BeforeEach", "net.jqwik.api.lifecycle.BeforeProperty");
        annotations.put("org.junit.After", "net.jqwik.api.lifecycle.AfterProperty");
        annotations.put("org.junit.jupiter.api.AfterEach", "net.jqwik.api.lifecycle.AfterProperty");
        annotations.put("org.junit.BeforeClass", "net.jqwik.api.lifecycle.BeforeContainer");
        annotations.put("org.junit.jupiter.api.BeforeAll", "net.jqwik.api.lifecycle.BeforeContainer");
        annotations.put("org.junit.AfterClass", "net.jqwik.api.lifecycle.AfterContainer");
        annotations.put("org.junit.jupiter.api.AfterAll", "net.jqwik.api.lifecycle.AfterContainer");
        return Collections.unmodifiableMap(annotations);
    }

    private static Set<String> createStaticLifecycleAnnotationSet() {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.junit.BeforeClass",
            "org.junit.jupiter.api.BeforeAll",
            "org.junit.AfterClass",
            "org.junit.jupiter.api.AfterAll"
        )));
    }
    public static Map<String, Value> mapTestedMethodArguments(
        List<MethodParameter> testedMethodParameters,
        List<Value> inputValues
    ) {
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


    private static void rewriteLifecycleAnnotations(Factory factory, CtClass<?> generalizedClassDeclaration) {
        for (CtMethod<?> method : generalizedClassDeclaration.getMethods()) {
            List<CtAnnotation<? extends Annotation>> annotations = new ArrayList<>(method.getAnnotations());
            for (CtAnnotation<? extends Annotation> annotation : annotations) {
                String annotationType = annotation.getAnnotationType().getQualifiedName();
                String jqwikAnnotationType = JQWIK_LIFECYCLE_ANNOTATIONS.get(annotationType);
                if (jqwikAnnotationType == null) {
                    continue;
                }

                method.removeAnnotation(annotation);
                CtAnnotation<Annotation> jqwikAnnotation = factory.Core().createAnnotation();
                jqwikAnnotation.setAnnotationType(factory.Type().createReference(jqwikAnnotationType));
                method.addAnnotation(jqwikAnnotation);
                if (STATIC_LIFECYCLE_ANNOTATIONS.contains(annotationType)) {
                    method.addModifier(ModifierKind.STATIC);
                }
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

        Optional<Integer> expectedParameterIndex = TestAnalysis.getExpectedParameterIndex(assertion);
        if (expectedParameterIndex.isPresent()) {
            List<CtExpression<?>> assertArguments = assertion.getArguments();
            assertArguments.set(expectedParameterIndex.get(), factory.Code().createCodeSnippetExpression(expectedExpression));
        }
    }

    private static void rewriteBooleanActualOutput(Factory factory, CtInvocation<?> assertion, Plan plan) {
        if (plan.getOutputJava() == null || plan.getOutput().getKind() != CapturedOutput.Kind.RETURNED_VALUE) {
            return;
        }
        String outputType = plan.getOutput().getReturnValue().getJavaType();
        boolean isBooleanOutput = outputType.equals("boolean") || outputType.equals("java.lang.Boolean");
        if (!isBooleanOutput || TestAnalysis.getExpectedParameterIndex(assertion).isPresent()) {
            return;
        }
        Optional<Integer> actualParameterIndex = TestAnalysis.getActualParameterIndex(assertion);
        if (!actualParameterIndex.isPresent()) {
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
