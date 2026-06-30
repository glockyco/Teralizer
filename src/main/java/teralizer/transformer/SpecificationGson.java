package teralizer.transformer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Value;

/**
 * Builds the {@link Gson} used to read and write the concrete specification files (the captured
 * input {@link Value}s and the {@link CapturedOutput}), with the typed adapters registered. Sharing
 * one factory keeps the wire contract identical across the writer ({@code SpecificationExtractor})
 * and every reader.
 */
public final class SpecificationGson {

    private SpecificationGson() {
    }

    public static Gson create() {
        return new GsonBuilder()
            .registerTypeHierarchyAdapter(Value.class, new ValueJsonAdapter())
            .registerTypeAdapter(CapturedOutput.class, new CapturedOutputJsonAdapter())
            .create();
    }
}
