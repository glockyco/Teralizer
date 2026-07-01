package teralizer.domain;

public enum TypeDomain {
    INTEGER,
    REAL,
    BOOLEAN,
    CHAR,
    STRING,
    ARRAY,
    OBJECT;

    public static TypeDomain from(String type) {
        if (type == null) {
            return OBJECT;
        }
        switch (type) {
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "long":
            case "java.lang.Long":
                return INTEGER;
            case "float":
            case "java.lang.Float":
            case "double":
            case "java.lang.Double":
                return REAL;
            case "boolean":
            case "java.lang.Boolean":
                return BOOLEAN;
            case "char":
            case "java.lang.Character":
                return CHAR;
            case "String":
            case "java.lang.String":
                return STRING;
            default:
                return type.endsWith("[]") ? ARRAY : OBJECT;
        }
    }
}
