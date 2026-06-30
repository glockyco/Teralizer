package teralizer.domain;

/**
 * A captured null reference. Its declared type is retained so a generated supplier or oracle can
 * still cast or coerce correctly (e.g. distinguishing a null {@code java.lang.Boolean} from a null
 * {@code java.lang.String}), rather than collapsing to the untyped literal text {@code "null"}.
 */
public final class NullValue extends Value {

    private final String javaType;

    public NullValue(String javaType) {
        if (javaType == null || javaType.trim().isEmpty()) {
            throw new IllegalArgumentException("NullValue requires a declared type");
        }
        if (JavaTypes.isPrimitive(javaType)) {
            throw new IllegalArgumentException("null is not valid for primitive type " + javaType);
        }
        this.javaType = javaType;
    }

    @Override
    public String getJavaType() {
        return this.javaType;
    }
}
