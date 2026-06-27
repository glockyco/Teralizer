package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanningContext {
    private final List<MethodParameter> parameters;
    private final List<ConstraintClause> clauses;
    private final Map<String, Integer> parameterIndexes;
    private final Map<String, String> parameterTypes;

    public PlanningContext(List<MethodParameter> parameters, List<ConstraintClause> clauses) {
        this.parameters = parameters;
        this.clauses = clauses;
        this.parameterIndexes = new HashMap<>();
        this.parameterTypes = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            this.parameterIndexes.put(parameter.getName(), i);
            this.parameterTypes.put(parameter.getName(), parameter.getType());
        }
    }

    public List<MethodParameter> getParameters() {
        return this.parameters;
    }

    public List<ConstraintClause> getClauses() {
        return this.clauses;
    }

    public Map<String, Integer> getParameterIndexes() {
        return Collections.unmodifiableMap(this.parameterIndexes);
    }

    public Map<String, String> getParameterTypes() {
        return Collections.unmodifiableMap(this.parameterTypes);
    }
}
