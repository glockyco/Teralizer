package teralizer.domain;

import java.util.Objects;

public class Not implements Expression {
    public final Expression operand;

    public Not(Expression operand) {
        this.operand = operand;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        this.operand.accept(visitor);
        visitor.postVisit(this);
    }

    @Override
    public <T> T fold(ModelFolder<T> folder) {
        return folder.fold(this, this.operand.fold(folder));
    }

    @Override
    public String toString() {
        return "!(" + this.operand + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Not not = (Not) o;
        return Objects.equals(operand, not.operand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(operand);
    }
}
