package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.Error;
import teralizer.domain.Model;

public class ErrorJsonRoundTripTest {
    @Example
    void errorRoundTripsViaJson() {
        Error original = new Error("java.lang.ArithmeticException", "/ by zero");

        String json = new ModelToJsonTransformer().transform(original);
        Model result = new JsonToModelTransformer().transform(json);

        Assert.assertEquals(original, result);
    }
}
