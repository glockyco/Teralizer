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
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Constant;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.InputGenerationPlanner;
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
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(SOURCE, "SubjectTest.java"));
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

    private static VelocityEngine velocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");
        VelocityEngine velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
        return velocityEngine;
    }

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
