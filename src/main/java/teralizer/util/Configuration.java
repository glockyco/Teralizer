package teralizer.util;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.dependencies.Dependency;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static final Config config;

    static {
        // Load default config from 'src/main/resources/reference.conf'.
        Config defaultConfig = ConfigFactory.defaultReference();

        // Load custom config from the path specified by '-Dteralizer.config'.
        String customConfigPath = System.getProperty("teralizer.config");
        Config customConfig = ConfigFactory.empty();
        if (customConfigPath != null && !customConfigPath.isEmpty()) {
            File customConfigFile = new File(customConfigPath);
            if (customConfigFile.exists()) {
                customConfig = ConfigFactory.parseFile(customConfigFile);
                LOGGER.atDebug().log("Loaded custom configuration from: " + customConfigPath);
            } else {
                throw new RuntimeException("Warning: Configuration file not found: " + customConfigPath);
            }
        }

        // Combine the two configurations, using the custom config as the
        // primary source of config values. Any values that are not present
        // there fall back to the values provided by the default config.
        config = customConfig.withFallback(defaultConfig).resolve();
    }

    // ----- General ----- //
    public static final String TOOL_NAME = "Teralizer";

    public static final String MAVEN_CUSTOM_BUILD_FILE = "pom." + TOOL_NAME.toLowerCase() + ".xml";
    public static final String MAVEN_DEFAULT_BUILD_FILE = "pom.xml";

    public static final String GRADLE_CUSTOM_BUILD_FILE = "build." + TOOL_NAME.toLowerCase() + ".gradle";
    public static final String GRADLE_DEFAULT_BUILD_FILE = "build.gradle";

    // ----- Database ----- //
    public static final Path DB_PATH = Paths.get("database/db.sqlite");
    public static final String DB_CONNECTION_STRING = "jdbc:sqlite:" + DB_PATH.toAbsolutePath() + "?foreign_keys=on";
    public static final Path DB_DDL_PATH = Paths.get("src/main/resources/db/create-tables.sql");

    // ----- Dependencies ----- //
    public static final Path EVOSUITE_JAR_PATH = Paths.get("src/main/resources/evosuite/evosuite-1.2.0.jar");

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
    public static final GeneralizationVariant[] GENERALIZATION_VARIANTS = GeneralizationVariant.values();

    public static final int MAX_TRIES_JQWIK = 20;
    public static final int MAX_SPECIFICATION_SIZE = 200000;

    public static final List<String> SUPPORTED_TYPES = Arrays.asList("byte", "short", "int", "long", "float", "double");

    public static final String TEST_PARAMETERS_CLASS_NAME = "TestParameters";
    public static final String TEST_PARAMETERS_SUPPLIER_CLASS_NAME = "TestParametersSupplier";

    public static final String JUNIT4_ASSERTION_PACKAGE = "org.junit.Assert";
    public static final String JUNIT5_ASSERTION_PACKAGE = "org.junit.jupiter.api.Assertions";
    public static final String ASSERT_EQUALS = "assertEquals";
    public static final String ASSERT_TRUE = "assertTrue";
    public static final String ASSERT_FALSE = "assertFalse";
    public static final String ASSERT_THROWS = "assertThrows";
    public static final List<String> GENERALIZABLE_ASSERTS = Arrays.asList(ASSERT_EQUALS, ASSERT_TRUE, ASSERT_FALSE, ASSERT_THROWS);

    // ----- EvoSuite ----- //
    public static String getEvosuiteStoppingCondition() {
        return config.getString("teralizer.evosuite.stopping-condition");
    }

    public static String getEvosuiteSearchBudget() {
        return config.getString("teralizer.evosuite.search-budget");
    }

    public static String getEvosuiteAssertionStrategy() {
        return config.getString("teralizer.evosuite.assertion-strategy");
    }

    public static String getEvosuiteCoverageCriterion() {
        return config.getString("teralizer.evosuite.coverage-criterion");
    }

    // ----- SPF / JPF ----- //
    public static double getJpfMaxExecutionTime() {
        return config.getDouble("teralizer.jpf.max-execution-time");
    }

    public static long getJpfMaxPathConditionSize() {
        return config.getLong("teralizer.jpf.max-path-condition-size");
    }

    // ----- Pitest ----- //
    public static String getPitestMutators() {
        return config.getString("teralizer.pitest.mutators");
    }
}
