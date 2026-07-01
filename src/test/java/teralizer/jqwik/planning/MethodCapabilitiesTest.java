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
    void outputOnlyStringTransformsAreRegisteredForRendering() {
        for (String method : new String[] {"trim", "replace", "toLowerCase", "toUpperCase"}) {
            Assert.assertTrue(method, MethodCapabilities.isSupported(method));
            Assert.assertTrue(method, MethodCapabilities.isOutputRenderable(method));
            Assert.assertFalse(method, MethodCapabilities.isInputGeneratable(method));
            Assert.assertNull(method, MethodCapabilities.get(method).staticQualifier);
        }
    }

    @Example
    void stringNumericMethodsHandledBySpfRemainSupportedForFiltering() {
        Assert.assertTrue(MethodCapabilities.isSupported("length"));
        Assert.assertTrue(MethodCapabilities.isSupported("indexOf"));
        Assert.assertFalse(MethodCapabilities.isOutputRenderable("length"));
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
