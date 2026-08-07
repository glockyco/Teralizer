package teralizer.spoon.codegen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.jqwik.api.Example;
import org.junit.Assert;

/**
 * Guards the language level of the code the pipeline emits.
 *
 * <p>A project compiles the generalized tests with its own build, and 785 of the 1,172 corpus
 * projects declare a source level below 1.8. A lambda or a method reference in a template fails that
 * compilation, and the failure takes the whole project: {@code BUILD_PROJECT_GENERALIZED} fails and
 * every generalization the project created is lost. Anonymous classes and plain statements carry the
 * same meaning at every source level, and the emitted code already uses them everywhere else.
 */
public class GeneratedSourceLevelTest {

    private static final Path TEMPLATES = Paths.get("src/main/resources/templates");
    private static final Pattern METHOD_REFERENCE = Pattern.compile("[A-Za-z0-9_.$]+::[a-zA-Z]");

    @Example
    void templatesEmitCodeThatCompilesBeforeJava8() throws IOException {
        List<Path> templates = templates();
        Assert.assertFalse("no templates found under " + TEMPLATES, templates.isEmpty());

        for (Path template : templates) {
            for (String line : Files.readAllLines(template, StandardCharsets.UTF_8)) {
                String code = withoutLineComment(line);
                Assert.assertFalse("lambda in " + template.getFileName() + ": " + line.trim(),
                    code.contains("->"));
                Assert.assertFalse("method reference in " + template.getFileName() + ": " + line.trim(),
                    METHOD_REFERENCE.matcher(code).find());
            }
        }
    }

    private static List<Path> templates() throws IOException {
        try (java.util.stream.Stream<Path> paths = Files.walk(TEMPLATES)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".vm"))
                .collect(Collectors.toList());
        }
    }

    private static String withoutLineComment(String line) {
        int comment = line.indexOf("//");
        return comment < 0 ? line : line.substring(0, comment);
    }
}
