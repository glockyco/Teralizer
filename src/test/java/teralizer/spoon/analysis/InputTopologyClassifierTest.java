package teralizer.spoon.analysis;

import java.util.List;
import java.util.Optional;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class InputTopologyClassifierTest {

    @Example
    void inlineCtorReceiver() {
        TopologyInput input = inputOf(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().isPrime(7)); }\n"
            + "}");

        Assert.assertEquals(MutResolution.ActualShape.CTOR_RECEIVER_CALL,
            InputTopologyClassifier.classifyShape(input.actual));
        Assert.assertEquals(MutResolution.ReceiverProvenance.INLINE_CTOR,
            InputTopologyClassifier.receiverProvenance(input.actual, input.testMethod, input.assertion));
    }

    @Example
    void localCtorReceiver_cleanVsMutated() {
        TopologyInput clean = inputOf(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); org.junit.Assert.assertEquals(0, s.getTotal()); }\n"
            + "}");
        Assert.assertEquals(MutResolution.ActualShape.SINGLE_CALL,
            InputTopologyClassifier.classifyShape(clean.actual));
        Assert.assertEquals(MutResolution.ReceiverProvenance.LOCAL_CTOR,
            InputTopologyClassifier.receiverProvenance(clean.actual, clean.testMethod, clean.assertion));

        TopologyInput mutated = inputOf(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}");
        Assert.assertEquals(MutResolution.ReceiverProvenance.LOCAL_CTOR_MUTATED,
            InputTopologyClassifier.receiverProvenance(mutated.actual, mutated.testMethod, mutated.assertion));
    }

    @Example
    void fieldReceiver_andOperatorShape() {
        TopologyInput field = inputOf(
            "public class SubjectTest {\n"
            + "  Subject sut = new Subject();\n"
            + "  public void t() { org.junit.Assert.assertEquals(0, sut.getTotal()); }\n"
            + "}");
        Assert.assertEquals(MutResolution.ReceiverProvenance.FIELD,
            InputTopologyClassifier.receiverProvenance(field.actual, field.testMethod, field.assertion));

        TopologyInput op = inputOf(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().gcd(6, 9) > 0); }\n"
            + "}");
        Assert.assertEquals(MutResolution.ActualShape.OPERATOR_COMPOSITE,
            InputTopologyClassifier.classifyShape(op.actual));
    }

    @Example
    void chainedCalls() {
        TopologyInput input = inputOf(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().compute(5).isEmpty()); }\n"
            + "}");

        Assert.assertEquals(MutResolution.ActualShape.CHAINED_CALLS_END0ARG,
            InputTopologyClassifier.classifyShape(input.actual));
        Assert.assertEquals(MutResolution.ReceiverProvenance.INLINE_CTOR,
            InputTopologyClassifier.receiverProvenance(input.actual, input.testMethod, input.assertion));
    }

    private static TopologyInput inputOf(String testSource) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        launcher.addInputResource(new VirtualFile(MethodUnderTestResolverTest.SUBJECT_SOURCE, "Subject.java"));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        List<CtInvocation<?>> assertions = TestAnalysis.findAllAsserts(testMethod);
        CtInvocation<?> assertion = assertions.get(0);
        return new TopologyInput(testMethod, assertion, actualExpression(assertion));
    }

    private static CtExpression<?> actualExpression(CtInvocation<?> assertion) {
        Optional<Integer> index = TestAnalysis.getActualParameterIndex(assertion);
        return index.isPresent() ? assertion.getArguments().get(index.get()) : null;
    }

    private static final class TopologyInput {
        final CtMethod<?> testMethod;
        final CtInvocation<?> assertion;
        final CtExpression<?> actual;

        TopologyInput(CtMethod<?> testMethod, CtInvocation<?> assertion, CtExpression<?> actual) {
            this.testMethod = testMethod;
            this.assertion = assertion;
            this.actual = actual;
        }
    }
}
