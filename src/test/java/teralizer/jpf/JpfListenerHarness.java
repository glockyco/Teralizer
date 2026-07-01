package teralizer.jpf;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gov.nasa.jpf.Config;
import gov.nasa.jpf.JPF;
import gov.nasa.jpf.JPFListenerException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Value;
import teralizer.jpf.targets.Cut;
import teralizer.transformer.SpecificationGson;
import teralizer.util.Configuration;

/**
 * Runs {@link TestGeneralizationListener} in-process against a compiled target fixture, exactly as
 * {@code teralizer.processing.task.JpfExecutionTask} does in the pipeline: render the shared
 * {@code jpf-config.vm}, build a JPF {@link Config} via {@link JPF#createConfig(String[])}, attach
 * the listener, run, then read back the concrete/symbolic specification files the listener wrote.
 *
 * <p>This gives the specification-extraction listener direct regression coverage instead of relying
 * on full pipeline runs to surface capture bugs runs later. Reusing the production
 * {@code jpf-config.vm} keeps the test configuration faithful to what the pipeline emits.
 *
 * <p><b>Working directory:</b> JPF resolves {@code site.properties} (which defines
 * {@code ${jpf-symbc}}) relative to the working directory, so tests must run with the project root
 * as the working directory — the Gradle default for the {@code test} task.
 */
public final class JpfListenerHarness {

    private JpfListenerHarness() {
    }

    private static final double DEFAULT_MAX_EXECUTION_TIME = 60.0;
    private static final long DEFAULT_MAX_PATH_CONDITION_SIZE = 100_000L;

    /** Parsed result of one listener run. The specification fields are the raw JSON the listener wrote. */
    public static final class Capture {
        private final List<Value> inputValues;
        private final CapturedOutput output;
        private final String inputSpecificationJson;
        private final String outputSpecificationJson;

        Capture(
            List<Value> inputValues,
            CapturedOutput output,
            String inputSpecificationJson,
            String outputSpecificationJson
        ) {
            this.inputValues = inputValues;
            this.output = output;
            this.inputSpecificationJson = inputSpecificationJson;
            this.outputSpecificationJson = outputSpecificationJson;
        }

        public List<Value> getInputValues() {
            return this.inputValues;
        }

        public CapturedOutput getOutput() {
            return this.output;
        }

        public String getInputSpecificationJson() {
            return this.inputSpecificationJson;
        }

        public String getOutputSpecificationJson() {
            return this.outputSpecificationJson;
        }
    }

    /**
     * Run the listener over a target fixture and return the captured specification.
     *
     * @param workDir              writable directory for the generated config and specification files
     * @param targetClassQN        the driver class declaring {@code main} (JPF's {@code target})
     * @param symbolicMethod       the {@code symbolic.method} spec, e.g. {@code pkg.Driver.wrapper(con)}
     * @param instrumentedMethodQN FQN of the method whose entry captures the concrete inputs; it must
     *                             carry the generalized parameters
     * @param testedMethodQN       FQN of the method whose exit triggers specification capture
     */
    public static Capture run(
        Path workDir,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN
    ) {
        Config config = buildConfig(workDir, targetClassQN, symbolicMethod, instrumentedMethodQN, testedMethodQN);
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        runUnwrappingAborts(jpf);

        if (jpf.foundErrors()) {
            String details = jpf.getSearchErrors().stream()
                .map(error -> error.getDescription() + "\n" + error.getDetails())
                .collect(Collectors.joining("\n--\n"));
            throw new IllegalStateException("JPF reported errors for " + symbolicMethod + ":\n" + details);
        }
        if (!jpf.getVM().isInitialized()) {
            throw new IllegalStateException("JPF VM failed to initialize for " + symbolicMethod);
        }

        // The listener now only observes; the specification files are written post-run from the
        // captured Invocation, exactly as JpfExecutionTask does in the pipeline.
        Path inputValuesPath = workDir.resolve("concrete-input.json");
        Path outputValuePath = workDir.resolve("concrete-output.json");
        Path inputSpecificationPath = workDir.resolve("symbolic-input.json");
        Path outputSpecificationPath = workDir.resolve("symbolic-output.json");
        if (listener.getInvocation() != null) {
            new SpecificationExtractor().write(listener.getInvocation(),
                inputValuesPath, outputValuePath, inputSpecificationPath, outputSpecificationPath);
        }
        return parse(inputValuesPath, outputValuePath, inputSpecificationPath, outputSpecificationPath);
    }

