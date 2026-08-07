package teralizer.spoon.codegen;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.jqwik.api.Example;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.domain.CapturedException;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.StringValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
import teralizer.jqwik.planning.ConstraintClause;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;
import teralizer.jqwik.planning.ParameterGenerationPlan;
import teralizer.jqwik.planning.RawJavaRecipe;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.GeneralizationRecipe;
import teralizer.spoon.analysis.TestAnalysis;
import teralizer.spoon.generalization.FirstValueArbitraryFactory;
import teralizer.spoon.generalization.JqwikValueRecorderFactory;
import teralizer.transformer.ModelToJavaTransformer;

public class GeneralizedTestBuilderTest {

    @Example
    void symbolicPlanRewritesOracleAndInstallsJqwikParameter() {
        Scenario scenario = scenario();
        Model inputModel = new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER));
        Model outputModel = new Variable("x", TypeDomain.INTEGER);
        MethodParameter parameter = new MethodParameter("int", "x");
        List<MethodParameter> parameters = Collections.singletonList(parameter);
        Map<String, Value> arguments = Collections.singletonMap("x", new PrimitiveValue("int", 2));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("x", "int"));
        String inputJava = transformer.transformPredicate(inputModel, Collections.singleton("x"));
        String outputJava = transformer.transform(outputModel);
        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, arguments, inputModel);

        CtClass<?> generalized = build(
            scenario,
            new GeneralizedTestBuilder.Plan(
                GeneralizationAlgorithm.IMPROVED,
                parameters,
                arguments,
                inputJava,
                plan,
                CapturedOutput.ofReturnValue(new PrimitiveValue("int", 2)),
                outputJava,
                100,
                FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine()),
                JqwikValueRecorderFactory.createRecorderClass(
                    velocityEngine(), Paths.get("jqwik-data"), 7L, 101L, "IMPROVED_100_TRIES", "returnsInput")
            )
        );

        CtMethod<?> testMethod = generalized.getMethodsByName("returnsInput").get(0);
        Assert.assertEquals(1, testMethod.getParameters().size());
        Assert.assertEquals("_p_", testMethod.getParameters().get(0).getSimpleName());
        Assert.assertTrue(testMethod.toString(), testMethod.toString().contains("JqwikValueRecorder.record(_p_)"));
        Assert.assertTrue(
            testMethod.toString(),
            testMethod.toString().contains("org.junit.Assert.assertEquals((int) (_p_.x), new example.Subject().id(_p_.x))")
        );
        Assert.assertTrue(generalized.getNestedTypes().stream().anyMatch(type -> type.getSimpleName().equals("TestParameters")));
        Assert.assertTrue(generalized.getNestedTypes().stream().anyMatch(type -> type.getSimpleName().equals("TestParametersSupplier")));
        Assert.assertTrue(generalized.getNestedTypes().stream().anyMatch(type -> type.getSimpleName().equals("FirstValueArbitrary")));
        Assert.assertTrue(generalized.getNestedTypes().stream().anyMatch(type -> type.getSimpleName().equals("JqwikValueRecorder")));
    }

    @Example
    void generalizedClassDropsJUnitRunnerAnnotation() {
        CtClass<?> generalized = build(scenario(RUN_WITH_SOURCE), identityPlan());

        Assert.assertFalse(generalized.getAnnotations().stream().anyMatch(annotation ->
            "org.junit.runner.RunWith".equals(annotation.getAnnotationType().getQualifiedName())));
    }

    @Example
    void symbolicPlanRewritesHamcrestMatcherExpectedExpression() {
        Scenario scenario = hamcrestScenario();
        Model inputModel = new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER));
        Model outputModel = new Variable("x", TypeDomain.INTEGER);
        MethodParameter parameter = new MethodParameter("int", "x");
        List<MethodParameter> parameters = Collections.singletonList(parameter);
        Map<String, Value> arguments = Collections.singletonMap("x", new PrimitiveValue("int", 2));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("x", "int"));
        String inputJava = transformer.transformPredicate(inputModel, Collections.singleton("x"));
        String outputJava = transformer.transform(outputModel);
        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, arguments, inputModel);

        CtClass<?> generalized = build(
            scenario,
            new GeneralizedTestBuilder.Plan(
                GeneralizationAlgorithm.IMPROVED,
                parameters,
                arguments,
                inputJava,
                plan,
                CapturedOutput.ofReturnValue(new PrimitiveValue("int", 2)),
                outputJava,
                100,
                FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine()),
                JqwikValueRecorderFactory.createRecorderClass(
                    velocityEngine(), Paths.get("jqwik-data"), 7L, 101L, "IMPROVED_100_TRIES", "returnsInput")
            )
        );

        String generated = generalized.getMethodsByName("returnsInput").get(0).toString();
        Assert.assertTrue(generated, generated.contains(
            "org.junit.Assert.assertThat(new example.Subject().id(_p_.x), org.hamcrest.CoreMatchers.is((int) (_p_.x)))"));
    }

    @Example
    void thrownTryFailCatchPlanKeepsCatchMessageAssertion() {
        Scenario scenario = tryFailCatchScenario();
        Model inputModel = new Operation(new Variable("x", TypeDomain.INTEGER), Operator.GT, new Constant(0L, TypeDomain.INTEGER));
        MethodParameter parameter = new MethodParameter("int", "x");
        List<MethodParameter> parameters = Collections.singletonList(parameter);
        Map<String, Value> arguments = Collections.singletonMap("x", new PrimitiveValue("int", 2));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("x", "int"));
        String inputJava = transformer.transformPredicate(inputModel, Collections.singleton("x"));
        InputGenerationPlan plan = new InputGenerationPlanner().plan(parameters, arguments, inputModel);

        CtClass<?> generalized = build(
            scenario,
            new GeneralizedTestBuilder.Plan(
                GeneralizationAlgorithm.IMPROVED,
                parameters,
                arguments,
                inputJava,
                plan,
                CapturedOutput.ofThrow(new CapturedException("java.lang.IllegalArgumentException", "bad input")),
                null,
                100,
                FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine()),
                JqwikValueRecorderFactory.createRecorderClass(
                    velocityEngine(), Paths.get("jqwik-data"), 7L, 101L, "IMPROVED_100_TRIES", "returnsInput")
            )
        );

        String generated = generalized.getMethodsByName("returnsInput").get(0).toString();
        Assert.assertTrue(generated, generated.contains("org.junit.Assert.fail()"));
        Assert.assertTrue(generated, generated.contains("org.junit.Assert.assertEquals(\"bad input\", e.getMessage())"));
    }

    @Example
    void parsePredicatePlanInstallsHelperClassAndUsesStaticFilter() {
        Scenario scenario = scenario(PARSE_SOURCE);
        MethodParameter parameter = new MethodParameter("java.lang.String", "s");
        List<MethodParameter> parameters = Collections.singletonList(parameter);
        Map<String, Value> arguments = Collections.singletonMap("s", new StringValue("42"));
        InputGenerationPlan generationPlan = new InputGenerationPlan(
            Collections.singletonList(new ParameterGenerationPlan(
                parameter,
                TypeDomain.STRING,
                new RawJavaRecipe("return net.jqwik.api.Arbitraries.strings().ascii().ofMaxLength(16)"),
                "(java.lang.String) (\"42\")",
                Collections.emptySet())),
            Collections.singletonList(new ConstraintClause(
                0,
                new Invocation(null, "ParsePredicates", "isInteger", Collections.singletonList(new Variable("s", TypeDomain.STRING))),
                "ParsePredicates.isInteger(_p_.s)")),
            Collections.emptySet());

        CtClass<?> generalized = build(
            scenario,
            new GeneralizedTestBuilder.Plan(
                GeneralizationAlgorithm.IMPROVED,
                parameters,
                arguments,
                "ParsePredicates.isInteger(_p_.s)",
                generationPlan,
                CapturedOutput.ofReturnValue(new StringValue("42")),
                new ModelToJavaTransformer(Collections.singletonMap("s", "java.lang.String")).transform(new Variable("s", TypeDomain.STRING)),
                100,
                FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine()),
                JqwikValueRecorderFactory.createRecorderClass(
                    velocityEngine(), Paths.get("jqwik-data"), 7L, 101L, "IMPROVED_100_TRIES", "returnsInput")
            )
        );

        CtClass<?> helper = nestedClass(generalized, "ParsePredicates");
        Assert.assertNotNull("generated tests using parse predicates must include the support helper", helper);
        assertHelperMethodDelegates(helper, "isInteger", "Integer.parseInt(s)");
        assertHelperMethodDelegates(helper, "isLong", "Long.parseLong(s)");
        assertHelperMethodDelegates(helper, "isFloat", "Float.parseFloat(s)");
        assertHelperMethodDelegates(helper, "isDouble", "Double.parseDouble(s)");

        CtClass<?> supplier = nestedClass(generalized, "TestParametersSupplier");
        Assert.assertNotNull(supplier);
        Assert.assertTrue(supplier.toString(), supplier.toString().contains("ParsePredicates.isInteger(_p_.s)"));
    }


    private static CtClass<?> nestedClass(CtClass<?> owner, String simpleName) {
        return owner.getNestedTypes().stream()
            .filter(type -> type.getSimpleName().equals(simpleName))
            .map(type -> (CtClass<?>) type)
            .findFirst()
            .orElse(null);
    }

    private static void assertHelperMethodDelegates(CtClass<?> helper, String method, String parserCall) {
        CtMethod<?> predicate = helper.getMethodsByName(method).isEmpty()
            ? null
            : helper.getMethodsByName(method).get(0);
        Assert.assertNotNull(method, predicate);
        String code = predicate.toString();
        Assert.assertTrue(code, code.contains(parserCall));
        Assert.assertTrue(code, code.contains("return true"));
        Assert.assertTrue(code, code.contains("return false"));
    }
    @Example
    void junit3GeneralizationBecomesASingleOwnerContainer() {
        CtClass<?> generalized = build(scenario(JUNIT3_SOURCE), identityPlan());

        CtMethod<?> setUp = generalized.getMethodsByName("setUp").get(0);
        CtMethod<?> tearDown = generalized.getMethodsByName("tearDown").get(0);
        Assert.assertTrue(setUp.getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.BeforeProperty".equals(annotation.getAnnotationType().getQualifiedName())));
        Assert.assertTrue(tearDown.getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.AfterProperty".equals(annotation.getAnnotationType().getQualifiedName())));
        // The vintage engine claims a TestCase subclass whatever annotations it carries, so the
        // ancestry and the convention-named sibling both have to go or two engines run the property.
        Assert.assertNull(generalized.getSuperclass());
        Assert.assertTrue(generalized.getMethodsByName("testSibling").isEmpty());
    }

    @Example
    void detachingJUnit3QualifiesInheritedAssertionsAndDropsSuperFixtureCalls() {
        CtClass<?> generalized = build(scenario(JUNIT3_INHERITED_SOURCE), identityPlan());

        Assert.assertNull(generalized.getSuperclass());
        for (String fixture : new String[] {"setUp", "tearDown"}) {
            Assert.assertTrue("@Override cannot compile without the ancestry: " + fixture,
                generalized.getMethodsByName(fixture).get(0).getAnnotations().stream().noneMatch(annotation ->
                    "java.lang.Override".equals(annotation.getAnnotationType().getQualifiedName())));
        }

        List<CtInvocation> inherited = generalized.getElements(new TypeFilter<>(CtInvocation.class)).stream()
            .filter(invocation -> invocation.getExecutable() != null
                && invocation.getExecutable().getDeclaringType() != null)
            .filter(invocation -> {
                String declaring = invocation.getExecutable().getDeclaringType().getQualifiedName();
                return "junit.framework.TestCase".equals(declaring) || "junit.framework.Assert".equals(declaring);
            })
            .collect(java.util.stream.Collectors.toList());

        // The inherited assertion survives, now naming the type that declares it, and nothing
        // reaches a superclass that no longer exists.
        Assert.assertFalse("expected a retained inherited assertion", inherited.isEmpty());
        for (CtInvocation<?> invocation : inherited) {
            if (invocation.getExecutable().isConstructor()) {
                continue;
            }
            Assert.assertFalse("super fixture call survived: " + invocation,
                invocation.getTarget() instanceof spoon.reflect.code.CtSuperAccess);
            Assert.assertTrue("assertion not qualified: " + invocation,
                invocation.getTarget() instanceof spoon.reflect.code.CtTypeAccess);
            Assert.assertEquals("junit.framework.Assert",
                ((spoon.reflect.code.CtTypeAccess<?>) invocation.getTarget()).getAccessedType().getQualifiedName());
        }
    }

    @Example
    void detachingDropsTheSuperConstructorCallAndKeepsTheClassConstructible() {
        CtClass<?> generalized = build(scenario(JUNIT3_STRING_CONSTRUCTOR_SOURCE), identityPlan());

        Assert.assertNull(generalized.getSuperclass());
        // Object declares no constructor taking a name, so a surviving super(testName) would not
        // compile, and jqwik needs a constructor it can call without arguments.
        Assert.assertTrue("super constructor call survived",
            generalized.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .noneMatch(invocation -> invocation.getExecutable() != null
                    && invocation.getExecutable().isConstructor()
                    && invocation.getTarget() instanceof spoon.reflect.code.CtSuperAccess));
        Assert.assertTrue("no constructor jqwik can call",
            generalized.getConstructors().stream().anyMatch(c -> c.getParameters().isEmpty()));
    }

    @Example
    void junit4LifecycleRewriteKeepsContainerHooksStatic() {
        CtClass<?> generalized = build(scenario(JUNIT4_LIFECYCLE_SOURCE), identityPlan());

        Assert.assertTrue(generalized.getMethodsByName("setUp").get(0).getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.BeforeProperty".equals(annotation.getAnnotationType().getQualifiedName())));
        Assert.assertTrue(generalized.getMethodsByName("after").get(0).getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.AfterProperty".equals(annotation.getAnnotationType().getQualifiedName())));
        Assert.assertTrue(generalized.getMethodsByName("beforeClass").get(0).getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.BeforeContainer".equals(annotation.getAnnotationType().getQualifiedName())));
        Assert.assertTrue(generalized.getMethodsByName("afterClass").get(0).getAnnotations().stream().anyMatch(annotation ->
            "net.jqwik.api.lifecycle.AfterContainer".equals(annotation.getAnnotationType().getQualifiedName())));
        Assert.assertTrue(generalized.getMethodsByName("beforeClass").get(0).hasModifier(ModifierKind.STATIC));
        Assert.assertTrue(generalized.getMethodsByName("afterClass").get(0).hasModifier(ModifierKind.STATIC));
        Assert.assertFalse(generalized.getMethodsByName("setUp").get(0).hasModifier(ModifierKind.STATIC));
        Assert.assertFalse(generalized.getMethodsByName("after").get(0).hasModifier(ModifierKind.STATIC));
    }

    private static GeneralizedTestBuilder.Plan identityPlan() {
        Model inputModel = new Operation(
            new Variable("x", TypeDomain.INTEGER),
            Operator.GT,
            new Constant(0L, TypeDomain.INTEGER));
        Model outputModel = new Variable("x", TypeDomain.INTEGER);
        MethodParameter parameter = new MethodParameter("int", "x");
        List<MethodParameter> parameters = Collections.singletonList(parameter);
        Map<String, Value> arguments = Collections.singletonMap("x", new PrimitiveValue("int", 2));
        ModelToJavaTransformer transformer = new ModelToJavaTransformer(Collections.singletonMap("x", "int"));
        String inputJava = transformer.transformPredicate(inputModel, Collections.singleton("x"));
        String outputJava = transformer.transform(outputModel);
        return new GeneralizedTestBuilder.Plan(
            GeneralizationAlgorithm.IMPROVED,
            parameters,
            arguments,
            inputJava,
            new InputGenerationPlanner().plan(parameters, arguments, inputModel),
            CapturedOutput.ofReturnValue(new PrimitiveValue("int", 2)),
            outputJava,
            100,
            FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine()),
            JqwikValueRecorderFactory.createRecorderClass(
                velocityEngine(), Paths.get("jqwik-data"), 7L, 101L, "IMPROVED_100_TRIES", "returnsInput")
        );
    }

    private static CtClass<?> build(Scenario scenario, GeneralizedTestBuilder.Plan plan) {
        GeneralizationRecipe clonedRecipe = scenario.recipe.rewriteForClone(
            "example.SubjectTest",
            "example._SubjectTest_Generalized_returnsInput_101_Test"
        );
        return new GeneralizedTestBuilder().build(
            scenario.launcher.getFactory(),
            clonedRecipe,
            new GeneralizedTestBuilder.Names(
                "example",
                "example",
                "SubjectTest",
                "_SubjectTest_Generalized_returnsInput_101_Test",
                "example.SubjectTest",
                "example._SubjectTest_Generalized_returnsInput_101_Test",
                scenario.testMethod.getPath().relativePath(scenario.testClass).toString(),
                scenario.assertion.getPath().relativePath(scenario.testMethod).toString(),
                "example.SubjectTest.returnsInput",
                "input-values.json",
                "input-specification.json",
                "output-value.json",
                "output-specification.json"
            ),
            plan
        );
    }

    private static Scenario scenario() {
        return scenario(SOURCE);
    }

    private static Scenario scenario(String source) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source, "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtClass<?> subjectClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "Subject"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("returnsInput").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtInvocation<?> testedCall = (CtInvocation<?>) assertion.getArguments().get(1);
        CtMethod<?> testedMethod = subjectClass.getMethodsByName("id").get(0);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            testedCall,
            GeneralizableInput.derive(testedMethod, testedCall),
            testedMethod.getType().getQualifiedName()
        );
        return new Scenario(launcher, testClass, testMethod, assertion, recipe);
    }

    private static Scenario hamcrestScenario() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(HAMCREST_SOURCE, "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtClass<?> subjectClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "Subject"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("returnsInput").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        CtExpression<?> actual = TestAnalysis.normalizedAssertion(assertion).get().getActualExpression();
        CtInvocation<?> testedCall = (CtInvocation<?>) actual;
        CtMethod<?> testedMethod = subjectClass.getMethodsByName("id").get(0);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            testedCall,
            GeneralizableInput.derive(testedMethod, testedCall),
            testedMethod.getType().getQualifiedName()
        );
        return new Scenario(launcher, testClass, testMethod, assertion, recipe);
    }

    private static Scenario tryFailCatchScenario() {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(TRY_FAIL_CATCH_SOURCE, "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtClass<?> subjectClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "Subject"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("returnsInput").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).stream()
            .filter(candidate -> "fail".equals(candidate.getExecutable().getSimpleName()))
            .findFirst()
            .get();
        CtInvocation<?> testedCall = testMethod.getElements(new TypeFilter<>(CtInvocation.class)).stream()
            .filter(candidate -> "reject".equals(candidate.getExecutable().getSimpleName()))
            .findFirst()
            .get();
        CtMethod<?> testedMethod = subjectClass.getMethodsByName("reject").get(0);
        GeneralizationRecipe recipe = GeneralizationRecipe.from(
            testedMethod,
            testedCall,
            GeneralizableInput.derive(testedMethod, testedCall),
            "java.lang.IllegalArgumentException"
        );
        return new Scenario(launcher, testClass, testMethod, assertion, recipe);
    }

    private static VelocityEngine velocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");
        VelocityEngine velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
        return velocityEngine;
    }

    private static final String JUNIT3_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest extends junit.framework.TestCase {\n"
        + "  protected void setUp() {}\n"
        + "  protected void tearDown() {}\n"
        + "  public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "  public void testSibling() {}\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    // Inherited assertions and inherited fixture calls: what detachment has to rewrite.
    private static final String JUNIT3_INHERITED_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest extends junit.framework.TestCase {\n"
        + "  @Override protected void setUp() { super.setUp(); }\n"
        + "  @Override protected void tearDown() { super.tearDown(); }\n"
        + "  public void returnsInput() {\n"
        + "    assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "  public void testSibling() { assertTrue(true); }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    // The conventional JUnit 3 shape: a String constructor calling super, and a suite() factory.
    private static final String JUNIT3_STRING_CONSTRUCTOR_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest extends junit.framework.TestCase {\n"
        + "  public SubjectTest(String testName) { super(testName); }\n"
        + "  public void returnsInput() {\n"
        + "    assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    private static final String JUNIT4_LIFECYCLE_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Before public void setUp() {}\n"
        + "  @org.junit.jupiter.api.AfterEach public void after() {}\n"
        + "  @org.junit.BeforeClass public void beforeClass() {}\n"
        + "  @org.junit.AfterClass public void afterClass() {}\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    private static final String SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    private static final String RUN_WITH_SOURCE = ""
        + "package example;\n"
        + "@org.junit.runner.RunWith(org.mockito.runners.MockitoJUnitRunner.class)\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(2, new Subject().id(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    private static final String HAMCREST_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertThat(new Subject().id(2), org.hamcrest.CoreMatchers.is(2));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int id(int x) { return x; }\n"
        + "}\n";

    private static final String TRY_FAIL_CATCH_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    try { new Subject().reject(2); org.junit.Assert.fail(); }\n"
        + "    catch (IllegalArgumentException e) { org.junit.Assert.assertEquals(\"bad input\", e.getMessage()); }\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  int reject(int x) { if (x > 0) throw new IllegalArgumentException(\"bad input\"); return x; }\n"
        + "}\n";

    private static final String PARSE_SOURCE = ""
        + "package example;\n"
        + "public class SubjectTest {\n"
        + "  @org.junit.Test public void returnsInput() {\n"
        + "    org.junit.Assert.assertEquals(\"42\", new Subject().id(\"42\"));\n"
        + "  }\n"
        + "}\n"
        + "class Subject {\n"
        + "  java.lang.String id(java.lang.String s) { return s; }\n"
        + "}\n";

    private static final class Scenario {
        private final Launcher launcher;
        private final CtClass<?> testClass;
        private final CtMethod<?> testMethod;
        private final CtInvocation<?> assertion;
        private final GeneralizationRecipe recipe;

        private Scenario(
            Launcher launcher,
            CtClass<?> testClass,
            CtMethod<?> testMethod,
            CtInvocation<?> assertion,
            GeneralizationRecipe recipe
        ) {
            this.launcher = launcher;
            this.testClass = testClass;
            this.testMethod = testMethod;
            this.assertion = assertion;
            this.recipe = recipe;
        }
    }
}
