package teralizer.spoon;

import spoon.Launcher;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

public class SpoonFactory {

    public static Launcher createLauncher(Path mainSourcePath, Path testSourcePath, String classpath) {
        Launcher launcher = new Launcher();

        launcher.addInputResource(mainSourcePath.toString());
        launcher.addInputResource(testSourcePath.toString());

        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setLevel("DEBUG");

        String[] jarDependencies = Arrays.stream(classpath.split(File.pathSeparator))
            .filter(p -> p.endsWith(".jar"))
            .toArray(String[]::new);
        launcher.getEnvironment().setSourceClasspath(jarDependencies);

        launcher.buildModel();

        return launcher;
    }
}
