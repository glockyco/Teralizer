package teralizer.domain;

import java.util.Objects;

public final class Constant implements Expression {
    public final Object value;
    public final TypeDomain domain;

    public Constant(Object value, TypeDomain domain) {
        this.value = value;
        this.domain = domain;
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public <T> T fold(ModelFolder<T> folder) {
        return folder.fold(this);
    }

    @Override
    public String toString() {
        return String.valueOf(this.value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Constant constant = (Constant) o;
        return Objects.equals(value, constant.value) && domain == constant.domain;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, domain);
    }
}
