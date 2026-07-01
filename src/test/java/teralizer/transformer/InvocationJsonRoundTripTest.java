package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Invocation;
import teralizer.domain.Model;
import teralizer.domain.Not;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

import java.util.Collections;

public class InvocationJsonRoundTripTest {

    @Example
    void staticMathInvocationRoundTrips() {
        Model model = new Invocation(
            null,
            "java.lang.Math",
            "pow",
            java.util.Arrays.asList(new Variable("x", TypeDomain.REAL), new Constant(2.0d, TypeDomain.REAL)));

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"receiver\": null"));
        Assert.assertTrue(json, json.contains("\"_type\": \"Variable\""));
        Assert.assertTrue(json, json.contains("\"_type\": \"Constant\""));
        Assert.assertTrue(json, json.contains("\"domain\": \"REAL\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }

    @Example
    void invocationAndNotSerializeWithTypeAndRoundTrip() {
        Model model = new Not(new Invocation(
            new Variable("value", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING))));

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"_type\": \"Not\""));
        Assert.assertTrue(json, json.contains("\"_type\": \"Invocation\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }
}
