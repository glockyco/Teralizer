package teralizer.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Method/function call node: instance {@code receiver.method(args)} or static
 * {@code qualifier.method(args)}. Exactly one of receiver/qualifier is set by convention.
 */
public class Invocation implements Expression {
    public final Expression receiver;
    public final String qualifier;
    public final String method;
    public final List<Expression> args;

    public Invocation(Expression receiver, String qualifier, String method, List<Expression> args) {
        this.receiver = receiver;
        this.qualifier = qualifier;
        this.method = method;
        this.args = args;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        if (this.receiver != null) {
            this.receiver.accept(visitor);
        }
        for (Expression arg : this.args) {
            arg.accept(visitor);
        }
        visitor.postVisit(this);
    }

    @Override
    public <T> T fold(ModelFolder<T> folder) {
        T foldedReceiver = this.receiver == null ? null : this.receiver.fold(folder);
        List<T> foldedArgs = new ArrayList<>(this.args.size());
        for (Expression arg : this.args) {
            foldedArgs.add(arg.fold(folder));
        }
        return folder.fold(this, foldedReceiver, foldedArgs);
    }

    @Override
    public String toString() {
        String base = this.receiver != null ? this.receiver.toString() : this.qualifier;
        return base + "." + this.method + this.args;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Invocation that = (Invocation) o;
        return Objects.equals(receiver, that.receiver)
            && Objects.equals(qualifier, that.qualifier)
            && Objects.equals(method, that.method)
            && Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiver, qualifier, method, args);
    }
}
