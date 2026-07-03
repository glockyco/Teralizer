package teralizer.processing.task;

import java.util.LinkedHashMap;
import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.support.compiler.VirtualFile;
import teralizer.spoon.analysis.GeneralizableInput;
import teralizer.spoon.analysis.TestAnalysis;

public class InstrumentedLocalLiftingTest {

    private static final String SUBJECT_SOURCE =
        "public class Subject {\n"
        + "  public static int count(double compare, double[] values) { return values.length; }\n"
        + "  public double compute(int[] a, int[] b, double v) { return v; }\n"
        + "  public static int pure(int a, int b) { return a + b; }\n"
        + "}";

    @Example
    void liftsTestLocalArrayReferencedByClonedArgument() {
        // count(compare, perma): compare is a generalizable input (replaced by a wrapper
        // parameter); perma is a test-method local array cloned verbatim into the wrapper
        // body, where it does not resolve -- the spike's whole-project build breaker.
        CtInvocation<?> call = testedCallFrom(
            "public class SubjectTest {\n"
            + "  public void t() {\n"
            + "    double[] perma = new double[]{ 1.0, 2.0 };\n"
            + "    double compare = 0.5;\n"
            + "    org.junit.Assert.assertEquals(2, Subject.count(compare, perma));\n"
            + "  }\n"
            + "}");
        CtMethod<?> testedMethod = (CtMethod<?>) call.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, call);

        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted =
            JpfInstrumentationTask.collectLiftableLocals(testedMethod, call, inputs);

        Assert.assertEquals(1, lifted.size());
        CtVariableReference<?> ref = lifted.keySet().iterator().next();
        Assert.assertEquals("perma", ref.getSimpleName());
        Assert.assertEquals("double[]", lifted.get(ref).getQualifiedName());
    }

    @Example
    void liftsArrayReadComponents_notTheGeneralizableInput() {
        // compute(actual[i], predicted[i], inputValue): inputValue is generalizable
        // (replaced); the cloned array reads need actual, predicted, AND i lifted.
        CtInvocation<?> call = testedCallFrom(
            "public class SubjectTest {\n"
            + "  public void t() {\n"
            + "    int[] actual = { 1 };\n"
            + "    int[] predicted = { 1 };\n"
            + "    int i = 0;\n"
            + "    Subject s = new Subject();\n"
            + "    org.junit.Assert.assertEquals(0.5, s.compute(new int[]{ actual[i] }, new int[]{ predicted[i] }, 0.5), 0.0);\n"
            + "  }\n"
            + "}");
        CtMethod<?> testedMethod = (CtMethod<?>) call.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, call);

        LinkedHashMap<CtVariableReference<?>, CtTypeReference<?>> lifted =
            JpfInstrumentationTask.collectLiftableLocals(testedMethod, call, inputs);

        Assert.assertEquals(3, lifted.size());
        Assert.assertTrue(lifted.keySet().stream().anyMatch(r -> r.getSimpleName().equals("actual")));
        Assert.assertTrue(lifted.keySet().stream().anyMatch(r -> r.getSimpleName().equals("predicted")));
        Assert.assertTrue(lifted.keySet().stream().anyMatch(r -> r.getSimpleName().equals("i")));
    }

    @Example
    void scopeFreeCallLiftsNothing() {
        CtInvocation<?> call = testedCallFrom(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, Subject.pure(1, 2)); }\n"
            + "}");
        CtMethod<?> testedMethod = (CtMethod<?>) call.getExecutable().getDeclaration();
        List<GeneralizableInput> inputs = GeneralizableInput.derive(testedMethod, call);

        Assert.assertTrue(JpfInstrumentationTask.collectLiftableLocals(testedMethod, call, inputs).isEmpty());
    }

    @Example
    void targetAndLiftedLocalsAreAlwaysConcreteForSpf() {
        // The receiver and lifted locals carry the test's fixed environment; symbolizing
        // them would let SPF vary state the test does not control. A String _target_
        // was previously marked sym because String is an input-generatable type.
        Launcher launcher = new Launcher();
        CtParameter<?> target = launcher.getFactory().createParameter(
            null, launcher.getFactory().Type().STRING, "_target_");
        CtParameter<?> local = launcher.getFactory().createParameter(
            null, launcher.getFactory().Type().INTEGER_PRIMITIVE, "_local_i");
        CtParameter<?> input = launcher.getFactory().createParameter(
            null, launcher.getFactory().Type().INTEGER_PRIMITIVE, "n");

        Assert.assertEquals("con", JpfInstrumentationTask.symbolicMarker(target));
        Assert.assertEquals("con", JpfInstrumentationTask.symbolicMarker(local));
        Assert.assertEquals("sym", JpfInstrumentationTask.symbolicMarker(input));
    }

    private static CtInvocation<?> testedCallFrom(String testSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        launcher.addInputResource(new VirtualFile(SUBJECT_SOURCE, "Subject.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(CtClass.class::isInstance).stream()
            .map(e -> (CtClass<?>) e)
            .filter(c -> c.getSimpleName().equals("SubjectTest"))
            .findFirst()
            .orElseThrow(IllegalStateException::new);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        return (CtInvocation<?>) assertion.getArguments().get(1);
    }
}
