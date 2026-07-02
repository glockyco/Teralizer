package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Constant;
import teralizer.domain.Model;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

/**
 * JSON round-trip for typed leaves as top-level model nodes.
 * {@code InvocationJsonRoundTripTest} covers leaves nested inside {@code Invocation}/{@code Not};
 * these pin the leaf-as-root path through {@link ModelToJsonTransformer}/{@link JsonToModelTransformer}.
 */
public class TypedLeafJsonRoundTripTest {

    @Example
    void variableRoundTripsAsTopLevelModel() {
        Model model = new Variable("x", TypeDomain.REAL);

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"_type\": \"Variable\""));
        Assert.assertTrue(json, json.contains("\"name\": \"x\""));
        Assert.assertTrue(json, json.contains("\"domain\": \"REAL\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }

    @Example
    void integerConstantRoundTripsAsTopLevelModel() {
        Model model = new Constant(42L, TypeDomain.INTEGER);

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"_type\": \"Constant\""));
        Assert.assertTrue(json, json.contains("\"domain\": \"INTEGER\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }

    @Example
    void realConstantRoundTripsAsTopLevelModel() {
        Model model = new Constant(3.14d, TypeDomain.REAL);

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"domain\": \"REAL\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }

    @Example
    void stringConstantRoundTripsAsTopLevelModel() {
        Model model = new Constant("hello", TypeDomain.STRING);

        String json = new ModelToJsonTransformer().transform(model);

        Assert.assertTrue(json, json.contains("\"domain\": \"STRING\""));
        Assert.assertTrue(json, json.contains("\"value\": \"hello\""));
        Assert.assertEquals(model, new JsonToModelTransformer().transform(json));
    }
}
