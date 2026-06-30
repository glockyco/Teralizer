package teralizer.jpf;

import teralizer.domain.CapturedOutput;
import teralizer.domain.Expression;
import teralizer.domain.Value;

import java.util.List;

/**
 * The raw result of one tested-method invocation, captured during the run: the concrete input
 * {@link Value}s and the {@link CapturedOutput} (a returned value, a void return, or a thrown
 * exception), plus the symbolic input/output already transformed to the {@link Expression} Model at
 * capture, where the SPF objects are still valid. It holds only Model POJOs and typed value records,
 * so downstream serialization runs after the search terminates without depending on any SPF object
 * remaining live.
 */
public final class Invocation {

    private final List<Value> concreteInputs;
    private final CapturedOutput output;
    private final Expression modelInput;
    private final Expression modelOutput;

    public Invocation(
        List<Value> concreteInputs,
        CapturedOutput output,
        Expression modelInput,
        Expression modelOutput
    ) {
        if (concreteInputs == null) {
            throw new IllegalArgumentException("concreteInputs must not be null");
        }
        if (output == null) {
            throw new IllegalArgumentException("output must not be null");
        }
        this.concreteInputs = concreteInputs;
        this.output = output;
        this.modelInput = modelInput;
        this.modelOutput = modelOutput;
    }

    public List<Value> getConcreteInputs() {
        return this.concreteInputs;
    }

    public CapturedOutput getOutput() {
        return this.output;
    }

    public Expression getModelInput() {
        return this.modelInput;
    }

    public Expression getModelOutput() {
        return this.modelOutput;
    }
}
