package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

import java.nio.file.Paths;
import java.util.Properties;

public class JqwikValueRecorderFactoryTest {

    @Example
    void rendersRecorderWithStableValueFile() {
        String source = JqwikValueRecorderFactory.render(velocityEngine(), Paths.get("/tmp/jqwik-values/7.tsv"));

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

    @Example
    void renderedRecorderIsValidParseableJava() {
        String source = JqwikValueRecorderFactory.render(velocityEngine(), Paths.get("/tmp/jqwik-values/7.tsv"));

        CtClass<?> recorderClass = Launcher.parseClass(source);

        Assert.assertEquals("JqwikValueRecorder", recorderClass.getSimpleName());
    }

    @Example
    void escapesSurrogateCharactersToKeepValueLogValidUtf8() {
        String source = JqwikValueRecorderFactory.render(velocityEngine(), Paths.get("/tmp/jqwik-values/7.tsv"));

        // A generated char in 0xD800-0xDFFF is a lone surrogate; writing it verbatim to a UTF-8
        // value log throws MalformedInputException. The recorder must escape it like a control char.
        Assert.assertTrue(source.contains("Character.isSurrogate(ch)"));
    }

    private static VelocityEngine velocityEngine() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");  // match production (TestGeneralizationRunner)

        VelocityEngine velocityEngine = new VelocityEngine(properties);
        velocityEngine.init();
        return velocityEngine;
    }
}
