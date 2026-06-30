package teralizer.domain;

/**
 * Classification of Java primitive and wrapper type names, shared by the typed {@link Value}
 * variants so the primitive/wrapper knowledge lives in one place rather than being duplicated
 * across capture, rendering, and validation.
 */
public final class JavaTypes {

    private JavaTypes() {
    }

    /**
     * The host wrapper class for a primitive or wrapper type name (e.g. {@code int} or
     * {@code java.lang.Integer} both map to {@link Integer}), or {@code null} if the name is neither.
     * Null-safe: a {@code null} type name yields {@code null}.
     */
    public static Class<?> wrapperFor(String javaType) {
        if (javaType == null) {
            return null;
        }
        switch (javaType) {
            case "byte":
            case "java.lang.Byte":
                return Byte.class;
            case "short":
            case "java.lang.Short":
                return Short.class;
            case "int":
            case "java.lang.Integer":
                return Integer.class;
            case "long":
            case "java.lang.Long":
                return Long.class;
            case "float":
            case "java.lang.Float":
                return Float.class;
            case "double":
            case "java.lang.Double":
                return Double.class;
            case "boolean":
            case "java.lang.Boolean":
                return Boolean.class;
            case "char":
            case "java.lang.Character":
                return Character.class;
            default:
                return null;
        }
    }

    /**
     * Whether the type name is one of the eight primitive types (which can never hold {@code null}).
     * Wrapper names (e.g. {@code java.lang.Integer}) are reference types and return {@code false}.
     */
    public static boolean isPrimitive(String javaType) {
        if (javaType == null) {
            return false;
        }
        switch (javaType) {
            case "byte":
            case "short":
            case "int":
            case "long":
            case "float":
            case "double":
            case "boolean":
            case "char":
                return true;
            default:
                return false;
        }
    }
}
