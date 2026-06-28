package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;

import java.util.List;
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
        Factory factory = new Launcher().getFactory();
        CtClass<?> firstValueClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass();

        CtMethod<?> generator = firstValueClass.getMethodsByName("generator").get(0);
        String generatorBody = generator.getBody().toString();

        Assert.assertTrue(
            "generator(int) must return a Shrinkable wrapping firstValue before delegating; "
                + "currently delegates directly, so the seed input is not guaranteed in normal generation",
            generatorBody.contains("firstValue"));
    }
}
