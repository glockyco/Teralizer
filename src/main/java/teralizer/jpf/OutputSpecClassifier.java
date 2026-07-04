package teralizer.jpf;

import java.util.HashSet;
import java.util.Set;
import teralizer.domain.CapturedOutput;
import teralizer.domain.Model;
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
     *
     * <p>{@link OutputSpecClass#NULL_CONCRETE} covers both null-output-model siblings: a benign
     * boolean relation that lives in the path condition because the return value has no separate
     * attribute, and an unlicensed concrete oracle whose expected side cannot co-vary with widened
     * inputs. The classifier intentionally names only the model shape; {@link
     * teralizer.generalization.WideningLicense} consumes this class together with return type,
     * path-condition, and concretization evidence to decide which sibling may be widened.
     */
    public static OutputSpecClass classify(CapturedInvocation invocation) {
        return classify(invocation.getOutput().getKind(), invocation.getModelOutput());
    }

    public static OutputSpecClass classify(CapturedOutput.Kind outputKind, Model modelOutput) {
        if (outputKind == CapturedOutput.Kind.THROWN) {
            return OutputSpecClass.EXCEPTION;
        }

        if (modelOutput == null) {
            return OutputSpecClass.NULL_CONCRETE;
        }

        Set<String> variableNames = new HashSet<>();
        modelOutput.accept(new VariableNameCollector(variableNames));
        return variableNames.isEmpty() ? OutputSpecClass.CONSTANT : OutputSpecClass.SYMBOLIC;
    }
}
