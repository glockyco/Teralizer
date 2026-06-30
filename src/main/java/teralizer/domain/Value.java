package teralizer.domain;

/**
 * A concrete value captured during specification extraction — a tested method's input or its
 * returned output. Unlike {@link MethodArgument}, which carries a stringly {@code (type, value)}
 * pair, a {@code Value} holds its payload in a type-faithful form so rendering and serialization
 * never re-parse a string: a boxed primitive keeps its exact wrapper (preserving e.g. {@code float}
 * precision), a string keeps its raw content (escaped only when rendered), and a null reference is
 * its own variant rather than the literal text {@code "null"}.
 *
 * <p>The closed set of variants — {@link PrimitiveValue}, {@link StringValue}, {@link NullValue} —
 * matches what the supported-type ceiling admits as a generalizable value: a primitive or its
 * wrapper, a {@code String}, or a null reference. Non-null object references are rejected upstream,
 * so they need no variant here.
 */
public abstract class Value {

    /** The declared Java type of the value's slot (e.g. {@code int}, {@code java.lang.Boolean}). */
    public abstract String getJavaType();
}
