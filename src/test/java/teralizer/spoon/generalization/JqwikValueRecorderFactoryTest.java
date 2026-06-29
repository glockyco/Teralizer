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
    void rendersRecorderWithIdentityAndValidJava() {
        String source = render();

        CtClass<?> recorderClass = Launcher.parseClass(source);
        Assert.assertEquals("JqwikValueRecorder", recorderClass.getSimpleName());

        Assert.assertTrue(source.contains("GENERALIZATION_ID = 7L"));
        Assert.assertTrue(source.contains("VARIANT = \"IMPROVED\""));
        Assert.assertTrue(source.contains("TEST_CASE_NAME = \"isAsciiPrintable\""));
        Assert.assertTrue(source.contains("/tmp/jqwik-data"));
        Assert.assertTrue(source.contains(".values.tsv"));
        Assert.assertTrue(source.contains(".outcome.json"));
    }

    @Example
    void rendersFilterMissesHookThatRemapsToSuccess() {
        String source = render();

        Assert.assertTrue(source.contains("public static final class LimitedFilterMissesHook"));
        Assert.assertTrue(source.contains("implements net.jqwik.api.lifecycle.AroundPropertyHook"));
        Assert.assertTrue(source.contains("net.jqwik.api.TooManyFilterMissesException.class::isInstance"));
        Assert.assertTrue(source.contains("sawDistinctNonSeedTuple"));
        Assert.assertTrue(source.contains("raw.mapToSuccessful()"));
    }

    @Example
    void defaultsToInMemoryOnlyDiagnostics() {
        String source = render();

        // A missing diagnostics-mode property must not write sidecars (e.g. an unplumbed PIT minion).
        Assert.assertTrue(source.contains("return DiagnosticsMode.IN_MEMORY_ONLY;"));
    }

    @Example
    void escapesSurrogateCharactersToKeepValueLogValidUtf8() {
        String source = render();

        // A generated char in 0xD800-0xDFFF is a lone surrogate; writing it verbatim to a UTF-8
        // value log throws MalformedInputException. The recorder must escape it like a control char.
        Assert.assertTrue(source.contains("Character.isSurrogate(ch)"));
    }

    private static String render() {
        return JqwikValueRecorderFactory.render(
            velocityEngine(),
            Paths.get("/tmp/jqwik-data"),
            3L,
            7L,
            "IMPROVED",
            "isAsciiPrintable"
        );
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
