package teralizer.domain;

import java.util.Objects;

public class ExceptionModel implements Expression {
    public String name;
    public String message;

    public ExceptionModel(String name, String message) {
        this.name = name;
        this.message = message;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    public String toString() {
        return this.name + ": " + this.message;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || this.getClass() != object.getClass()) return false;
        ExceptionModel exception = (ExceptionModel) object;
        return Objects.equals(this.name, exception.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.name);
    }
}
