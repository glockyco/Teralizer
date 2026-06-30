package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.Value;
import teralizer.domain.MethodParameter;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

import java.lang.reflect.Method;
import java.util.Collections;

public class BooleanSupplierRenderingTest {

    @Example
    void baselineBooleanFirstValueUsesLiteral() throws Exception {
        Method createArbitrary = BaselineTestParametersSupplierFactory.class.getDeclaredMethod("createArbitrary", Value.class);
        createArbitrary.setAccessible(true);

        String body = (String) createArbitrary.invoke(null, new PrimitiveValue("boolean", true));

        Assert.assertEquals("return net.jqwik.api.Arbitraries.just(true)", body);
    }

    @Example
    void naiveBooleanFirstValueRendersAsLiteralInTupleSeed() {
        MethodParameter value = new MethodParameter("boolean", "value");
        CtClass<?> supplierClass = NaiveTestParametersSupplierFactory.createSupplierClass(
            new Launcher().getFactory(),
            Collections.singletonList(value),
            Collections.singletonMap("value", new PrimitiveValue("boolean", false)),
            null
        );

        // Boolean originals must render as the literal `false` (not `0`), seeded once at the tuple level.
        Assert.assertTrue("boolean original must render as the false literal in the tuple seed",
            supplierClass.toString().contains("(boolean) (false)"));
    }
}
