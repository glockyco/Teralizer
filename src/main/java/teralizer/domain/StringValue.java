package teralizer.domain;

/**
 * A captured {@link String} value, holding the raw content. Escaping into a Java string literal
 * happens only at render time, so a value containing quotes, backslashes, newlines, or control
 * characters (including NUL) round-trips faithfully and never corrupts the generated source.
 */
public final class StringValue extends Value {

    private final String value;

    public StringValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("StringValue content must not be null; use NullValue");
        }
        this.value = value;
    }

    @Override
    public String getJavaType() {
        return "java.lang.String";
    }

    /** The raw, unescaped string content. */
    public String getValue() {
        return this.value;
    }
}
