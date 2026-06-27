package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;

import java.nio.file.Paths;

public class JqwikValueRecorderFactoryTest {

    @Example
    void rendersRecorderWithStableValueFile() {
        String source = JqwikValueRecorderFactory.createRecorderSource(Paths.get("/tmp/jqwik-values/7.tsv"));

        Assert.assertTrue(source.contains("public static class JqwikValueRecorder"));
        Assert.assertTrue(source.contains("private static final java.nio.file.Path VALUE_LOG_PATH"));
        Assert.assertTrue(source.contains("/tmp/jqwik-values/7.tsv"));
        Assert.assertTrue(source.contains("field.getName()"));
        Assert.assertTrue(source.contains("escapeValue(field.get(parameters))"));
        Assert.assertTrue(source.contains("private static String escapeValue(Object value)"));
        Assert.assertTrue(source.contains("case '\\n'"));
        Assert.assertTrue(source.contains("case '\\t'"));
        Assert.assertTrue(source.contains("field.setAccessible(true)"));
        Assert.assertTrue(source.contains("private static boolean initialized = false"));
        Assert.assertTrue(source.contains("public static synchronized void reset()"));
        Assert.assertTrue(source.contains("java.nio.file.Files.deleteIfExists(VALUE_LOG_PATH)"));
        Assert.assertTrue(source.contains("java.nio.file.StandardOpenOption.APPEND"));
        Assert.assertTrue(source.contains("field.isSynthetic()"));
        Assert.assertTrue(source.contains("field.getName().startsWith(\"$\")"));
    }
}
