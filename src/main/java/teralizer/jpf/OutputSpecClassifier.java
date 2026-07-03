package teralizer.jpf;

import java.util.HashSet;
import java.util.Set;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Expression;
import teralizer.transformer.VariableNameCollector;

public final class OutputSpecClassifier {

    public enum OutputSpecClass {
        SYMBOLIC,
        CONSTANT,
        NULL_CONCRETE,
        EXCEPTION
    }

    private OutputSpecClassifier() {}

    /**
     * Classify the persisted output specification by the shape of the captured output model.
     * {@link OutputSpecClass#NULL_CONCRETE} is expected for boolean-returning MUTs whose comparison
     * result has no return-value attribute: the relation remains in the path condition, so this is
     * telemetry about where the output model is absent rather than an extraction error.
     */
    public static OutputSpecClass classify(Invocation invocation) {
        if (invocation.getOutput().getKind() == CapturedOutput.Kind.THROWN) {
            return OutputSpecClass.EXCEPTION;
        }

        Expression modelOutput = invocation.getModelOutput();
        if (modelOutput == null) {
            return OutputSpecClass.NULL_CONCRETE;
        }

        Set<String> variableNames = new HashSet<>();
        modelOutput.accept(new VariableNameCollector(variableNames));
        return variableNames.isEmpty() ? OutputSpecClass.CONSTANT : OutputSpecClass.SYMBOLIC;
    }
}
