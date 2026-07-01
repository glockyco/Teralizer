package teralizer.transformer;

import java.util.Set;
import teralizer.domain.ModelVisitor;
import teralizer.domain.Variable;

/**
 * Collects the names of every {@code Variable} node in a {@link teralizer.domain.Model}
 * tree. A read-only observer over the no-op {@link ModelVisitor} hooks; used to decide
 * whether a non-generalizable clause constrains any generated parameter.
 */
public final class VariableNameCollector extends ModelVisitor {
    private final Set<String> names;

    public VariableNameCollector(Set<String> names) {
        this.names = names;
    }

    @Override
    public void preVisit(Variable variable) {
        this.names.add(variable.name);
    }
}
