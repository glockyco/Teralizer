package teralizer.jpf;

import teralizer.domain.Expression;
import teralizer.domain.MethodArgument;

import java.util.List;

/**
 * The raw result of one tested-method invocation, captured during the run: concrete input/output
 * values plus the symbolic input/output already transformed to the {@link Expression} Model at
 * capture, where the SPF objects are still valid. It holds only Model POJOs and concrete-value
 * records, so downstream serialization runs after the search terminates without depending on any
 * SPF object remaining live.
 */
public final class Invocation {

    private final List<MethodArgument> concreteInputs;
    private final MethodArgument concreteOutput;
    private final Expression modelInput;
    private final Expression modelOutput;

    public Invocation(
        List<MethodArgument> concreteInputs,
        MethodArgument concreteOutput,
        Expression modelInput,
        Expression modelOutput
    ) {
        this.concreteInputs = concreteInputs;
        this.concreteOutput = concreteOutput;
        this.modelInput = modelInput;
        this.modelOutput = modelOutput;
    }

    public List<MethodArgument> getConcreteInputs() {
        return this.concreteInputs;
    }

    public MethodArgument getConcreteOutput() {
        return this.concreteOutput;
    }

    public Expression getModelInput() {
        return this.modelInput;
    }

    public Expression getModelOutput() {
        return this.modelOutput;
    }
}
