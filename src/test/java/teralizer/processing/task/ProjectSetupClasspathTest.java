package teralizer.processing.task;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.jqwik.api.Example;
import org.junit.Assert;

public class ProjectSetupClasspathTest {
    @Example
    void dependencies_are_relativized_after_compiled_paths_in_order() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");
        String firstDependency = workingDir.resolve(".m2/repository/org/example/first.jar").toString();
        String secondDependency = workingDir.resolve(".m2/repository/org/example/second.jar").toString();

        String classpath = ProjectSetupTask.assembleClasspath(
            firstDependency + File.pathSeparator + secondDependency,
            mainCompiled,
            testCompiled,
            workingDir);

        Assert.assertEquals(
            mainCompiled + File.pathSeparator +
                testCompiled + File.pathSeparator +
                ".m2/repository/org/example/first.jar" + File.pathSeparator +
                ".m2/repository/org/example/second.jar",
            classpath);
    }

    @Example
    void empty_raw_classpath_keeps_only_compiled_paths() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");

        String classpath = ProjectSetupTask.assembleClasspath("", mainCompiled, testCompiled, workingDir);

        Assert.assertEquals(mainCompiled + File.pathSeparator + testCompiled, classpath);
    }

    @Example
    void blank_raw_classpath_keeps_only_compiled_paths() {
        Path workingDir = Paths.get("/workspace/repo");
        Path mainCompiled = workingDir.resolve("project/target/classes");
        Path testCompiled = workingDir.resolve("project/target/test-classes");

        String classpath = ProjectSetupTask.assembleClasspath("  \n", mainCompiled, testCompiled, workingDir);

        Assert.assertEquals(mainCompiled + File.pathSeparator + testCompiled, classpath);
    }
}
