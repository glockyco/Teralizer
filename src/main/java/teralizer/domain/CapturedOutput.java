package teralizer.domain;

/**
 * The outcome of one tested-method invocation: it returned a value, returned from a {@code void}
 * method, or threw an exception. Exactly one case holds, enforced by the factory methods, so the
 * ambiguous {@code (type, value)} pair that previously crammed an exception's class name into a
 * value slot is gone.
 */
public final class CapturedOutput {

    public enum Kind {
        /** The method returned a value, held in {@link #getReturnValue()}. */
        RETURNED_VALUE,
        /** The method returned from a {@code void} signature; there is no value. */
        VOID,
        /** The method threw, captured in {@link #getThrownException()}. */
        THROWN
    }

    private final Kind kind;
    private final Value returnValue;
    private final CapturedException thrownException;

    private CapturedOutput(Kind kind, Value returnValue, CapturedException thrownException) {
        this.kind = kind;
        this.returnValue = returnValue;
        this.thrownException = thrownException;
    }

    public static CapturedOutput ofReturnValue(Value returnValue) {
        if (returnValue == null) {
            throw new IllegalArgumentException("returned value must not be null; use ofVoid() for void returns");
        }
        return new CapturedOutput(Kind.RETURNED_VALUE, returnValue, null);
    }

    public static CapturedOutput ofVoid() {
        return new CapturedOutput(Kind.VOID, null, null);
    }

    public static CapturedOutput ofThrow(CapturedException thrownException) {
        if (thrownException == null) {
            throw new IllegalArgumentException("thrown exception must not be null");
        }
        return new CapturedOutput(Kind.THROWN, null, thrownException);
    }

    public Kind getKind() {
        return this.kind;
    }

    /** The returned value; valid only when {@link #getKind()} is {@link Kind#RETURNED_VALUE}. */
    public Value getReturnValue() {
        if (this.kind != Kind.RETURNED_VALUE) {
            throw new IllegalStateException("no return value for output kind " + this.kind);
        }
        return this.returnValue;
    }

    /** The thrown exception; valid only when {@link #getKind()} is {@link Kind#THROWN}. */
    public CapturedException getThrownException() {
        if (this.kind != Kind.THROWN) {
            throw new IllegalStateException("no thrown exception for output kind " + this.kind);
        }
        return this.thrownException;
    }
}
