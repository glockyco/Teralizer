package teralizer.processing.task;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.ReferenceValue;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;

public class TestGeneralizationTaskTest {
    @Example
    void skipsConcreteReceiverWhenMappingInstanceMethodArguments() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));
        // SPF stores the instance receiver as the first input value. It is an opaque, unrenderable
        // ReferenceValue that must be offset-skipped so the declared parameter maps to the real
        // argument, never to the receiver.
        List<Value> values = Arrays.asList(
            new ReferenceValue("org.example.Subject"),
            new PrimitiveValue("double", 2.0)
        );

        Map<String, Value> mapped = TestGeneralizationTask.mapTestedMethodArguments(parameters, values);

        Assert.assertEquals(1, mapped.size());
        Assert.assertEquals("double", mapped.get("x").getJavaType());
        Assert.assertEquals(Double.valueOf(2.0), ((PrimitiveValue) mapped.get("x")).getValue());
    }

    @Example
    void recoversTypedTemporaryParametersFromInputAndOutputModels() {
        List<MethodParameter> declared = Arrays.asList(new MethodParameter("int", "x"));
        Model input = new Operation(
            new Variable("INT_1", TypeDomain.INTEGER),
            Operator.GT,
            new Constant(0L, TypeDomain.INTEGER));
        Model output = new Invocation(
            new Variable("STR_2", TypeDomain.STRING),
            null,
            "trim",
            java.util.Collections.emptyList());

        List<MethodParameter> recovered = TestGeneralizationTask.collectTemporaryParameters(input, output, declared);

        Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("INT_1") && p.getType().equals("int")));
        Assert.assertTrue(recovered.stream().anyMatch(p -> p.getName().equals("STR_2") && p.getType().equals("java.lang.String")));
        Assert.assertFalse(recovered.stream().anyMatch(p -> p.getName().equals("x")));
    }
}
