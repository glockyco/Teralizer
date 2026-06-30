package teralizer.domain;

/**
 * A captured non-null reference whose value is not a generalizable literal — the receiver of an
 * instance-method call, or an argument of an unsupported reference type. Such a value is never
 * rendered into a generated test (the receiver is offset-skipped when mapping arguments to
 * parameters, and unsupported-typed arguments are dropped downstream), so it carries only its
 * declared type and has no payload.
 *
 * <p>This variant exists so capture can represent such a reference faithfully rather than corrupting
 * it into a {@link NullValue} (which would misrepresent a present object as absent). Rendering one is
 * a bug: {@link teralizer.transformer.ModelToJavaTransformer#transform(Value)} fails fast on it.
 */
public final class ReferenceValue extends Value {

    private final String javaType;

    public ReferenceValue(String javaType) {
        if (javaType == null || javaType.trim().isEmpty()) {
            throw new IllegalArgumentException("ReferenceValue requires a declared type");
        }
        if (JavaTypes.isPrimitive(javaType)) {
            throw new IllegalArgumentException("a primitive cannot be a reference: " + javaType);
        }
        this.javaType = javaType;
    }

    @Override
    public String getJavaType() {
        return this.javaType;
    }
}
