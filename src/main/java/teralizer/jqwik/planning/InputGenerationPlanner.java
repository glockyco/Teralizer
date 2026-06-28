package teralizer.jqwik.planning;

import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputGenerationPlanner {
    private final List<DomainPlanner> domainPlanners = DomainPlanners.REGISTERED;

    public InputGenerationPlan plan(List<MethodParameter> parameters, Model inputModel) {
        return this.plan(parameters, Collections.emptyMap(), inputModel);
    }

    public InputGenerationPlan plan(List<MethodParameter> parameters, Map<String, MethodArgument> arguments, Model inputModel) {
        Map<String, String> parameterTypes = new HashMap<>();
        for (MethodParameter parameter : parameters) {
            parameterTypes.put(parameter.getName(), parameter.getType());
        }

        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes, parameterTypes.keySet());
        PlanningContext context = new PlanningContext(parameters, clauses, arguments);
        List<ParameterGenerationPlan> parameterPlans = new ArrayList<>();
        for (MethodParameter parameter : parameters) {
            TypeDomain domain = TypeDomain.from(parameter.getType());
            parameterPlans.add(this.planParameter(parameter, domain, context));
        }
        // Per-parameter consumed ids are reported on each ParameterGenerationPlan, but the
        // plan-level set is intentionally empty: emitting a residual-only filter (dropping checks
        // for clauses provably enforced by construction) requires a by-construction soundness
        // proof that a clause is implied by the recipe — a prior attempt produced unsound
        // generated tests, so the full input filter is retained as the sound fallback.
        // @TODO Prove recipe-implied clauses and emit a residual-only filter.
        return new InputGenerationPlan(parameterPlans, context.getClauses(), Collections.emptySet());
    }

    private ParameterGenerationPlan planParameter(MethodParameter parameter, TypeDomain domain, PlanningContext context) {
        for (DomainPlanner domainPlanner : this.domainPlanners) {
            if (domainPlanner.supports(domain)) {
                return domainPlanner.plan(parameter, context);
            }
        }
        return new ParameterGenerationPlan(parameter, domain, new RawJavaRecipe(defaultRecipe(parameter, domain)), Collections.emptySet());
    }

    private static String defaultRecipe(MethodParameter parameter, TypeDomain domain) {
        switch (domain) {
            case BOOLEAN:
                return "return net.jqwik.api.Arbitraries.of(true, false)";
            case STRING:
                return "return net.jqwik.api.Arbitraries.strings()";
            case ARRAY:
            case OBJECT:
            default:
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
        }
    }
}
