package teralizer.jqwik.planning;

import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        // Aggregate per-parameter consumed clause ids to plan level for generation-coverage
        // telemetry (which clauses each recipe enforced by construction). The residual filter
        // stays unconditional — the factory uses getFullPredicate()/hasClauses() (all clauses),
        // not getResidualPredicate()/hasResidualClauses(). Residual-only filtering is a non-goal
        // (no outcome change, only added unsoundness surface).
        Set<Integer> planConsumedClauseIds = new LinkedHashSet<>();
        for (ParameterGenerationPlan parameterPlan : parameterPlans) {
            planConsumedClauseIds.addAll(parameterPlan.getConsumedClauseIds());
        }
        return new InputGenerationPlan(parameterPlans, context.getClauses(), planConsumedClauseIds);
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
