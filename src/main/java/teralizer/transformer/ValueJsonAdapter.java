package teralizer.transformer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import teralizer.domain.JavaTypes;
import teralizer.domain.NullValue;
import teralizer.domain.PrimitiveValue;
import teralizer.domain.ReferenceValue;
import teralizer.domain.StringValue;
import teralizer.domain.Value;

/**
 * Gson (de)serializer for the typed {@link Value} hierarchy, using a {@code kind}-tagged object form
 * so the variant and the declared type survive the round trip. A boxed primitive is reconstructed
 * into its exact wrapper from the declared type (Gson would otherwise read every JSON number as a
 * {@code double}). Non-finite reals are written as their canonical token ({@code NaN},
 * {@code Infinity}, {@code -Infinity}) because JSON has no literal for them; a {@code char} is
 * written as its integer code point.
 *
 * <p>Register with {@code registerTypeHierarchyAdapter(Value.class, new ValueJsonAdapter())} so it
 * applies to the concrete subtypes when serializing a {@code List<Value>}.
 */
public final class ValueJsonAdapter implements JsonSerializer<Value>, JsonDeserializer<Value> {

    @Override
    public JsonElement serialize(Value src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        if (src instanceof NullValue) {
            object.addProperty("kind", "NULL");
            object.addProperty("type", src.getJavaType());
        } else if (src instanceof StringValue) {
            object.addProperty("kind", "STRING");
            object.addProperty("value", ((StringValue) src).getValue());
        } else if (src instanceof PrimitiveValue) {
            object.addProperty("kind", "PRIMITIVE");
            object.addProperty("type", src.getJavaType());
            addPrimitivePayload(object, ((PrimitiveValue) src).getValue());
        } else if (src instanceof ReferenceValue) {
            object.addProperty("kind", "REFERENCE");
            object.addProperty("type", src.getJavaType());
        } else {
            throw new IllegalArgumentException("Unknown Value variant: " + src.getClass().getName());
        }
        return object;
    }

    private static void addPrimitivePayload(JsonObject object, Object boxed) {
        if (boxed instanceof Character) {
            object.addProperty("value", (int) ((Character) boxed).charValue());
        } else if (boxed instanceof Boolean) {
            object.addProperty("value", (Boolean) boxed);
        } else if (boxed instanceof Float) {
            Float value = (Float) boxed;
            if (value.isNaN() || value.isInfinite()) {
                object.addProperty("value", value.toString());
            } else {
                object.addProperty("value", value);
            }
        } else if (boxed instanceof Double) {
            Double value = (Double) boxed;
            if (value.isNaN() || value.isInfinite()) {
                object.addProperty("value", value.toString());
            } else {
                object.addProperty("value", value);
            }
        } else {
            object.addProperty("value", (Number) boxed);
        }
    }

    @Override
    public Value deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject object = json.getAsJsonObject();
        String kind = object.get("kind").getAsString();
        switch (kind) {
            case "NULL":
                return new NullValue(object.get("type").getAsString());
            case "STRING":
                return new StringValue(object.get("value").getAsString());
            case "PRIMITIVE":
                String type = object.get("type").getAsString();
                return new PrimitiveValue(type, parsePrimitive(type, object.get("value")));
            case "REFERENCE":
                return new ReferenceValue(object.get("type").getAsString());
            default:
                throw new JsonParseException("Unknown Value kind: " + kind);
        }
    }

    private static Object parsePrimitive(String javaType, JsonElement value) {
        Class<?> wrapper = JavaTypes.wrapperFor(javaType);
        if (wrapper == Byte.class) {
            return value.getAsByte();
        }
        if (wrapper == Short.class) {
            return value.getAsShort();
        }
        if (wrapper == Integer.class) {
            return value.getAsInt();
        }
        if (wrapper == Long.class) {
            return value.getAsLong();
        }
        if (wrapper == Float.class) {
            return value.getAsJsonPrimitive().isString() ? Float.parseFloat(value.getAsString()) : value.getAsFloat();
        }
        if (wrapper == Double.class) {
            return value.getAsJsonPrimitive().isString() ? Double.parseDouble(value.getAsString()) : value.getAsDouble();
        }
        if (wrapper == Boolean.class) {
            return value.getAsBoolean();
        }
        if (wrapper == Character.class) {
            return (char) value.getAsInt();
        }
        throw new JsonParseException("Not a primitive type: " + javaType);
    }
}
