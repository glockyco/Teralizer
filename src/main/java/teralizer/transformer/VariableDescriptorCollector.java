package teralizer.transformer;

import java.util.LinkedHashMap;
import java.util.Map;
import teralizer.domain.Model;
import teralizer.domain.ModelVisitor;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;

public final class VariableDescriptorCollector extends ModelVisitor {
    private final Map<String, TypeDomain> variables = new LinkedHashMap<>();

    public static Map<String, TypeDomain> collect(Model... models) {
        VariableDescriptorCollector collector = new VariableDescriptorCollector();
        for (Model model : models) {
            if (model != null) {
                model.accept(collector);
            }
        }
        return new LinkedHashMap<>(collector.variables);
    }

    @Override
    public void preVisit(Variable variable) {
        this.variables.putIfAbsent(variable.name, variable.domain);
    }
}
