package teralizer.transformer;

/**
 * Signals that an SPF constraint chain contains a term kind the
 * {@link SpfToModelTransformer} has no Model node for (the concolic "special"
 * expressions and the {@code FunctionExpression} used for native-peer calls like
 * {@code Double.doubleToRawLongBits}). This is a typed, attributable outcome rather
 * than a bare {@link UnsupportedOperationException}, so the listener can mark the
 * extracted spec incomplete instead of crashing, and a future renderer can
 * distinguish "unsupported SPF term" from a JPF bug.
 */
public class UnsupportedSpfTermException extends RuntimeException {
    public UnsupportedSpfTermException(String message) {
        super(message);
    }
}