    /**
     * Run the listener and classify the outcome, without writing or reading specification files —
     * for asserting outcomes (e.g. {@code TARGET_NOT_ENTERED}) that produce no specification. Uses
     * the default extraction ceilings.
     */
    public static ExtractionOutcome runOutcome(
        Path workDir,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN
    ) {
        return runOutcome(workDir, targetClassQN, symbolicMethod, instrumentedMethodQN, testedMethodQN,
            DEFAULT_MAX_EXECUTION_TIME, DEFAULT_MAX_PATH_CONDITION_SIZE);
    }

    /**
     * Run the listener under explicit extraction ceilings and classify the outcome. A tight
     * {@code maxPathConditionSize} (or {@code maxExecutionTime}) lets a test exercise the listener's
     * capability-limit guards, which abort the run with a typed {@link ExtractionAborted}.
     */
    public static ExtractionOutcome runOutcome(
        Path workDir,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN,
        double maxExecutionTime,
        long maxPathConditionSize
    ) {
        Config config = buildConfig(workDir, targetClassQN, symbolicMethod, instrumentedMethodQN,
            testedMethodQN, maxExecutionTime, maxPathConditionSize);
        JPF jpf = new JPF(config);
        TestGeneralizationListener listener = new TestGeneralizationListener(config);
        jpf.addListener(listener);
        try {
            runUnwrappingAborts(jpf);
        } catch (gov.nasa.jpf.symbc.string.UnsupportedSymbolicStringOpException e) {
            return ExtractionOutcome.unsupportedTerm(e.getMessage());
        }

        if (jpf.foundErrors()) {
            String details = jpf.getSearchErrors().stream()
                .map(error -> error.getDescription() + "\n" + error.getDetails())
                .collect(Collectors.joining("\n--\n"));
            throw new IllegalStateException("JPF reported errors for " + symbolicMethod + ":\n" + details);
        }
        if (!jpf.getVM().isInitialized()) {
            throw new IllegalStateException("JPF VM failed to initialize for " + symbolicMethod);
        }

        return ExtractionOutcome.fromState(listener.wasTargetEntered(), listener.getInvocation() != null);
    }

    /**
     * Build the JPF {@link Config} for a scenario without attaching a listener, so a caller can
     * attach its own (e.g. an observer-only listener) and run. Renders the production
     * {@code jpf-config.vm} exactly as {@link #run} does; the four specification paths are derived
     * from {@code workDir} and re-derived by the caller if it needs to read them back. Uses the
     * default extraction ceilings.
     */
    public static Config buildConfig(
        Path workDir,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN
    ) {
        return buildConfig(workDir, targetClassQN, symbolicMethod, instrumentedMethodQN, testedMethodQN,
            DEFAULT_MAX_EXECUTION_TIME, DEFAULT_MAX_PATH_CONDITION_SIZE);
    }

    /** Build the JPF {@link Config} under explicit extraction ceilings (see {@link #buildConfig}). */
    public static Config buildConfig(
        Path workDir,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN,
        double maxExecutionTime,
        long maxPathConditionSize
    ) {
        Path jpfConfigPath = workDir.resolve("scenario.jpf");
        Path inputValuesPath = workDir.resolve("concrete-input.json");
        Path outputValuePath = workDir.resolve("concrete-output.json");
        Path inputSpecificationPath = workDir.resolve("symbolic-input.json");
        Path outputSpecificationPath = workDir.resolve("symbolic-output.json");
        Path reportPath = workDir.resolve("report.txt");

        writeConfig(
            jpfConfigPath, targetClassQN, symbolicMethod, instrumentedMethodQN, testedMethodQN,
            inputValuesPath, outputValuePath, inputSpecificationPath, outputSpecificationPath, reportPath,
            maxExecutionTime, maxPathConditionSize
        );

        return JPF.createConfig(new String[]{jpfConfigPath.toString()});
    }

