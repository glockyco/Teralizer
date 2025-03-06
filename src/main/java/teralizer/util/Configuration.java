package teralizer.util;

import teralizer.processing.dependencies.Dependency;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Configuration {

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

    // ----- SPF / JPF ----- //
    public static final double JPF_MAX_EXECUTION_TIME = 10; // seconds
    public static final long JPF_MAX_PATH_CONDITION_SIZE = 1000000; // characters

    // ----- Pitest ----- //
    public static final String PITEST_MUTATORS = "DEFAULTS"; // https://pitest.org/quickstart/mutators/
}
