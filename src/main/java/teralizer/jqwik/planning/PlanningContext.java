package teralizer.jqwik.planning;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import teralizer.domain.MethodParameter;
import teralizer.domain.Value;

public class PlanningContext {
    private final List<MethodParameter> parameters;
    private final List<ConstraintClause> clauses;
    private final Map<String, Integer> parameterIndexes;
    private final Map<String, String> parameterTypes;
    private final Map<String, Value> arguments;
    private final Map<String, NumericClauseInterpretation> interpretations;

    public PlanningContext(List<MethodParameter> parameters, List<ConstraintClause> clauses) {
        this(parameters, clauses, Collections.emptyMap());
    }

    public PlanningContext(
        List<MethodParameter> parameters,
        List<ConstraintClause> clauses,
        Map<String, Value> arguments
    ) {
        this.parameters = parameters;
        this.clauses = clauses;
        this.arguments = arguments;
        this.parameterIndexes = new HashMap<>();
        this.parameterTypes = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            this.parameterIndexes.put(parameter.getName(), i);
            this.parameterTypes.put(parameter.getName(), parameter.getType());
        }
        this.interpretations = NumericClauseInterpreter.interpret(clauses, parameters);
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

    public Map<String, Value> getArguments() {
        return Collections.unmodifiableMap(this.arguments);
    }

    public NumericClauseInterpretation getInterpretation(String name) {
        NumericClauseInterpretation interpretation = this.interpretations.get(name);
        return interpretation != null ? interpretation : NumericClauseInterpretation.empty();
    }
}
