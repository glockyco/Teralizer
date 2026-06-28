package teralizer.domain;

import java.util.Objects;

public class ArrayElementExpression implements Expression {
    public String arrayName;
    public String elementType;
    public Expression elementSelector;

    public ArrayElementExpression(String name, String elementType, Expression elementSelector) {
        this.arrayName = name;
        this.elementType = elementType;
        this.elementSelector = elementSelector;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        this.elementSelector.accept(visitor);
        visitor.postVisit(this);
    }

    @Override
    public <T> T fold(ModelFolder<T> folder) {
        T selector = this.elementSelector.fold(folder);
        return folder.fold(this, selector);
    }

    @Override
    public String toString() {
        return this.arrayName + "[" + this.elementSelector + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;
        ArrayElementExpression that = (ArrayElementExpression) o;
        return Objects.equals(this.arrayName, that.arrayName) && Objects.equals(this.elementSelector, that.elementSelector);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.arrayName, this.elementSelector);
    }
}
