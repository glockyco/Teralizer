package teralizer.spoon.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.jqwik.api.Example;
import org.junit.Assert;

class GeneratedTestValidatorTest {

    @Example
    void flagsOnlyTheUncompilableFile() throws Exception {
        Path root = Files.createTempDirectory("validator-test");
        Path good = write(root, "Good.java", "public class Good { int v() { return 1; } }");
        Path bad = write(root, "Bad.java", "public class Bad { int v() { return Nonexistent.MISSING; } }");

        Map<Path, String> errors = GeneratedTestValidator.compilationErrors(
            Arrays.asList(good, bad), "", Collections.singletonList(root));

        Assert.assertEquals(Collections.singleton(bad), errors.keySet());
        assertCapturedJavacError(errors.get(bad), "Nonexistent");
    }

    @Example
    void resolvesReferencedSourcesThroughSourcepath() throws Exception {
        Path root = Files.createTempDirectory("validator-sourcepath");
        // Dependency lives only as source on the sourcepath, never precompiled: the validator must
        // resolve it (this is how a generated wrapper resolves the untouched original test/main).
        write(root, "Dep.java", "public class Dep { static int seed() { return 5; } }");
        Path user = write(root, "User.java", "public class User { int v() { return Dep.seed(); } }");

        Map<Path, String> errors = GeneratedTestValidator.compilationErrors(
            Collections.singletonList(user), "", Collections.singletonList(root));

        Assert.assertTrue("User resolves Dep through the sourcepath", errors.isEmpty());
    }

    @Example
    void emptyInputYieldsNoFailures() {
        Map<Path, String> errors = GeneratedTestValidator.compilationErrors(
            Collections.emptyList(), "", Collections.emptyList());

        Assert.assertTrue(errors.isEmpty());
    }

    @Example
    void flagsUncompilableFileGivenRelativePaths() throws Exception {
        // The pipeline passes repo-root-relative generated paths, while javac reports absolute
        // source URIs. The returned set must still match the caller's original (relative) Path.
        Path root = Files.createTempDirectory("validator-relative").toAbsolutePath();
        write(root, "Bad.java", "public class Bad { int v() { return Nonexistent.MISSING; } }");
        Path cwd = java.nio.file.Paths.get("").toAbsolutePath();
        Path relativeBad = cwd.relativize(root.resolve("Bad.java"));
        Path relativeRoot = cwd.relativize(root);

        Map<Path, String> errors = GeneratedTestValidator.compilationErrors(
            Collections.singletonList(relativeBad), "", Collections.singletonList(relativeRoot));

        Assert.assertEquals(Collections.singleton(relativeBad), errors.keySet());
        assertCapturedJavacError(errors.get(relativeBad), "Nonexistent");
    }

    private static void assertCapturedJavacError(String errorText, String expectedFragment) {
        Assert.assertNotNull(errorText);
        Assert.assertFalse(errorText.trim().isEmpty());
        Assert.assertTrue(errorText, errorText.startsWith("line "));
        Assert.assertTrue(errorText,
            errorText.contains(expectedFragment) || errorText.contains("cannot find symbol"));
    }

    private static Path write(Path root, String name, String source) throws Exception {
        Path file = root.resolve(name);
        Files.write(file, source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }
}
