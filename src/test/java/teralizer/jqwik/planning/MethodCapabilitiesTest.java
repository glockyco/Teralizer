package teralizer.jqwik.planning;

import net.jqwik.api.Example;
import org.junit.Assert;

public class MethodCapabilitiesTest {

    @Example
    void currentSoundStringMethodsAreRegistered() {
        Assert.assertTrue(MethodCapabilities.isSupported("equals"));
        Assert.assertTrue(MethodCapabilities.isOutputRenderable("equalsIgnoreCase"));
        Assert.assertFalse(MethodCapabilities.isInputGeneratable("equalsIgnoreCase"));
        Assert.assertTrue(MethodCapabilities.isInputGeneratable("startsWith"));
    }

    @Example
    void unsupportedStringMethodsAreAbsent() {
        Assert.assertFalse(MethodCapabilities.isSupported("compareTo"));
        Assert.assertFalse(MethodCapabilities.isOutputRenderable("substring"));
        Assert.assertNull(MethodCapabilities.get("compareTo"));
    }

    @Example
    void instanceMethodsHaveNoStaticQualifier() {
        MethodCapability isEmpty = MethodCapabilities.get("isEmpty");
        Assert.assertNotNull(isEmpty);
        Assert.assertEquals("isEmpty", isEmpty.method);
        Assert.assertNull(isEmpty.staticQualifier);
    }

    @Example
    void currentStaticMathFunctionsAreRegisteredForRendering() {
        MethodCapability sqrt = MethodCapabilities.get("sqrt");
        Assert.assertNotNull(sqrt);
        Assert.assertEquals("sqrt", sqrt.method);
        Assert.assertEquals("java.lang.Math", sqrt.staticQualifier);
        Assert.assertFalse(sqrt.inputGeneratable);
        Assert.assertTrue(sqrt.outputRenderable);
    }
}
