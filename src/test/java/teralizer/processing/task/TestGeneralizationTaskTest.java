package teralizer.processing.task;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TestGeneralizationTaskTest {
    @Example
    void skipsConcreteReceiverWhenMappingInstanceMethodArguments() {
        List<MethodParameter> parameters = Arrays.asList(new MethodParameter("double", "x"));
        List<MethodArgument> values = Arrays.asList(
            new MethodArgument("org.example.Subject", "org.example.Subject@1"),
            new MethodArgument("double", "2.0")
        );

        Map<String, MethodArgument> mapped = TestGeneralizationTask.mapTestedMethodArguments(parameters, values);

        Assert.assertEquals("2.0", mapped.get("x").getValue());
        Assert.assertEquals("double", mapped.get("x").getType());
    }
}
