package teralizer.transformer;

import teralizer.domain.Model;

/**
 * Signals that a {@link Model} (or sub-expression) cannot be rendered to a Java
 * expression because it uses an operator or node kind the renderer has no case for.
 *
 * <p>This is a typed, attributable outcome rather than a bare {@link RuntimeException}
 * so callers can distinguish "this clause is non-generalizable" from unrelated runtime
 * failures and decide — per clause — whether to drop it (sound only when the clause
 * references solely non-symbolized parameters) or to fail the generalization.
 */
public class NonGeneralizableExpressionException extends RuntimeException {
    public NonGeneralizableExpressionException(String message) {
        super(message);
    }
}
