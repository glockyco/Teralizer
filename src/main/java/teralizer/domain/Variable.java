package teralizer.domain;

import java.util.Objects;

public final class Variable implements Expression {
    public final String name;
    public final TypeDomain domain;

    public Variable(String name, TypeDomain domain) {
        this.name = name;
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
        return this.name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Variable variable = (Variable) o;
        return Objects.equals(name, variable.name) && domain == variable.domain;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, domain);
    }
}
