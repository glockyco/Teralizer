package teralizer.domain;

import java.util.Objects;

public class ArrayExpression implements Expression {
    public String name;
    public String elementType;

    public ArrayExpression(String name, String elementType) {
        this.name = name;
        this.elementType = elementType;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayExpression that = (ArrayExpression) o;
        return Objects.equals(this.name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.name);
    }
}
