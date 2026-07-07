package teralizer.spoon.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Compiles a set of generated {@code .java} files in one in-process {@code javac} pass and reports
 * which files carry at least one error. The generated wrappers and generalized tests are extracted
 * out of their original test bodies, so a shape the codegen cannot yet express soundly (a
 * method-local type, an inaccessible inherited member, an unrepresentable expression) yields code
 * that does not compile. The pipeline build compiles the whole test tree atomically, so a single
 * such file would drop an entire project; validating first lets the build task quarantine the
 * offending files per-assertion and keep the rest.
 *
 * <p>javac is the ground-truth oracle for well-formedness: it resolves accessibility, generics, and
 * narrowing exactly, which a no-classpath Spoon model cannot. Referenced originals resolve through
 * {@code -sourcepath} (the main and test source roots), so validation needs no prior compiled
 * state and survives the build's own {@code clean}.
 */
public final class GeneratedTestValidator {

    private GeneratedTestValidator() {
    }

    /**
     * @param generatedFiles the generated files to validate (existing paths only)
     * @param classpath      the project dependency classpath (path-separator joined)
     * @param sourceRoots    the source roots that resolve referenced originals (main, test)
     * @return the subset of {@code generatedFiles} with at least one compilation error
     */
    public static Set<Path> uncompilableFiles(List<Path> generatedFiles, String classpath, List<Path> sourceRoots) {
        if (generatedFiles.isEmpty()) {
            return Collections.emptySet();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("No system Java compiler available (JDK required, not a JRE).");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Path outputDir;
        try {
            outputDir = Files.createTempDirectory("teralizer-validate");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create validation output directory.", e);
        }
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {
            String sourcePath = sourceRoots.stream().map(Path::toString).collect(Collectors.joining(java.io.File.pathSeparator));
            List<String> options = new ArrayList<>();
            options.add("-cp");
            options.add(classpath);
            options.add("-sourcepath");
            options.add(sourcePath);
            options.add("-d");
            options.add(outputDir.toString());
            // Skip annotation processing; the generated tests need none and it avoids classpath scans.
            options.add("-proc:none");

            List<java.io.File> files = generatedFiles.stream().map(Path::toFile).collect(Collectors.toList());
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromFiles(files);
            compiler.getTask(null, fileManager, diagnostics, options, null, units).call();

            Set<Path> generatedSet = new HashSet<>(generatedFiles);
            Set<Path> uncompilable = new HashSet<>();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                if (diagnostic.getKind() != Diagnostic.Kind.ERROR || diagnostic.getSource() == null) {
                    continue;
                }
                Path source = java.nio.file.Paths.get(diagnostic.getSource().toUri());
                if (generatedSet.contains(source)) {
                    uncompilable.add(source);
                }
            }
            return uncompilable;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to validate generated tests.", e);
        } finally {
            deleteRecursively(outputDir);
        }
    }

    private static void deleteRecursively(Path root) {
        try {
            if (!Files.exists(root)) {
                return;
            }
            try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
                paths.sorted(Collections.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // Best-effort cleanup of a temp directory; a leftover file is harmless.
                    }
                });
            }
        } catch (IOException ignored) {
            // Best-effort cleanup of a temp directory; a leftover file is harmless.
        }
    }
}
