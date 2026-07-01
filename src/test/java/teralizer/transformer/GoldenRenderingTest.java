package teralizer.transformer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.*;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GoldenRenderingTest {
    @Example
    void currentRepresentativeModelsRenderAsTheBaseline() throws Exception {
        Map<String, String> expected = readBaseline();
        Map<String, String> actual = new LinkedHashMap<>();
        for (Map.Entry<String, ModelCase> entry : cases().entrySet()) {
            actual.put(entry.getKey(), entry.getValue().transformer.transform(entry.getValue().model));
        }
        Assert.assertEquals(expected, actual);
    }

    private static Map<String, ModelCase> cases() {
        Map<String, ModelCase> cases = new LinkedHashMap<>();
        cases.put("math.sqrt.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("x"), Operator.SQRT, null)));
        cases.put("math.pow.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("x"), Operator.POW, new ConstantReal(2.0))));
        cases.put("math.atan2.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Operation(new VariableReal("y"), Operator.ATAN2, new VariableReal("x"))));
        cases.put("boolean.path.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("b", "boolean")),
            new Operation(new VariableInteger("b"), Operator.NE, new ConstantInteger(0))));
        cases.put("char.bound.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("c", "char")),
            new Operation(new VariableInteger("c"), Operator.GT, new ConstantInteger(64))));
        cases.put("string.transform.invocation", new ModelCase(
            new ModelToJavaTransformer(),
            new Invocation(new VariableString("s"), null, "trim", Collections.emptyList())));
        return cases;
    }

    private static Map<String, String> readBaseline() throws Exception {
        try (Reader reader = new InputStreamReader(
            GoldenRenderingTest.class.getResourceAsStream("/golden/rendering-baseline.json"),
            StandardCharsets.UTF_8)) {
            Type type = new TypeToken<LinkedHashMap<String, String>>() {}.getType();
            return new Gson().fromJson(reader, type);
        }
    }

    private static final class ModelCase {
        final ModelToJavaTransformer transformer;
        final Model model;

        ModelCase(ModelToJavaTransformer transformer, Model model) {
            this.transformer = transformer;
            this.model = model;
        }
    }
}
