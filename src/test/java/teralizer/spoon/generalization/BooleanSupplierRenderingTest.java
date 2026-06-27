package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;

import java.lang.reflect.Method;
import java.util.Optional;

public class BooleanSupplierRenderingTest {

    @Example
    void baselineBooleanFirstValueUsesLiteral() throws Exception {
        Method createArbitrary = BaselineTestParametersSupplierFactory.class.getDeclaredMethod("createArbitrary", MethodArgument.class);
        createArbitrary.setAccessible(true);

        String body = (String) createArbitrary.invoke(null, new MethodArgument("boolean", "1"));

        Assert.assertEquals("return net.jqwik.api.Arbitraries.just(true)", body);
    }

    @Example
    void naiveBooleanFirstValueUsesLiteral() throws Exception {
        Method createArbitrary = NaiveTestParametersSupplierFactory.class.getDeclaredMethod(
            "createArbitrary",
            MethodParameter.class,
            Optional.class
        );
        createArbitrary.setAccessible(true);

        String body = (String) createArbitrary.invoke(
            null,
            new MethodParameter("boolean", "value"),
            Optional.of(new MethodArgument("boolean", "0"))
        );

        Assert.assertEquals("return new FirstValueArbitrary<Boolean>(false, net.jqwik.api.Arbitraries.of(true, false))", body);
    }
}
