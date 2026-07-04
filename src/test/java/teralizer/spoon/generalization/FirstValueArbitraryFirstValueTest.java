package teralizer.spoon.generalization;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import net.jqwik.api.Example;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;

/**
 * {@link FirstValueArbitraryFactory} builds the rendered arbitrary wrapper used by generated
 * jqwik properties to execute the original concrete tuple before random exploration.
 */
public class FirstValueArbitraryFirstValueTest {

    @Example
    void edgeCasesFirstRunsPrependedSeedOnceBeforeRandomValues() throws Exception {
        Class<?> propertyClass = compileGeneratedHarness("SeedCharacterizationProperty", ""
            + "public static final java.util.List<Integer> values = new java.util.ArrayList<Integer>();\n"
            + "@net.jqwik.api.Property(tries = 4, seed = \"0\", edgeCases = net.jqwik.api.EdgeCasesMode.FIRST, shrinking = net.jqwik.api.ShrinkingMode.OFF)\n"
            + "void property(@net.jqwik.api.ForAll(\"parameters\") TestParameters parameters) {\n"
            + "    values.add(Integer.valueOf(parameters.x));\n"
            + "}\n"
            + "@net.jqwik.api.Provide\n"
            + "net.jqwik.api.Arbitrary<TestParameters> parameters() {\n"
            + "    return new FirstValueArbitrary<TestParameters>(new TestParameters(2), new SequenceArbitrary(new int[]{100, 101, 102, 103}, null));\n"
            + "}\n");

        runJqwikProperties(propertyClass);

        Assert.assertEquals(
            "With EdgeCasesMode.FIRST, the prepended edge case executes first and the random phase does not force-emit it again.",
            Arrays.asList(2, 100, 101, 102),
            readStaticList(propertyClass, "values"));
    }

    @Example
    void edgeCasesFirstDeduplicatesSeedAndDelegateEdgeCasesBeforeRandomValues() throws Exception {
        Class<?> propertyClass = compileGeneratedHarness("EdgeCaseDeduplicationProperty", ""
            + "public static final java.util.List<Integer> values = new java.util.ArrayList<Integer>();\n"
            + "@net.jqwik.api.Property(tries = 6, seed = \"0\", edgeCases = net.jqwik.api.EdgeCasesMode.FIRST, shrinking = net.jqwik.api.ShrinkingMode.OFF)\n"
            + "void property(@net.jqwik.api.ForAll(\"parameters\") TestParameters parameters) {\n"
            + "    values.add(Integer.valueOf(parameters.x));\n"
            + "}\n"
            + "@net.jqwik.api.Provide\n"
            + "net.jqwik.api.Arbitrary<TestParameters> parameters() {\n"
            + "    return new FirstValueArbitrary<TestParameters>(new TestParameters(2), new SequenceArbitrary(new int[]{2, 1, 100, 101, 102, 103}, null, new int[]{2, 1}));\n"
            + "}\n");

        runJqwikProperties(propertyClass);

        Assert.assertEquals(
            "The seed and delegate edge cases execute once, and the random phase starts with fresh rows.",
            Arrays.asList(2, 1, 100, 101, 102, 103),
            readStaticList(propertyClass, "values"));
    }

    @Example
    void randomGeneratorSkipsSeenRowsWhileFreshRowsAreAvailable() throws Exception {
        Class<?> harnessClass = compileGeneratedHarness("DistinctRandomHarness", randomDrawMethods());

        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) invokeStatic(
            harnessClass,
            "drawRandomRows",
            new Class<?>[]{int.class, int[].class, int.class},
            Integer.valueOf(2),
            new int[]{2, 100, 100, 101, 101, 102, 102, 103},
            Integer.valueOf(4));

