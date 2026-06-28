package teralizer.util;

import net.jqwik.api.Example;
import org.junit.Assert;

/**
 * The SPF model/native-peer classpath is prepended to the project classpath so JPF
 * resolves model classes (e.g. {@code FastMath.abs}) from the symbc build before the
 * project's own classes. The literal must live in one place — {@link Configuration} —
 * so {@code JpfInstrumentationTask} and the config template test reference a single
 * source of truth rather than duplicating the string.
 */
public class ConfigurationJpfSymbcClasspathTest {

    @Example
    void exposesSymbcModelClasspathConstant() {
        Assert.assertNotNull(Configuration.JPF_SYMBC_MODEL_CLASSPATH);
        Assert.assertFalse(Configuration.JPF_SYMBC_MODEL_CLASSPATH.isEmpty());
    }

    @Example
    void symbcModelClasspathPointsAtSymbcBuildClasses() {
        Assert.assertEquals("${jpf-symbc}/build/classes", Configuration.JPF_SYMBC_MODEL_CLASSPATH);
    }
}
