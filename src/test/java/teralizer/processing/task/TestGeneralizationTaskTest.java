package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.MethodParameter;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.ReferenceValue;
import teralizer.domain.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
}
