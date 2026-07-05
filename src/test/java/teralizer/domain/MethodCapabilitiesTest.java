package teralizer.domain;

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
        Assert.assertTrue(MethodCapabilities.isOutputRenderable("length"));
        Assert.assertTrue(MethodCapabilities.isSupported("indexOf"));
        Assert.assertTrue(MethodCapabilities.isSupported("lastIndexOf"));
        Assert.assertFalse(MethodCapabilities.isOutputRenderable("indexOf"));
        Assert.assertFalse(MethodCapabilities.isOutputRenderable("lastIndexOf"));
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

    @Example
    void parsePredicateHelpersAreStaticBooleanPredicatesForRenderingAndGeneration() {
        for (String method : new String[] {"isInteger", "isLong", "isFloat", "isDouble"}) {
            MethodCapability capability = MethodCapabilities.get(method);

            Assert.assertNotNull(method, capability);
            Assert.assertEquals(method, capability.method);
            Assert.assertEquals(method, "ParsePredicates", capability.staticQualifier);
            Assert.assertNull(method, capability.receiverDomain);
            Assert.assertEquals(method, TypeDomain.BOOLEAN, capability.returnDomain);
            Assert.assertTrue(method, capability.inputGeneratable);
            Assert.assertTrue(method, capability.outputRenderable);
        }
    }

    @Example
    void capabilitiesDeclareReturnDomains() {
        for (String method : new String[] {"equals", "equalsIgnoreCase", "startsWith", "endsWith", "contains", "isEmpty"}) {
            Assert.assertEquals(method, TypeDomain.BOOLEAN, MethodCapabilities.get(method).returnDomain);
        }
        for (String method : new String[] {"trim", "replace", "toLowerCase", "toUpperCase", "concat"}) {
            Assert.assertEquals(method, TypeDomain.STRING, MethodCapabilities.get(method).returnDomain);
        }
        Assert.assertEquals(TypeDomain.STRING, MethodCapabilities.get("valueOf").returnDomain);
        Assert.assertEquals(TypeDomain.REAL, MethodCapabilities.get("sqrt").returnDomain);
        Assert.assertEquals(TypeDomain.INTEGER, MethodCapabilities.get("length").returnDomain);
        Assert.assertTrue(MethodCapabilities.get("length").outputRenderable);
    }

    @Example
    void capabilitiesDeclareInputConstraintKinds() {
        Assert.assertEquals(MethodCapability.InputConstraintKind.EQUALITY, MethodCapabilities.get("equals").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.PREFIX, MethodCapabilities.get("startsWith").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.SUFFIX, MethodCapabilities.get("endsWith").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.CONTAINS, MethodCapabilities.get("contains").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.EMPTY, MethodCapabilities.get("isEmpty").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.NONE, MethodCapabilities.get("trim").inputConstraintKind);
        Assert.assertEquals(MethodCapability.InputConstraintKind.NONE, MethodCapabilities.get("replace").inputConstraintKind);
    }
}
