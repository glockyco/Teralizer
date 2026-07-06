package teralizer.transformer;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import teralizer.domain.CapturedInput;
import teralizer.domain.Value;

/**
 * Gson (de)serializer for {@link CapturedInput}: the {@link Value} object form (see
 * {@link ValueJsonAdapter}) extended with a {@code name} member carrying the wrapper parameter
 * name. The name is the downstream mapping key, so a captured-input file without names fails
 * loud here instead of mapping positionally by luck.
 */
public final class CapturedInputJsonAdapter implements JsonSerializer<CapturedInput>, JsonDeserializer<CapturedInput> {

    @Override
    public JsonElement serialize(CapturedInput src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject object = context.serialize(src.getValue(), Value.class).getAsJsonObject();
        object.addProperty("name", src.getName());
        return object;
    }

    @Override
    public CapturedInput deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
        JsonObject object = json.getAsJsonObject();
        if (!object.has("name")) {
            throw new JsonParseException("Captured input is missing the wrapper parameter name: " + object);
        }
        Value value = context.deserialize(json, Value.class);
        return new CapturedInput(object.get("name").getAsString(), value);
    }
}
