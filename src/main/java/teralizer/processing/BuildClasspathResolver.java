package teralizer.processing;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;

/**
 * Resolves the compile classpath a build file declares, as {@code <main>:<test>:<deps...>}.
 *
 * <p>A project is resolved twice in a pipeline run against two different build files: the original
 * ({@code pom.xml} / {@code build.gradle}) at setup, and the tool's custom build file
 * ({@code pom.teralizer.xml} / {@code build.teralizer.gradle}) after {@code AddDependenciesTask}
 * adds jqwik, pitest, and the junit-platform runner. The generated wrappers and generalized tests
 * compile against the custom build file, so any consumer that must see those added dependencies
 * (the generated-test validator, the instrumented/generalized builds) needs the classpath resolved
 * from it, not from the original. Keeping the resolution in one place stops the two callers from
 * drifting.
 */
public final class BuildClasspathResolver {

    private BuildClasspathResolver() {
    }

    /**
     * Resolve a Maven build file's classpath via {@code dependency:build-classpath}. {@code -f}
     * targets the named build file so the tool's custom POM (with the added dependencies) resolves,
     * not only the original {@code pom.xml}.
     */
    public static String resolveMaven(Path rootPath, String buildFileName, Path mainCompiled, Path testCompiled)
        throws IOException, InterruptedException {
        Path classpathOutputFile = Files.createTempFile("teralizer-classpath", ".txt").toAbsolutePath();
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                "mvn",
                "-q",
                "-f", buildFileName,
                "dependency:build-classpath",
                "-Dmdep.outputFile=" + classpathOutputFile);
            processBuilder.directory(rootPath.toFile());
            Process process = processBuilder.start();

            try (
                InputStreamReader outputStream = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8);
                BufferedReader outputReader = new BufferedReader(outputStream);
                InputStreamReader errorStream = new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8);
                BufferedReader errorReader = new BufferedReader(errorStream)
            ) {
                String line;
                while ((line = outputReader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                while ((line = errorReader.readLine()) != null) {
                    error.append(line).append("\n");
                }
            }

            if (process.waitFor() != 0) {
                throw new RuntimeException("Failed to resolve classpath from " + buildFileName + ".\nOutput:\n\n"
                    + output + (error.toString().isEmpty() ? "" : "\n\nError:\n\n" + error));
            }

            String rawClasspath = new String(Files.readAllBytes(classpathOutputFile), StandardCharsets.UTF_8).trim();
            return assembleClasspath(rawClasspath, mainCompiled, testCompiled, Paths.get(System.getProperty("user.dir")));
        } finally {
            Files.deleteIfExists(classpathOutputFile);
        }
    }

    /**
     * Resolve a Gradle build file's classpath through the tooling API. {@code --build-file} targets
     * the named build file so the tool's custom build script (with the added dependencies)
     * resolves, not only the original {@code build.gradle}.
     */
    public static String resolveGradle(Path rootPath, String buildFileName, Path mainCompiled, Path testCompiled) {
        File projectDirectoryFile = rootPath.toFile();
        if (!projectDirectoryFile.exists() || !projectDirectoryFile.isDirectory()) {
            throw new IllegalArgumentException("Invalid project directory: " + rootPath);
        }

        List<String> classpathElements = new ArrayList<>();
        classpathElements.add(mainCompiled.toString());
        classpathElements.add(rootPath.resolve("build/classes/java/test").toString());

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectDirectoryFile);
        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class)
                .withArguments("--build-file", buildFileName);
            EclipseProject project = modelBuilder.get();
            project.getClasspath().forEach(dependency -> classpathElements.add(dependency.getFile().toString()));
        }

        return String.join(File.pathSeparator, classpathElements);
    }

    /**
     * Prepend the compiled output dirs to the resolved dependency classpath. Dependency paths are
     * relativized against the working directory to match the pipeline's repo-root-relative form.
     */
    public static String assembleClasspath(String rawClasspath, Path mainCompiled, Path testCompiled, Path workingDir) {
        List<String> classpathElements = new ArrayList<>();
        classpathElements.add(mainCompiled.toString());
        classpathElements.add(testCompiled.toString());
        if (rawClasspath != null && !rawClasspath.trim().isEmpty()) {
            Arrays.stream(rawClasspath.trim().split(File.pathSeparator))
                .map(path -> workingDir.relativize(Paths.get(path)).toString())
                .forEach(classpathElements::add);
        }
        return String.join(File.pathSeparator, classpathElements);
    }
}
