package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * {@link FirstValueArbitraryFactory} builds an arbitrary that prepends a seed value so
 * the original concrete input is exercised before random exploration. That injection
 * must reach the normal generation path: {@code generator(int)} must emit {@code firstValue}
 * as its first sample, then delegate — relying only on {@code edgeCases()} leaves the seed
 * input unexercised under limited {@code tries} / non-edge-case generation modes.
 */
public class FirstValueArbitraryFirstValueTest {

    @Example
    void generatorEmitsFirstValueBeforeDelegating() {
        CtClass<?> firstValueClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine());

        CtMethod<?> generator = firstValueClass.getMethodsByName("generator").get(0);
        String generatorBody = generator.getBody().toString();

        Assert.assertTrue(
            "generator(int) must return a Shrinkable wrapping firstValue before delegating; "
                + "currently delegates directly, so the seed input is not guaranteed in normal generation",
            generatorBody.contains("firstValue"));
    }

    @Example
    void exhaustiveDelegatesToWrappedArbitrary() {
        CtClass<?> firstValueClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine());

        List<CtMethod<?>> exhaustiveMethods = firstValueClass.getMethodsByName("exhaustive");

        Assert.assertEquals(
            "FirstValueArbitrary must preserve the delegate's exhaustive generator so jqwik AUTO mode can stop "
                + "after finite ranges are covered instead of falling back to randomized tries",
            1,
            exhaustiveMethods.size());

        CtMethod<?> exhaustive = exhaustiveMethods.get(0);
        List<String> parameterNames = exhaustive.getParameters().stream()
            .map(CtParameter::getSimpleName)
            .collect(Collectors.toList());
        Assert.assertEquals(
            "exhaustive(long) must keep jqwik's maxNumberOfSamples parameter",
            java.util.Collections.singletonList("maxNumberOfSamples"),
            parameterNames);
        Assert.assertTrue(
            "exhaustive(long) must delegate the same maxNumberOfSamples value to the wrapped arbitrary",
            exhaustive.getBody().toString().contains("delegate.exhaustive(maxNumberOfSamples)"));
    }

    private static VelocityEngine velocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");  // match production (TestGeneralizationRunner)

        VelocityEngine velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
        return velocityEngine;
    }
}
