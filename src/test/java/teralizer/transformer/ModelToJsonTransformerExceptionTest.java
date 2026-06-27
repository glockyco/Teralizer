package teralizer.transformer;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.ExceptionModel;

public class ModelToJsonTransformerExceptionTest {
    @Example
    void serializesMissingExceptionMessageAsJsonNull() {
        String json = new ModelToJsonTransformer().transform(new ExceptionModel("java.lang.ArithmeticException", null));

        Assert.assertTrue(json.contains("\"class\": \"java.lang.ArithmeticException\""));
        Assert.assertTrue(json.contains("\"message\": null"));
    }
}
