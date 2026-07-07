package teralizer.processing;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.jqwik.api.Example;
import org.junit.Assert;

public class BuildClasspathResolverTest {
    @Example
    void dependencies_are_relativized_after_compiled_paths_in_order() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");
        String firstDependency = workingDir.resolve(".m2/repository/org/example/first.jar").toString();
        String secondDependency = workingDir.resolve(".m2/repository/org/example/second.jar").toString();

        String classpath = BuildClasspathResolver.assembleClasspath(
            firstDependency + File.pathSeparator + secondDependency,
            mainCompiled,
            testCompiled,
            workingDir);

        // Expected relative paths are built through Paths so the separator matches the
        // platform (CI runs this on Windows, where relativize produces backslashes).
        Assert.assertEquals(
            mainCompiled + File.pathSeparator +
                testCompiled + File.pathSeparator +
                Paths.get(".m2", "repository", "org", "example", "first.jar") + File.pathSeparator +
                Paths.get(".m2", "repository", "org", "example", "second.jar"),
            classpath);
    }

    @Example
    void empty_raw_classpath_keeps_only_compiled_paths() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");

        String classpath = BuildClasspathResolver.assembleClasspath("", mainCompiled, testCompiled, workingDir);

        Assert.assertEquals(mainCompiled + File.pathSeparator + testCompiled, classpath);
    }

    @Example
    void blank_raw_classpath_keeps_only_compiled_paths() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");

        String classpath = BuildClasspathResolver.assembleClasspath("  \n", mainCompiled, testCompiled, workingDir);

        Assert.assertEquals(mainCompiled + File.pathSeparator + testCompiled, classpath);
    }
}