    private static void writeConfig(
        Path jpfConfigPath,
        String targetClassQN,
        String symbolicMethod,
        String instrumentedMethodQN,
        String testedMethodQN,
        Path inputValuesPath,
        Path outputValuePath,
        Path inputSpecificationPath,
        Path outputSpecificationPath,
        Path reportPath,
        double maxExecutionTime,
        long maxPathConditionSize
    ) {
        VelocityEngine velocity = new VelocityEngine(templateProperties());
        velocity.init();

        VelocityContext context = new VelocityContext();
        context.put("jpfSymbcModelClasspath", Configuration.JPF_SYMBC_MODEL_CLASSPATH);
        context.put("pathSeparator", File.pathSeparator);
        context.put("classpath", fixturesClasspath());
        context.put("symbolicMethod", symbolicMethod);
        // Plain-arithmetic solver defaults (the non-raw-bits SpfSymbolicConfigSelector selection).
        // Harness scenarios drive concrete (con) parameters, so the solver is not exercised; these
        // values only keep the rendered config valid.
        context.put("symbolicDp", "z3");
        context.put("symbolicFp", false);
        context.put("symbolicBvLength", 32);
        context.put("symbolicStrings", true);
        context.put("maxExecutionTime", maxExecutionTime);
        context.put("maxPathConditionSize", maxPathConditionSize);
        context.put("driverClassQualifiedName", targetClassQN);
        // Only test_generalization.{instrumented_method,tested_method} and the four spec paths are
        // read by the listener; the remaining identity keys are populated for template completeness
        // (jpf-config.vm uses runtime.references.strict).
        context.put("instrumentedMethodQualifiedName", instrumentedMethodQN);
        context.put("testedMethodQualifiedName", testedMethodQN);
        context.put("instrumentedClassQualifiedName", classOf(instrumentedMethodQN));
        context.put("testedClassQualifiedName", classOf(testedMethodQN));
        context.put("testClassQualifiedName", classOf(instrumentedMethodQN));
        context.put("testMethodQualifiedName", instrumentedMethodQN);
        context.put("inputValuesPath", configPath(inputValuesPath));
        context.put("outputValuePath", configPath(outputValuePath));
        context.put("inputSpecificationPath", configPath(inputSpecificationPath));
        context.put("outputSpecificationPath", configPath(outputSpecificationPath));
        context.put("reportPath", configPath(reportPath));

        try {
            Files.createFile(reportPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create JPF report file: " + reportPath, e);
        }

        try (FileWriter writer = new FileWriter(jpfConfigPath.toFile())) {
            Template template = velocity.getTemplate("jpf-config.vm");
            template.merge(context, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JPF config: " + jpfConfigPath, e);
        }
    }

    /**
     * Run JPF, surfacing a listener's typed {@link ExtractionAborted} directly: JPF wraps a listener
     * throw in {@link JPFListenerException}, so an abort would otherwise reach callers only as a
     * buried cause. Mirrors how {@code JpfExecutionTask} unwraps the abort in the pipeline.
     */
    private static void runUnwrappingAborts(JPF jpf) {
        try {
            jpf.run();
        } catch (JPFListenerException e) {
            if (e.getCause() instanceof ExtractionAborted) {
                throw (ExtractionAborted) e.getCause();
            }
            throw e;
        }
    }

    private static Capture parse(
        Path inputValuesPath,
        Path outputValuePath,
        Path inputSpecificationPath,
        Path outputSpecificationPath
    ) {
        Gson gson = SpecificationGson.create();
        Type valueListType = new TypeToken<List<Value>>() {
        }.getType();
        List<Value> inputs = gson.fromJson(readFile(inputValuesPath), valueListType);
        CapturedOutput output = gson.fromJson(readFile(outputValuePath), CapturedOutput.class);
        String inputSpecification = Files.exists(inputSpecificationPath) ? readFile(inputSpecificationPath) : null;
        String outputSpecification = Files.exists(outputSpecificationPath) ? readFile(outputSpecificationPath) : null;
        return new Capture(inputs, output, inputSpecification, outputSpecification);
    }

    private static String readFile(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read specification file: " + path, e);
        }
    }

    private static String classOf(String methodQualifiedName) {
        return methodQualifiedName.substring(0, methodQualifiedName.lastIndexOf('.'));
    }

    /**
     * Render a filesystem path for the {@code .jpf} config file. JPF parses that file with
     * {@code Properties}-style escaping, where a backslash is an escape character, so a Windows
     * {@code C:\dir} path written verbatim is corrupted (and its classpath entry lost, leaving JPF
     * unable to load the target). Forward slashes are accepted by the JVM on every platform, so
     * normalize to them. No-op where the platform separator is already {@code /}.
     */
    private static String configPath(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String fixturesClasspath() {
        try {
            return Paths.get(Cut.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString().replace('\\', '/');
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to locate the compiled fixture classpath", e);
        }
    }

    private static Properties templateProperties() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");
        return properties;
    }
}
