package teralizer.util;

import net.jqwik.api.Example;
import org.junit.Assert;

public class TypeCapabilityTest {

    @Example
    void supportsGeneratedInputForAllSixteenLegacyTypes() {
        // Integer domain
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("byte"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Byte"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("short"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Short"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("int"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Integer"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("long"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Long"));
        // Real domain
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("float"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Float"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("double"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Double"));
        // Char domain
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("char"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Character"));
        // Boolean domain
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("boolean"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.Boolean"));
    }

    @Example
    void rejectsGeneratedInputForUnsupportedTypes() {
        Assert.assertFalse(TypeCapability.supportsGeneratedInput("int[]"));
        Assert.assertFalse(TypeCapability.supportsGeneratedInput("Integer"));   // simple wrapper, maps to OBJECT
        Assert.assertFalse(TypeCapability.supportsGeneratedInput("java.util.List"));
        Assert.assertFalse(TypeCapability.supportsGeneratedInput(null));
    }

    @Example
    void supportsStringInputButNotReturnYet() {
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("String"));
        Assert.assertTrue(TypeCapability.supportsGeneratedInput("java.lang.String"));
        // A symbolic String return oracle is not captured yet, so String is input-only.
        Assert.assertFalse(TypeCapability.supportsReturnValue("String"));
        Assert.assertFalse(TypeCapability.supportsReturnValue("java.lang.String"));
    }

    @Example
    void supportsReturnValueForRepresentativeSubset() {
        Assert.assertTrue(TypeCapability.supportsReturnValue("int"));
        Assert.assertTrue(TypeCapability.supportsReturnValue("boolean"));
        Assert.assertFalse(TypeCapability.supportsReturnValue("String"));
        Assert.assertFalse(TypeCapability.supportsReturnValue("int[]"));
    }
}
