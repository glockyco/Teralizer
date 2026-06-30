package teralizer.domain;

/**
 * A captured primitive or boxed-wrapper value, held as its host {@link Object} wrapper
 * ({@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float}, {@link Double},
 * {@link Boolean}, or {@link Character}). Storing the wrapper rather than a string keeps the value
 * exact — a {@code float} renders as {@code 3.14F}, not the widened {@code 3.140000104904175F}.
 *
 * <p>The constructor enforces that the payload's wrapper matches the declared {@code javaType}
 * (accepting both the primitive and wrapper spelling, e.g. {@code int} and {@code java.lang.Integer}),
 * so an inconsistent pair cannot be built — the typed boundary cannot silently carry a mismatched
 * value the way the stringly {@link MethodArgument} could. A {@code null} payload is invalid; a null
 * reference is a {@link NullValue}.
 */
public final class PrimitiveValue extends Value {

    private final String javaType;
    private final Object value;

    public PrimitiveValue(String javaType, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("PrimitiveValue payload must not be null; use NullValue");
        }
        Class<?> expectedWrapper = JavaTypes.wrapperFor(javaType);
        if (expectedWrapper == null) {
            throw new IllegalArgumentException("Not a primitive or wrapper type: " + javaType);
        }
        if (!expectedWrapper.isInstance(value)) {
            throw new IllegalArgumentException(
                "PrimitiveValue payload " + value.getClass().getName() + " does not match declared type " + javaType);
        }
        this.javaType = javaType;
        this.value = value;
    }

    @Override
    public String getJavaType() {
        return this.javaType;
    }

    /** The boxed wrapper holding the exact value. */
    public Object getValue() {
        return this.value;
    }
}
