package teralizer.jpf;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import teralizer.transformer.ModelToJsonTransformer;
import teralizer.transformer.SpecificationGson;

/**
 * Serializes a captured {@link CapturedInvocation} to the four specification files. Pure with respect to
 * JPF/SPF: it operates only on Model POJOs and typed value records, so it runs after the JPF search
 * has terminated without depending on any SPF object remaining valid.
 *
 * <p>The concrete input values and the {@link teralizer.domain.CapturedOutput} are written with the
 * typed {@code Value}/{@code CapturedOutput} adapters (see {@link SpecificationGson}); the symbolic
 * input/output models are written by {@link ModelToJsonTransformer}.
 */
public final class SpecificationExtractor {

    private final ModelToJsonTransformer modelToJsonTransformer = new ModelToJsonTransformer();
    private final Gson gson = SpecificationGson.create();

    public void write(
        CapturedInvocation invocation,
        Path inputValuesPath,
        Path outputValuePath,
        Path inputSpecificationPath,
        Path outputSpecificationPath
    ) {
        String jsonInput = this.modelToJsonTransformer.transform(invocation.getModelInput());
        String jsonOutput = this.modelToJsonTransformer.transform(invocation.getModelOutput());

        try {
            Files.write(inputValuesPath, this.gson.toJson(invocation.getConcreteInputs()).getBytes());
            Files.write(outputValuePath, this.gson.toJson(invocation.getOutput()).getBytes());
            Files.write(inputSpecificationPath, jsonInput.getBytes());
            Files.write(outputSpecificationPath, jsonOutput.getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
