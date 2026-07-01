package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ConstantString;
import teralizer.domain.Invocation;
import teralizer.domain.Model;
import teralizer.domain.Not;
import teralizer.domain.VariableString;

import java.util.Collections;

public class InvocationJsonRoundTripTest {

    @Example
    void invocationAndNotSerializeWithTypeAndRoundTrip() {
        Model model = new Not(new Invocation(
            new VariableString("value"),
            null,
            "equals",
            Collections.singletonList(new ConstantString("foo"))));

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"_type\": \"Not\""));
        Assert.assertTrue(json, json.contains("\"_type\": \"Invocation\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }
}
