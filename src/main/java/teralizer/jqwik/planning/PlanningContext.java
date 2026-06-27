package teralizer.jqwik.planning;

import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.VariableConstraints;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlanningContext {
    private final List<MethodParameter> parameters;
    private final List<ConstraintClause> clauses;
    private final Map<String, Integer> parameterIndexes;
    private final Map<String, String> parameterTypes;
    private final Map<String, MethodArgument> arguments;
    private final Map<String, VariableConstraints> constraints;

    public PlanningContext(List<MethodParameter> parameters, List<ConstraintClause> clauses) {
        this(parameters, clauses, Collections.emptyMap(), Collections.emptyMap());
    }

    public PlanningContext(
        List<MethodParameter> parameters,
        List<ConstraintClause> clauses,
        Map<String, MethodArgument> arguments,
        Map<String, VariableConstraints> constraints
    ) {
        this.parameters = parameters;
        this.clauses = clauses;
        this.arguments = arguments;
        this.constraints = constraints;
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

    public Map<String, MethodArgument> getArguments() {
        return Collections.unmodifiableMap(this.arguments);
    }

    public Map<String, VariableConstraints> getConstraints() {
        return Collections.unmodifiableMap(this.constraints);
    }
}
