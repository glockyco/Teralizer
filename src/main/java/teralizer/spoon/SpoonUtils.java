package teralizer.spoon;

import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public class SpoonUtils {

    public static CtTypeReference<?> getTypeReference(Factory factory, String typeName) {
        switch (typeName) {
            case "boolean":
                return factory.Type().BOOLEAN_PRIMITIVE;
            case "java.lang.Boolean":
            case "Boolean":
                return factory.Type().BOOLEAN;
            case "byte":
                return factory.Type().BYTE_PRIMITIVE;
            case "java.lang.Byte":
            case "Byte":
                return factory.Type().BYTE;
            case "char":
                return factory.Type().CHARACTER_PRIMITIVE;
            case "java.lang.Character":
            case "Character":
                return factory.Type().CHARACTER;
            case "double":
                return factory.Type().DOUBLE_PRIMITIVE;
            case "java.lang.Double":
            case "Double":
                return factory.Type().DOUBLE;
            case "float":
                return factory.Type().FLOAT_PRIMITIVE;
            case "java.lang.Float":
            case "Float":
                return factory.Type().FLOAT;
            case "int":
                return factory.Type().INTEGER_PRIMITIVE;
            case "java.lang.Integer":
            case "Integer":
                return factory.Type().INTEGER;
            case "long":
                return factory.Type().LONG_PRIMITIVE;
            case "java.lang.Long":
            case "Long":
                return factory.Type().LONG;
            case "short":
                return factory.Type().SHORT_PRIMITIVE;
            case "java.lang.Short":
            case "Short":
                return factory.Type().SHORT;
            case "void":
                return factory.Type().VOID_PRIMITIVE;
            case "java.lang.Void":
            case "Void":
                return factory.Type().VOID;
            default:
                return factory.Type().get(typeName).getReference();
        }
    }
}
