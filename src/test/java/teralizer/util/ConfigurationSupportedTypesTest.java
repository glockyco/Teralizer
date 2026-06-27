package teralizer.util;

import net.jqwik.api.Example;
import org.junit.Assert;

public class ConfigurationSupportedTypesTest {

    @Example
    void supportsCharacterAndBooleanTypes() {
        Assert.assertTrue(Configuration.SUPPORTED_TYPES.contains("char"));
        Assert.assertTrue(Configuration.SUPPORTED_TYPES.contains("java.lang.Character"));
        Assert.assertTrue(Configuration.SUPPORTED_TYPES.contains("boolean"));
        Assert.assertTrue(Configuration.SUPPORTED_TYPES.contains("java.lang.Boolean"));
    }
}
