package teralizer.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationAlgorithm;
import teralizer.processing.dependencies.Dependency;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);
    private static final Dotenv DOTENV = Dotenv.configure().ignoreIfMissing().load();
    private static final Config CONFIG;

    // ----- General ----- //
    public static final String TOOL_NAME = "Teralizer";
    public static final String TOOL_NAME_LOWER = TOOL_NAME.toLowerCase();
    public static final String TOOL_CONFIG_PROPERTY = TOOL_NAME_LOWER + ".config";

    static {
        // Load default config from 'src/main/resources/reference.conf'.
        Config defaultConfig = ConfigFactory.defaultReference();

        // Load custom config from the path specified by '-Dteralizer.config'.
        String customConfigPath = System.getProperty(TOOL_CONFIG_PROPERTY);
        Config customConfig = ConfigFactory.empty();
        if (customConfigPath != null && !customConfigPath.isEmpty()) {
            File customConfigFile = new File(customConfigPath);
            if (customConfigFile.exists()) {
                customConfig = ConfigFactory.parseFile(customConfigFile);
                LOGGER.atDebug().log("Loaded custom configuration from: " + customConfigPath);
            } else {
                throw new RuntimeException("Configuration file not found: " + customConfigPath);
            }
        }

        // Combine the two configurations, using the custom config as the
        // primary source of config values. Any values that are not present
        // there fall back to the values provided by the default config.
        CONFIG = customConfig.withFallback(defaultConfig).resolve();
    }

    public static final String MAVEN_CUSTOM_BUILD_FILE = "pom." + TOOL_NAME_LOWER + ".xml";
    public static final String MAVEN_DEFAULT_BUILD_FILE = "pom.xml";

    public static final String GRADLE_CUSTOM_BUILD_FILE = "build." + TOOL_NAME_LOWER + ".gradle";
    public static final String GRADLE_DEFAULT_BUILD_FILE = "build.gradle";

    // ----- Database ----- //
    public static final String DB_HOST = DOTENV.get("DB_HOST", "localhost");
    public static final String DB_PORT = DOTENV.get("DB_PORT", "5432");
    public static final String DB_NAME = DOTENV.get("DB_NAME", "postgres");
    public static final String DB_USER = DOTENV.get("DB_USER", "postgres");
    public static final String DB_PASSWORD = DOTENV.get("DB_PASSWORD", "postgres");

    public static final Path DB_PATH = Paths.get("database", TOOL_NAME_LOWER);
    public static final Path DB_DDL_PATH = Paths.get("src/main/resources/db/create-tables.sql");
    public static final String DB_CONNECTION_STRING = String.format("jdbc:postgresql://%s:%s/%s?user=%s&password=%s", DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD);

    // ----- Data Directory ----- //
    public static final Path DATA_DIR = Paths.get(DOTENV.get("DATA_DIR", "data"));

    // ----- Dependencies ----- //
    public static final String EVOSUITE_MAIN_CLASS = "org.evosuite.EvoSuite";
    public static final Path EVOSUITE_JAR_PATH = Paths.get("src/main/resources/evosuite/evosuite-1.2.0.jar");
    public static final Path EVOSUITE_LOGBACK_XML_PATH = Paths.get(/*src/main/resources/*/"evosuite/logback.xml");

    public static final Dependency JUNIT_4_DEPENDENCY = new Dependency("junit", "junit", "4.12");
    public static final Dependency JUNIT_VINTAGE_DEPENDENCY = new Dependency("org.junit.vintage", "junit-vintage-engine", "5.11.0");
    public static final Dependency PITEST_DEPENDENCY = new Dependency("org.pitest", "pitest-junit5-plugin", "1.2.1");
    public static final Dependency JQWIK_DEPENDENCY = new Dependency("net.jqwik", "jqwik", "1.8.5");

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

    // ----- JUnit ----- //
    public static int getJunitMaxExecutionTime() {
        return CONFIG.getInt(TOOL_NAME_LOWER + ".junit.max-execution-time");
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
