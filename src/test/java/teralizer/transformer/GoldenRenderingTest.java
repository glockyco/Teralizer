package teralizer.transformer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.*;
import teralizer.domain.Constant;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

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
            new Invocation(null, "java.lang.Math", "sqrt", Collections.singletonList(new Variable("x", TypeDomain.REAL)))));
        cases.put("math.pow.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Invocation(null, "java.lang.Math", "pow", Arrays.asList(new Variable("x", TypeDomain.REAL), new Constant(2.0, TypeDomain.REAL)))));
        cases.put("math.atan2.operation", new ModelCase(
            new ModelToJavaTransformer(),
            new Invocation(null, "java.lang.Math", "atan2", Arrays.asList(new Variable("y", TypeDomain.REAL), new Variable("x", TypeDomain.REAL)))));
        cases.put("boolean.path.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("b", "boolean")),
            new Operation(new Variable("b", TypeDomain.INTEGER), Operator.NE, new Constant((long) 0, TypeDomain.INTEGER))));
        cases.put("char.bound.predicate", new ModelCase(
            new ModelToJavaTransformer(Collections.singletonMap("c", "char")),
            new Operation(new Variable("c", TypeDomain.INTEGER), Operator.GT, new Constant((long) 64, TypeDomain.INTEGER))));
        cases.put("string.transform.invocation", new ModelCase(
            new ModelToJavaTransformer(),
            new Invocation(new Variable("s", TypeDomain.STRING), null, "trim", Collections.emptyList())));
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
