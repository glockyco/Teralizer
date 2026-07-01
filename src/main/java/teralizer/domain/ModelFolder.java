package teralizer.domain;

import java.util.List;

/**
 * Total bottom-up fold over the concrete {@link Model} node set. Every concrete node
 * has its own abstract hook, so any concrete folder must implement every hook — a
 * missing node case is a compile error, not a silent no-op (the failure mode of the
 * no-op-default {@link ModelVisitor}). Composite hooks receive the already-folded
 * children, so folders need no internal stack.
 *
 * <p>This is the seam for renderers (Model&rarr;Java, Model&rarr;JSON write) that must
 * not silently drop a node kind. Observers that legitimately ignore most nodes keep
 * using {@link ModelVisitor}.
 *
 * @param <T> the folded value type
 */
public abstract class ModelFolder<T> {
    public abstract T fold(Constant constant);
    public abstract T fold(Variable variable);
    public abstract T fold(ArrayExpression expression);
    public abstract T fold(ArrayElementExpression expression, T elementSelector);
    public abstract T fold(Invocation invocation, T receiver, List<T> args);
    public abstract T fold(Not not, T operand);
    public abstract T fold(Operation operation, T left, T right);
    public abstract T fold(Operator operator);
    public abstract T fold(Error error);
    public abstract T fold(ExceptionModel exceptionModel);
}
