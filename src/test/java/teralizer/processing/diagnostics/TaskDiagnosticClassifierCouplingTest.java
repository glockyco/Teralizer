package teralizer.processing.diagnostics;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.jqwik.api.Example;
import org.junit.Assert;

/**
 * Guards the classifier against matching text that nothing produces.
 *
 * <p>A classifier branch that reads a failure message couples two files that the compiler does not
 * connect. When the wording moves, the branch stops matching and the outcome falls through to the
 * default code, which names an internal defect and is not exempt from structural failure in
 * {@code PipelinePlanner}. Nothing reports the break: the build stays green and the pipeline keeps
 * running.
 *
 * <p>Every literal the classifier matches must therefore be one of two things: a message this
 * project emits, or a message from a named external tool. Prefer a typed exception over either.
 */
public class TaskDiagnosticClassifierCouplingTest {

    private static final Path MAIN = Paths.get("src", "main", "java");
    private static final Path CLASSIFIER = MAIN.resolve(
        Paths.get("teralizer", "processing", "diagnostics", "TaskDiagnosticClassifier.java"));

    /** Literals owned by a tool outside this project, with the tool that prints them. */
    private static final Map<String, String> EXTERNAL_MESSAGES = new LinkedHashMap<>();

    static {
        EXTERNAL_MESSAGES.put("Source option", "javac, source/target release rejection");
        EXTERNAL_MESSAGES.put("release version", "javac, source/target release rejection");
        EXTERNAL_MESSAGES.put("invalid target release", "javac, source/target release rejection");
    }

    private static final Pattern MATCHED_LITERAL =
        Pattern.compile("contains\\(\\s*\\w+\\s*,\\s*\"([^\"]+)\"\\s*\\)");

    @Example
    void everyMatchedMessageIsEmittedHereOrOwnedByANamedTool() {
        String classifier = read(CLASSIFIER);
        List<String> sources = readMainSourcesExcept(CLASSIFIER);

        List<String> unaccounted = new ArrayList<>();
        Matcher matcher = MATCHED_LITERAL.matcher(classifier);
        while (matcher.find()) {
            String literal = matcher.group(1);
            if (EXTERNAL_MESSAGES.containsKey(literal)) {
                continue;
            }
            boolean emittedHere = sources.stream().anyMatch(source -> source.contains(literal));
            if (!emittedHere && !unaccounted.contains(literal)) {
                unaccounted.add(literal);
            }
        }

        Assert.assertEquals(
            "The classifier matches text that no source in src/main/java produces and that no entry "
            + "in EXTERNAL_MESSAGES claims. Either the message was reworded, in which case classify "
            + "on a type instead, or it belongs to an external tool, in which case name that tool in "
            + "EXTERNAL_MESSAGES. Unaccounted: " + unaccounted,
            Collections.emptyList(), unaccounted);
    }

    @Example
    void everyDeclaredExternalMessageIsStillMatched() {
        String classifier = read(CLASSIFIER);

        List<String> stale = EXTERNAL_MESSAGES.keySet().stream()
            .filter(literal -> !classifier.contains('"' + literal + '"'))
            .collect(Collectors.toList());

        Assert.assertEquals(
            "EXTERNAL_MESSAGES claims a literal the classifier no longer matches. Remove the entry: "
            + stale,
            Collections.emptyList(), stale);
    }

    private static List<String> readMainSourcesExcept(Path excluded) {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.equals(excluded))
                .map(TaskDiagnosticClassifierCouplingTest::read)
                .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
