package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;
import teralizer.domain.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputGenerationPlanner {
    public InputGenerationPlan plan(List<MethodParameter> parameters, Model inputModel) {
        Map<String, String> parameterTypes = new HashMap<>();
        for (MethodParameter parameter : parameters) {
            parameterTypes.put(parameter.getName(), parameter.getType());
        }

        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes);
        PlanningContext context = new PlanningContext(parameters, clauses);
        List<ParameterGenerationPlan> parameterPlans = new ArrayList<>();
        for (MethodParameter parameter : parameters) {
            TypeDomain domain = TypeDomain.from(parameter.getType());
            parameterPlans.add(new ParameterGenerationPlan(
                parameter,
                domain,
                new RawJavaRecipe(""),
                Collections.emptySet()
            ));
        }
        return new InputGenerationPlan(parameterPlans, context.getClauses(), Collections.emptySet());
    }
}
