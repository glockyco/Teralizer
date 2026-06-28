package teralizer.spoon.generalization;

import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The recorder is built two ways: a Spoon class for production codegen and a source
 * string for tests. Both paths must reuse the single {@code createResetBody} /
 * {@code createRecordBody} / {@code createEscapeValueBody} helpers so the behavior
 * cannot drift between production and test paths. These tests pin that contract by
 * asserting the text source embeds exactly the bodies the Spoon path renders.
 */
public class JqwikValueRecorderEquivalenceTest {

    private static final Path LOG_PATH = Paths.get("/tmp/jqwik-values/eq.tsv");

    @Example
    void textSourceEmbedsTheSharedResetBody() throws Exception {
        assertBothPathsEmbedSharedBody("createResetBody", LOG_PATH, true);
    }

    @Example
    void textSourceEmbedsTheSharedRecordBody() throws Exception {
        assertBothPathsEmbedSharedBody("createRecordBody", LOG_PATH, true);
    }

    @Example
    void textSourceEmbedsTheSharedEscapeValueBody() throws Exception {
        assertBothPathsEmbedSharedBody("createEscapeValueBody", null, true);
    }

    @Example
    void spoonAndTextPathsDeclareTheSameMethods() {
        Factory factory = new Launcher().getFactory();
        CtClass<?> spoonClass = JqwikValueRecorderFactory.createRecorderClass(factory, LOG_PATH);
        CtClass<?> textClass = Launcher.parseClass(
            JqwikValueRecorderFactory.createRecorderSource(LOG_PATH));

        List<String> spoonMethods = signatures(spoonClass);
        List<String> textMethods = signatures(textClass);

        Assert.assertEquals("recorder method set drifted between Spoon and text paths",
            spoonMethods, textMethods);
    }

    /**
     * Both the text source and the Spoon-built class must embed the same shared body
     * snippet, so a change to either path that inlines a different body fails here.
     * Spoon pretty-prints snippets (resolving FQNs, reflowing whitespace), so compare
     * a whitespace/FQN-normalized form rather than a verbatim substring match.
     */
    private static void assertBothPathsEmbedSharedBody(String helper, Path path, boolean reindented) throws Exception {
        String sharedBody = invokeHelper(helper, path);
        String normalizedShared = normalize(sharedBody);

        String textSource = JqwikValueRecorderFactory.createRecorderSource(LOG_PATH);
        Assert.assertTrue("text source must embed the shared " + helper + " body",
            normalize(textSource).contains(normalizedShared));

        Factory factory = new Launcher().getFactory();
        CtClass<?> spoonClass = JqwikValueRecorderFactory.createRecorderClass(factory, LOG_PATH);
        String spoonSource = spoonClass.toString();
        Assert.assertTrue("Spoon class must embed the shared " + helper + " body",
            normalize(spoonSource).contains(normalizedShared));
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", " ")
            .replace("java.nio.file.Path", "Path")
            .replace("java.nio.file.Paths", "Paths")
            .replace("java.nio.file.Files", "Files")
            .replace("java.io.IOException", "IOException")
            .replace("java.lang.reflect.Field", "Field")
            .replace("java.util.Collections", "Collections")
            .replace("java.nio.charset.StandardCharsets", "StandardCharsets")
            .replace("java.nio.file.StandardOpenOption", "StandardOpenOption")
            .replace("java.lang.String", "String")
            .replace("java.lang.Object", "Object")
            .replace("java.util.concurrent.ThreadLocalRandom", "ThreadLocalRandom")
            .trim();
    }

    private static List<String> signatures(CtClass<?> ctClass) {
        return ctClass.getMethods().stream()
            .map(CtMethod::getSignature)
            .sorted()
            .collect(Collectors.toList());
    }

    private static String invokeHelper(String name, Path path) throws Exception {
        Method m = path == null
            ? JqwikValueRecorderFactory.class.getDeclaredMethod(name)
            : JqwikValueRecorderFactory.class.getDeclaredMethod(name, Path.class);
        m.setAccessible(true);
        return path == null ? (String) m.invoke(null) : (String) m.invoke(null, path);
    }
}
