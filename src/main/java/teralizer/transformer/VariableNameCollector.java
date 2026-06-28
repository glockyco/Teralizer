package teralizer.transformer;

import teralizer.domain.ModelVisitor;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;
import teralizer.domain.VariableString;

import java.util.Set;

/**
 * Collects the names of every {@code Variable*} node in a {@link teralizer.domain.Model}
 * tree. A read-only observer over the no-op {@link ModelVisitor} hooks; used to decide
 * whether a non-generalizable clause constrains any generated parameter.
 */
public final class VariableNameCollector extends ModelVisitor {
    private final Set<String> names;

    public VariableNameCollector(Set<String> names) {
        this.names = names;
    }

    @Override
    public void preVisit(VariableInteger variable) {
        this.names.add(variable.name);
    }

    @Override
    public void preVisit(VariableReal variable) {
        this.names.add(variable.name);
    }

    @Override
    public void preVisit(VariableString variable) {
        this.names.add(variable.name);
    }
}
