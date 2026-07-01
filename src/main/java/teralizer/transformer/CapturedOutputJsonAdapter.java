package teralizer.transformer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import teralizer.domain.CapturedException;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Value;

/**
 * Gson (de)serializer for {@link CapturedOutput}, tagging the three outcome kinds so a returned
 * value, a void return, and a thrown exception are distinguishable on disk. The nested return value
 * is delegated to the registered {@link Value} adapter; a thrown exception is written as its name and
 * (nullable) message.
 */
public final class CapturedOutputJsonAdapter implements JsonSerializer<CapturedOutput>, JsonDeserializer<CapturedOutput> {

    @Override
    public JsonElement serialize(CapturedOutput src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = new JsonObject();
        object.addProperty("kind", src.getKind().name());
        switch (src.getKind()) {
            case RETURNED_VALUE:
                object.add("value", context.serialize(src.getReturnValue(), Value.class));
                break;
            case VOID:
                break;
            case THROWN:
                JsonObject exception = new JsonObject();
                exception.addProperty("name", src.getThrownException().getName());
                exception.addProperty("message", src.getThrownException().getMessage());
                object.add("exception", exception);
                break;
            default:
                throw new IllegalArgumentException("Unknown CapturedOutput kind: " + src.getKind());
        }
        return object;
    }

    @Override
    public CapturedOutput deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject object = json.getAsJsonObject();
        CapturedOutput.Kind kind = CapturedOutput.Kind.valueOf(object.get("kind").getAsString());
        switch (kind) {
            case RETURNED_VALUE:
                return CapturedOutput.ofReturnValue(context.deserialize(object.get("value"), Value.class));
            case VOID:
                return CapturedOutput.ofVoid();
            case THROWN:
                JsonObject exception = object.getAsJsonObject("exception");
                JsonElement message = exception.get("message");
                return CapturedOutput.ofThrow(new CapturedException(
                    exception.get("name").getAsString(),
                    message == null || message.isJsonNull() ? null : message.getAsString()));
            default:
                throw new JsonParseException("Unknown CapturedOutput kind: " + kind);
        }
    }
}
