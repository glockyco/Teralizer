package teralizer.processing;

/**
 * Thrown when no assertion of a project reaches test generalization, because every assertion was
 * rejected by a filter. The project has nothing to extract, which is a measured outcome and not
 * pipeline breakage.
 *
 * <p>The type carries that meaning so that {@code TaskDiagnosticClassifier} and
 * {@code PipelinePlanner} recognize the condition without reading the message. The message is prose
 * for a reader and is free to change; the classification decides whether the project halts.
 */
public class NoGeneralizableAssertionException extends RuntimeException {

    public NoGeneralizableAssertionException(String message) {
        super(message);
    }
}