        Assert.assertEquals(
            Arrays.asList("x=100", "x=101", "x=102", "x=103"),
            rows);
    }

    @Example
    void randomGeneratorFallsBackToDuplicateWhenRetryBoundIsExhausted() throws Exception {
        Class<?> harnessClass = compileGeneratedHarness("FallbackRandomHarness", randomDrawMethods());

        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) invokeStatic(
            harnessClass,
            "drawRandomRows",
            new Class<?>[]{int.class, int[].class, int.class},
            Integer.valueOf(2),
            new int[]{2, 3, 3, 3, 3, 3, 3, 3, 3},
            Integer.valueOf(2));

        Assert.assertEquals(
            "The first random fresh row is kept, and the next draw degrades to a duplicate instead of looping forever.",
            Arrays.asList("x=3", "x=3"),
            rows);
    }

    @Example
    void exhaustiveDelegatesToWrappedArbitrary() throws Exception {
        CtClass<?> firstValueClass = FirstValueArbitraryFactory.createFirstValueArbitraryClass(velocityEngine());

        List<CtMethod<?>> exhaustiveMethods = firstValueClass.getMethodsByName("exhaustive");

        Assert.assertEquals(
            "FirstValueArbitrary must preserve the delegate's exhaustive generator so jqwik AUTO mode can stop "
                + "after finite ranges are covered instead of falling back to randomized tries",
            1,
            exhaustiveMethods.size());

        CtMethod<?> exhaustive = exhaustiveMethods.get(0);
        List<String> parameterNames = exhaustive.getParameters().stream()
            .map(CtParameter::getSimpleName)
            .collect(Collectors.toList());
        Assert.assertEquals(
            "exhaustive(long) must keep jqwik's maxNumberOfSamples parameter",
            java.util.Collections.singletonList("maxNumberOfSamples"),
            parameterNames);
        Assert.assertTrue(
            "exhaustive(long) must delegate the same maxNumberOfSamples value to the wrapped arbitrary",
            exhaustive.getBody().toString().contains("delegate.exhaustive(maxNumberOfSamples)"));

        Class<?> harnessClass = compileGeneratedHarness("ExhaustiveHarness", randomDrawMethods());
        @SuppressWarnings("unchecked")
        List<String> rows = (List<String>) invokeStatic(
            harnessClass,
            "exhaustiveRows",
            new Class<?>[]{int[].class},
            new int[]{7, 7, 8});
        Assert.assertEquals(Arrays.asList("x=7", "x=7", "x=8"), rows);
    }

    @Example
    void randomDedupUsesRecorderRowSerializationWithoutJqwikFilter() {
        String source = FirstValueArbitraryFactory.render(velocityEngine());

        Assert.assertTrue(source.contains("JqwikValueRecorder.serializeRow"));
        Assert.assertFalse("dedup must stay invisible to jqwik discard accounting", source.contains("return delegate.filter("));
    }

    private static Object invokeStatic(
        Class<?> harnessClass,
        String methodName,
        Class<?>[] parameterTypes,
        Object... arguments
    ) throws Exception {
        Method method = harnessClass.getMethod(methodName, parameterTypes);
        return method.invoke(null, arguments);
    }

    private static List<?> readStaticList(Class<?> propertyClass, String fieldName) throws Exception {
        return (List<?>) propertyClass.getField(fieldName).get(null);
    }

    private static String randomDrawMethods() {
        return ""
            + "public static java.util.List<String> drawRandomRows(int seed, int[] randomValues, int draws) {\n"
            + "    FirstValueArbitrary<TestParameters> arbitrary = new FirstValueArbitrary<TestParameters>(new TestParameters(seed), new SequenceArbitrary(randomValues, null));\n"
            + "    net.jqwik.api.RandomGenerator<TestParameters> generator = arbitrary.generator(20);\n"
            + "    java.util.List<String> rows = new java.util.ArrayList<String>();\n"
            + "    java.util.Random random = new java.util.Random(0L);\n"
            + "    for (int i = 0; i < draws; i++) {\n"
            + "        rows.add(JqwikValueRecorder.serializeRow(generator.next(random).value()));\n"
            + "    }\n"
            + "    return rows;\n"
            + "}\n"
            + "public static java.util.List<String> exhaustiveRows(int[] exhaustiveValues) {\n"
            + "    FirstValueArbitrary<TestParameters> arbitrary = new FirstValueArbitrary<TestParameters>(new TestParameters(2), new SequenceArbitrary(new int[]{99}, exhaustiveValues));\n"
            + "    java.util.List<String> rows = new java.util.ArrayList<String>();\n"
            + "    java.util.Iterator<TestParameters> iterator = arbitrary.exhaustive(100L).get().iterator();\n"
            + "    while (iterator.hasNext()) {\n"
            + "        rows.add(JqwikValueRecorder.serializeRow(iterator.next()));\n"
            + "    }\n"
            + "    return rows;\n"
            + "}\n";
    }

    private static void runJqwikProperties(Class<?> propertyClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(propertyClass))
            .build();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        org.junit.platform.launcher.Launcher launcher = LauncherFactory.create();
        TestPlan testPlan = launcher.discover(request);
        Assert.assertTrue("dynamic property class must contain a discoverable jqwik property", testPlan.containsTests());
        launcher.execute(request, listener);

        TestExecutionSummary summary = listener.getSummary();
        StringWriter writer = new StringWriter();
        summary.printTo(new PrintWriter(writer));
        Assert.assertEquals(writer.toString(), 0, summary.getFailures().size());
    }

    private static Class<?> compileGeneratedHarness(String simpleName, String body) throws Exception {
        Path root = Files.createTempDirectory("first-value-arbitrary-test");
        Path sourceRoot = root.resolve("src/generated");
        Path classes = root.resolve("classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classes);
        Path sourceFile = sourceRoot.resolve(simpleName + ".java");
        Files.write(sourceFile, generatedSource(simpleName, body).getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("tests must run on a JDK with javac", compiler);
        DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new DiagnosticCollector<javax.tools.JavaFileObject>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        try {
            Iterable<? extends javax.tools.JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(
                java.util.Collections.singletonList(sourceFile.toFile()));
            List<String> options = Arrays.asList("-classpath", System.getProperty("java.class.path"), "-d", classes.toString());
            Boolean compiled = compiler.getTask(null, fileManager, diagnostics, options, null, units).call();
            if (!Boolean.TRUE.equals(compiled)) {
                Assert.fail(formatDiagnostics(diagnostics));
            }
        } finally {
            fileManager.close();
        }

        URLClassLoader classLoader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, FirstValueArbitraryFirstValueTest.class.getClassLoader());
        return classLoader.loadClass("generated." + simpleName);
    }

    private static String generatedSource(String simpleName, String body) {
        return ""
            + "package generated;\n"
            + "public class " + simpleName + " {\n"
            + "public static class TestParameters {\n"
            + "    public int x;\n"
            + "    public TestParameters(int x) { this.x = x; }\n"
            + "    public String toString() { return \"TestParameters{x=\" + this.x + \"}\"; }\n"
            + "}\n"
            + "public static class JqwikValueRecorder {\n"
            + "    public static String serializeRow(final TestParameters parameters) { return \"x=\" + parameters.x; }\n"
            + "}\n"
            + sequenceArbitrarySource()
            + FirstValueArbitraryFactory.render(velocityEngine())
            + body
            + "}\n";
    }

    private static String sequenceArbitrarySource() {
        return ""
            + "private static class SequenceArbitrary implements net.jqwik.api.Arbitrary<TestParameters> {\n"
            + "    private final int[] randomValues;\n"
            + "    private final int[] exhaustiveValues;\n"
            + "    private final int[] edgeCaseValues;\n"
            + "    SequenceArbitrary(int[] randomValues, int[] exhaustiveValues) {\n"
            + "        this(randomValues, exhaustiveValues, null);\n"
            + "    }\n"
            + "    SequenceArbitrary(int[] randomValues, int[] exhaustiveValues, int[] edgeCaseValues) {\n"
            + "        this.randomValues = randomValues;\n"
            + "        this.exhaustiveValues = exhaustiveValues;\n"
            + "        this.edgeCaseValues = edgeCaseValues;\n"
            + "    }\n"
            + "    public net.jqwik.api.RandomGenerator<TestParameters> generator(int genSize) {\n"
            + "        return new net.jqwik.api.RandomGenerator<TestParameters>() {\n"
            + "            private int index = 0;\n"
            + "            public net.jqwik.api.Shrinkable<TestParameters> next(java.util.Random random) {\n"
            + "                int value = randomValues[Math.min(index, randomValues.length - 1)];\n"
            + "                index++;\n"
            + "                return net.jqwik.api.Shrinkable.unshrinkable(new TestParameters(value));\n"
            + "            }\n"
            + "        };\n"
            + "    }\n"
            + "    public java.util.Optional<net.jqwik.api.ExhaustiveGenerator<TestParameters>> exhaustive(long maxNumberOfSamples) {\n"
            + "        if (exhaustiveValues == null) {\n"
            + "            return java.util.Optional.empty();\n"
            + "        }\n"
            + "        final java.util.List<TestParameters> values = new java.util.ArrayList<TestParameters>();\n"
            + "        for (int i = 0; i < exhaustiveValues.length; i++) {\n"
            + "            values.add(new TestParameters(exhaustiveValues[i]));\n"
            + "        }\n"
            + "        return java.util.Optional.of(new net.jqwik.api.ExhaustiveGenerator<TestParameters>() {\n"
            + "            public long maxCount() { return values.size(); }\n"
            + "            public java.util.Iterator<TestParameters> iterator() { return values.iterator(); }\n"
            + "        });\n"
            + "    }\n"
            + "    public net.jqwik.api.EdgeCases<TestParameters> edgeCases(int maxEdgeCases) {\n"
            + "        if (edgeCaseValues == null) {\n"
            + "            return net.jqwik.api.EdgeCases.none();\n"
            + "        }\n"
            + "        java.util.List<java.util.function.Supplier<net.jqwik.api.Shrinkable<TestParameters>>> suppliers = new java.util.ArrayList<java.util.function.Supplier<net.jqwik.api.Shrinkable<TestParameters>>>();\n"
            + "        for (int i = 0; i < edgeCaseValues.length; i++) {\n"
            + "            final int value = edgeCaseValues[i];\n"
            + "            suppliers.add(new java.util.function.Supplier<net.jqwik.api.Shrinkable<TestParameters>>() {\n"
            + "                public net.jqwik.api.Shrinkable<TestParameters> get() {\n"
            + "                    return net.jqwik.api.Shrinkable.unshrinkable(new TestParameters(value));\n"
            + "                }\n"
            + "            });\n"
            + "        }\n"
            + "        return net.jqwik.api.EdgeCases.fromSuppliers(suppliers);\n"
            + "    }\n"
            + "}\n";
    }

    private static String formatDiagnostics(DiagnosticCollector<javax.tools.JavaFileObject> diagnostics) {
        StringBuilder builder = new StringBuilder();
        for (Diagnostic<? extends javax.tools.JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            builder.append(diagnostic.toString()).append('\n');
        }
        return builder.toString();
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
