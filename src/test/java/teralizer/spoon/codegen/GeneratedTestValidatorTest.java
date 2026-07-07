package teralizer.spoon.codegen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;

class GeneratedTestValidatorTest {

    @Example
    void flagsOnlyTheUncompilableFile() throws Exception {
        Path root = Files.createTempDirectory("validator-test");
        Path good = write(root, "Good.java", "public class Good { int v() { return 1; } }");
        Path bad = write(root, "Bad.java", "public class Bad { int v() { return Nonexistent.MISSING; } }");

        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            Arrays.asList(good, bad), "", Collections.singletonList(root));

        Assert.assertEquals(Collections.singleton(bad), uncompilable);
    }

    @Example
    void resolvesReferencedSourcesThroughSourcepath() throws Exception {
        Path root = Files.createTempDirectory("validator-sourcepath");
        // Dependency lives only as source on the sourcepath, never precompiled: the validator must
        // resolve it (this is how a generated wrapper resolves the untouched original test/main).
        write(root, "Dep.java", "public class Dep { static int seed() { return 5; } }");
        Path user = write(root, "User.java", "public class User { int v() { return Dep.seed(); } }");

        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            Collections.singletonList(user), "", Collections.singletonList(root));

        Assert.assertTrue("User resolves Dep through the sourcepath", uncompilable.isEmpty());
    }

    @Example
    void emptyInputYieldsNoFailures() {
        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            Collections.emptyList(), "", Collections.emptyList());

        Assert.assertTrue(uncompilable.isEmpty());
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

        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            Collections.singletonList(relativeBad), "", Collections.singletonList(relativeRoot));

        Assert.assertEquals(Collections.singleton(relativeBad), uncompilable);
    }

    private static Path write(Path root, String name, String source) throws Exception {
        Path file = root.resolve(name);
        Files.write(file, source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return file;
    }
}
