package teralizer.jpf;

import java.util.Collections;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.CapturedException;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Constant;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public class OutputSpecClassifierTest {

    @Example
    void classifiesThrownOutputAsException() {
        CapturedInvocation invocation = new CapturedInvocation(
            Collections.emptyList(),
            CapturedOutput.ofThrow(new CapturedException("java.lang.IllegalArgumentException", "bad")),
            null,
            new Variable("ignored", TypeDomain.INTEGER));

        Assert.assertEquals(
            OutputSpecClassifier.OutputSpecClass.EXCEPTION,
            OutputSpecClassifier.classify(invocation));
    }

    @Example
    void classifiesNullModelOutputAsNullConcrete() {
        CapturedInvocation invocation = new CapturedInvocation(
            Collections.emptyList(),
            CapturedOutput.ofReturnValue(new PrimitiveValue("boolean", true)),
            null,
            null);

        Assert.assertEquals(
            OutputSpecClassifier.OutputSpecClass.NULL_CONCRETE,
            OutputSpecClassifier.classify(invocation));
    }

    @Example
    void classifiesAnyVariableInModelOutputAsSymbolic() {
        CapturedInvocation invocation = new CapturedInvocation(
            Collections.emptyList(),
            CapturedOutput.ofReturnValue(new PrimitiveValue("int", 4)),
            null,
            new Operation(
                new Variable("x", TypeDomain.INTEGER),
                Operator.PLUS,
                new Constant(1L, TypeDomain.INTEGER)));

        Assert.assertEquals(
            OutputSpecClassifier.OutputSpecClass.SYMBOLIC,
            OutputSpecClassifier.classify(invocation));
    }

    @Example
    void classifiesVariableFreeModelOutputAsConstant() {
        CapturedInvocation invocation = new CapturedInvocation(
            Collections.emptyList(),
            CapturedOutput.ofReturnValue(new PrimitiveValue("int", 4)),
            null,
            new Operation(
                new Constant(2L, TypeDomain.INTEGER),
                Operator.PLUS,
                new Constant(2L, TypeDomain.INTEGER)));

        Assert.assertEquals(
            OutputSpecClassifier.OutputSpecClass.CONSTANT,
            OutputSpecClassifier.classify(invocation));
    }
}
