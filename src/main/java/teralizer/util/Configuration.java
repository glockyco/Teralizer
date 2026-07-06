package teralizer.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.processing.dependencies.Dependency;

public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();
    private static final Config CONFIG;

    // ----- General ----- //
    public static final String TOOL_NAME = "Teralizer";
    public static final String TOOL_NAME_LOWER = TOOL_NAME.toLowerCase();
    public static final String TOOL_CONFIG_PROPERTY = TOOL_NAME_LOWER + ".config";

    static {
        CONFIG = buildConfig(
            System.getProperty(TOOL_CONFIG_PROPERTY),
            ConfigFactory.systemProperties(),
            ConfigFactory.defaultReference()
        );
    }

    /**
     * Build the effective configuration with standard precedence:
     * {@code overrides} (JVM system properties) > the {@code configPaths} files > {@code reference}
     * (reference.conf). {@code configPaths} is a comma-separated list; later files win over earlier
     * ones, so a run can compose a shared profile with a per-project config
     * (e.g. {@code -Dteralizer.config=profile.conf,project-N.conf}) and still override any value
     * from the command line (e.g. {@code -Dteralizer.pitest.enabled=false}).
     */
    static Config buildConfig(String configPaths, Config overrides, Config reference) {
        Config customConfig = ConfigFactory.empty();
        if (configPaths != null && !configPaths.isEmpty()) {
            for (String path : configPaths.split(",")) {
                String trimmed = path.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                File customConfigFile = new File(trimmed);
                if (!customConfigFile.exists()) {
                    throw new RuntimeException("Configuration file not found: " + trimmed);
                }
                // Later files take precedence: parse as primary, accumulate earlier as fallback.
                customConfig = ConfigFactory.parseFile(customConfigFile).withFallback(customConfig);
                LOGGER.atDebug().log("Loaded custom configuration from: " + trimmed);
            }
        }
        return overrides.withFallback(customConfig).withFallback(reference).resolve();
    }

    /** Reads the protected-corpus policy file, skipping blank lines and {@code #} comments. */
    static List<String> loadProtectedDatabasePatterns(Path path) {
        try {
            List<String> patterns = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                patterns.add(trimmed);
            }
            return patterns;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Cannot read protected-database policy at " + path, e);
        }
    }

    /** True when the database name matches any policy pattern. A pattern may use {@code *} as a wildcard. */
    static boolean isProtectedDatabase(String name, List<String> patterns) {
        for (String pattern : patterns) {
            if (globMatches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String pattern, String value) {
        if (pattern.indexOf('*') < 0) {
            return pattern.equals(value);
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return value.matches(regex.toString());
    }

    private static String resolveDatabaseName() {
        if (!CONFIG.hasPath("teralizer.database.name")) {
            throw new RuntimeException("teralizer.database.name is not set. Name the target database in the "
                + "run profile (for example teralizer.database.name = postgres_verification), or pass "
                + "-Dteralizer.database.name=<db> on the command line.");
        }
        String name = CONFIG.getString("teralizer.database.name");
        boolean allowProtected = CONFIG.hasPath("teralizer.database.allow-protected")
            && CONFIG.getBoolean("teralizer.database.allow-protected");
        if (!allowProtected && isProtectedDatabase(name, loadProtectedDatabasePatterns(PROTECTED_DB_PATH))) {
            throw new RuntimeException("Refusing to target protected database '" + name + "'. Set "
                + "teralizer.database.allow-protected = true in the profile to run against a real corpus.");
        }
        return name;
    }

    public static final String MAVEN_CUSTOM_BUILD_FILE = "pom." + TOOL_NAME_LOWER + ".xml";
    public static final String MAVEN_GENERALIZED_BUILD_FILE = "pom." + TOOL_NAME_LOWER + ".generalized.xml";
    public static final String MAVEN_DEFAULT_BUILD_FILE = "pom.xml";

    public static final String GRADLE_CUSTOM_BUILD_FILE = "build." + TOOL_NAME_LOWER + ".gradle";
    public static final String GRADLE_DEFAULT_BUILD_FILE = "build.gradle";

    // ----- Database ----- //
    public static final Path PROTECTED_DB_PATH = Paths.get("src/main/resources/db/protected-databases.txt");
    public static final String DB_HOST = DOTENV.get("DB_HOST", "localhost");
    public static final String DB_PORT = DOTENV.get("DB_PORT", "5432");
    public static final String DB_NAME = resolveDatabaseName();
    public static final String DB_USER = DOTENV.get("DB_USER", "postgres");
    public static final String DB_PASSWORD = DOTENV.get("DB_PASSWORD", "postgres");

    public static final Path DB_PATH = Paths.get("database", TOOL_NAME_LOWER);
    public static final Path DB_DDL_PATH = Paths.get("src/main/resources/db/create-tables.sql");
    public static final String DB_CONNECTION_STRING = String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s", DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD);

    // ----- Data Directory ----- //
    public static final Path DATA_DIR = Paths.get(CONFIG.getString("teralizer.data-dir"));

    // ----- Dependencies ----- //
    public static final String EVOSUITE_MAIN_CLASS = "org.evosuite.EvoSuite";
    public static final Path EVOSUITE_JAR_PATH = Paths.get("src/main/resources/evosuite/evosuite-1.2.0.jar");
    public static final Path EVOSUITE_LOGBACK_XML_PATH = Paths.get(/*src/main/resources/*/"evosuite/logback.xml");

    public static final Dependency JUNIT_4_DEPENDENCY = new Dependency("junit", "junit", "4.12");
    public static final Dependency JUNIT_VINTAGE_DEPENDENCY = new Dependency("org.junit.vintage", "junit-vintage-engine", "5.11.0");
    public static final Dependency PITEST_DEPENDENCY = new Dependency("org.pitest", "pitest-junit5-plugin", "1.2.1");
    public static final Dependency JQWIK_DEPENDENCY = new Dependency("net.jqwik", "jqwik", "1.8.5");

    /**
     * Language level the generated test harness (jqwik-value-recorder template) requires.
     * Generated property tests use lambdas and method references, so the *test* compilation
     * of a target project must be at least this level; main compilation is never touched.
     */
    public static final String GENERATED_TEST_LANGUAGE_LEVEL = "1.8";

    /**
     * Oldest maven-surefire-plugin able to run JUnit-platform (jqwik) tests. A project pinning
     * an older surefire silently skips every generated property test while the build stays
     * green — the worst failure mode, a false pass.
     */
    public static final String SUREFIRE_MIN_VERSION = "2.22.2";

    public static final Path MAVEN_JACOCO_CONFIG_PATH = Paths.get("src/main/resources/jacoco-config-maven.txt");
    public static final Path MAVEN_PITEST_CONFIG_PATH = Paths.get("src/main/resources/pitest-config-maven.txt");

    public static final Path GRADLE_JACOCO_CONFIG_PATH = Paths.get("src/main/resources/jacoco-config-gradle.txt");
    public static final Path GRADLE_JACOCO_PLUGIN_PATH = Paths.get("src/main/resources/jacoco-plugin-gradle.txt");
    public static final Path GRADLE_PITEST_CONFIG_PATH = Paths.get("src/main/resources/pitest-config-gradle.txt");
    public static final Path GRADLE_PITEST_PLUGIN_PATH = Paths.get("src/main/resources/pitest-plugin-gradle.txt");

    // ----- Generalization ----- //
    public static final int MAX_SPECIFICATION_SIZE = 200000;

    public static final String TEST_ANNOTATION_TEST = "Test";
    public static final String TEST_ANNOTATION_REPEATED = "RepeatedTest";
    public static final String TEST_ANNOTATION_PARAMETERIZED = "ParameterizedTest";
    public static final String TEST_ANNOTATION_PROPERTY = "Property";
    public static final List<String> SUPPORTED_TEST_ANNOTATIONS = Collections.singletonList(TEST_ANNOTATION_TEST);
    public static final List<String> KNOWN_TEST_ANNOTATIONS = Arrays.asList(TEST_ANNOTATION_TEST, TEST_ANNOTATION_REPEATED, TEST_ANNOTATION_PARAMETERIZED, TEST_ANNOTATION_PROPERTY);

    public static final String TEST_PARAMETERS_CLASS_NAME = "TestParameters";
    public static final String TEST_PARAMETERS_SUPPLIER_CLASS_NAME = "TestParametersSupplier";

    public static final String JUNIT4_ASSERTION_PACKAGE = "org.junit.Assert";
    public static final String JUNIT5_ASSERTION_PACKAGE = "org.junit.jupiter.api.Assertions";

    public static final String ASSERT_EQUALS = "assertEquals";
    public static final String ASSERT_TRUE = "assertTrue";
    public static final String ASSERT_FALSE = "assertFalse";
    public static final String ASSERT_THROWS = "assertThrows";
    public static final List<String> GENERALIZABLE_ASSERTS = Arrays.asList(ASSERT_EQUALS, ASSERT_TRUE, ASSERT_FALSE, ASSERT_THROWS);

    public static String render() {
        return CONFIG.getConfig(TOOL_NAME_LOWER).root().render(
            ConfigRenderOptions.defaults().setFormatted(true).setOriginComments(false)
        );
    }

    // ----- Project ----- //
    public static Path getProjectRootPath() {
        return Paths.get(getProjectRootPathString());
    }

    public static String getProjectRootPathString() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".project.root-path");
    }

    public static Path getProjectDataPath() {
        return DATA_DIR.resolve(getProjectRootPath().getFileName().toString());
    }

    public static Path getProjectMainSourcePath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.main-source-path");
    }

    public static Path getProjectTestSourcePath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.test-source-path");
    }

    public static Path getProjectMainCompiledPath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.main-compiled-path");
    }

    public static Path getProjectTestCompiledPath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.test-compiled-path");
    }

    public static Path getProjectTestReportsPath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.test-reports-path");
    }

    public static Path getProjectCoverageReportsPath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.coverage-reports-path");
    }

    public static Path getProjectMutationReportsPath() {
        return getPathOrNull(TOOL_NAME_LOWER + ".project.mutation-reports-path");
    }

    public static boolean getProjectUseTestGeneration() {
        return CONFIG.getBoolean(TOOL_NAME_LOWER + ".project.use-test-generation");
    }

    public static boolean getProjectUseTestGeneralization() {
        return CONFIG.getBoolean(TOOL_NAME_LOWER + ".project.use-test-generalization");
    }

    // ----- EvoSuite ----- //
    public static String getEvosuiteStoppingCondition() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".evosuite.stopping-condition");
    }

    public static String getEvosuiteSearchBudget() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".evosuite.search-budget");
    }

    public static String getEvosuiteAssertionStrategy() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".evosuite.assertion-strategy");
    }

    public static String getEvosuiteCoverageCriterion() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".evosuite.coverage-criterion");
    }

    // ----- SPF / JPF ----- //
    /**
     * The SPF model/native-peer classpath, prepended to the project classpath in the
     * generated JPF config so model classes (e.g. {@code FastMath.abs}) resolve from
     * the symbc build before the project's own classes. Single source of truth for
     * {@code JpfInstrumentationTask} and the config template test.
     */
    public static final String JPF_SYMBC_MODEL_CLASSPATH = "${jpf-symbc}/build/classes";

    public static double getJpfMaxExecutionTime() {
        return CONFIG.getDouble(TOOL_NAME_LOWER + ".jpf.max-execution-time");
    }

    public static long getJpfMaxPathConditionSize() {
        return CONFIG.getLong(TOOL_NAME_LOWER + ".jpf.max-path-condition-size");
    }

    /**
     * JPF search depth ceiling for constraint collection. Every symbolic-operand branch consumes
     * one choice point in collect mode, so loop-heavy tested methods burn depth linearly; the
     * listener reports hitting this limit as a typed SEARCH_DEPTH_LIMIT abort.
     */
    public static int getJpfMaxSearchDepth() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".jpf.max-search-depth");
    }

    // ----- JUnit ----- //
    public static int getJunitMaxExecutionTime() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.max-execution-time");
    }

    public static int getJunitBaselineTriesBudget() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.baseline-tries-budget");
    }

    /** Ceiling (seconds) on the workload-scaled generalized-suite timeout. Never trims below
     * {@code junit.max-execution-time}. */
    public static int getJunitMaxGeneralizedExecutionTime() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.max-generalized-execution-time");
    }

    // ----- Pitest ----- //
    public static String getPitestMutators() {
        return CONFIG.getString(TOOL_NAME_LOWER + ".pitest.mutators");
    }

    public static int getPitestMaxExecutionTime() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".pitest.max-execution-time");
    }

    /** Whether mutation testing runs. Defaults to true; set {@code teralizer.pitest.enabled = false}
     * to skip PIT (e.g. fast validation runs that only exercise generation/build/JaCoCo). */
    public static boolean isPitestEnabled() {
        String path = TOOL_NAME_LOWER + ".pitest.enabled";
        return !CONFIG.hasPath(path) || CONFIG.getBoolean(path);
    }

    /** Whether the ORIGINAL-stage PIT run executes. Off by default: it mutates the full-suite
     * coverage scope and only the INITIAL/GENERALIZED comparison is consumed. */
    public static boolean isPitestOriginalEnabled() {
        return CONFIG.getBoolean(TOOL_NAME_LOWER + ".pitest.original.enabled");
    }

    // ----- Generalizations ----- //
    public static String[] getGeneralizationVariants() {
        return CONFIG.getObject(TOOL_NAME_LOWER + ".generalizations").keySet().toArray(new String[0]);
    }

    public static GeneralizationAlgorithm getGeneralizationAlgorithm(String variant) {
        return GeneralizationAlgorithm.valueOf(CONFIG.getString(TOOL_NAME_LOWER + ".generalizations." + variant + ".algorithm"));
    }

    public static int getGeneralizationJqwikTries(String variant) {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".generalizations." + variant + ".jqwik.tries");
    }

    // ----- Helper Methods ----- //
    private static Path getPathOrNull(String key) {
        return !CONFIG.hasPath(key) || CONFIG.getIsNull(key) ? null : Paths.get(CONFIG.getString(key));
    }
}
