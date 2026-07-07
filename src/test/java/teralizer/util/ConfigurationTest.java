package teralizer.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {

    private static Path writeConf(Path dir, String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.write(file, content.getBytes());
        return file;
    }

    @Test
    void isProtectedMatchesExactAndGlob() {
        List<String> patterns = Arrays.asList("postgres_dev", "*_replication");
        assertTrue(Configuration.isProtectedDatabase("postgres_dev", patterns));
        assertTrue(Configuration.isProtectedDatabase("postgres_dev_replication", patterns));
        assertFalse(Configuration.isProtectedDatabase("postgres_verification", patterns));
    }

    @Test
    void loadsProtectedPatternsSkippingCommentsAndBlanks(@TempDir Path dir) throws IOException {
        Path file = writeConf(dir, "protected.txt", "# comment\n\npostgres_dev\n*_replication\n");
        assertEquals(Arrays.asList("postgres_dev", "*_replication"),
            Configuration.loadProtectedDatabasePatterns(file));
    }

    @Test
    void policyFileListsCanonicalProtectedNames() {
        List<String> patterns = Configuration.loadProtectedDatabasePatterns(Configuration.PROTECTED_DB_PATH);
        assertTrue(patterns.contains("postgres_dev"));
        assertTrue(patterns.contains("postgres_reporeapers_rerun"));
        assertTrue(patterns.contains("postgres_fusion_spike"));
    }

    @Test
    void laterConfigFileWinsOverEarlier(@TempDir Path dir) throws IOException {
        Path profile = writeConf(dir, "profile.conf",
            "teralizer {\n  pitest { enabled = false }\n  shared = 1\n}");
        Path project = writeConf(dir, "project.conf",
            "teralizer {\n  shared = 2\n  project { root-path = \"p\" }\n}");

        Config config = Configuration.buildConfig(
            profile + "," + project, ConfigFactory.empty(), ConfigFactory.empty());

        assertEquals(2, config.getInt("teralizer.shared"), "later file wins on overlap");
        assertFalse(config.getBoolean("teralizer.pitest.enabled"), "earlier file's keys preserved");
        assertEquals("p", config.getString("teralizer.project.root-path"));
    }

    @Test
    void overridesWinOverConfigFiles(@TempDir Path dir) throws IOException {
        Path project = writeConf(dir, "project.conf", "teralizer { pitest { enabled = true } }");
        Config overrides = ConfigFactory.parseString("teralizer { pitest { enabled = false } }");

        Config config = Configuration.buildConfig(project.toString(), overrides, ConfigFactory.empty());

        assertFalse(config.getBoolean("teralizer.pitest.enabled"), "override (e.g. sysprop) wins");
    }

    @Test
    void referenceIsTheFallback(@TempDir Path dir) throws IOException {
        Path project = writeConf(dir, "project.conf", "teralizer { project { root-path = \"p\" } }");
        Config reference = ConfigFactory.parseString("teralizer { jpf { max-execution-time = 10 } }");

        Config config = Configuration.buildConfig(project.toString(), ConfigFactory.empty(), reference);

        assertEquals(10, config.getInt("teralizer.jpf.max-execution-time"), "unset keys fall back to reference");
        assertEquals("p", config.getString("teralizer.project.root-path"));
    }

    @Test
    void projectUseTestReductionDefaultsToTrue() {
        assertTrue(Configuration.getProjectUseTestReduction());
    }

    @Test
    void emptyPathsYieldsOverridesOverReference() {
        Config reference = ConfigFactory.parseString("teralizer { a = 1, b = 1 }");
        Config overrides = ConfigFactory.parseString("teralizer { b = 2 }");

        Config config = Configuration.buildConfig(null, overrides, reference);

        assertEquals(1, config.getInt("teralizer.a"));
        assertEquals(2, config.getInt("teralizer.b"));
    }

    @Test
    void missingConfigFileThrows() {
        assertThrows(RuntimeException.class, () -> Configuration.buildConfig(
            "/nonexistent/teralizer-config.conf", ConfigFactory.empty(), ConfigFactory.empty()));
    }
}
