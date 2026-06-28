package teralizer.jqwik.planning;

import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputGenerationPlanner {
    private final List<DomainPlanner> domainPlanners = Arrays.asList(new NumericDomainPlanner());

    public InputGenerationPlan plan(List<MethodParameter> parameters, Model inputModel) {
        return this.plan(parameters, Collections.emptyMap(), inputModel);
    }

    public InputGenerationPlan plan(List<MethodParameter> parameters, Map<String, MethodArgument> arguments, Model inputModel) {
        Map<String, String> parameterTypes = new HashMap<>();
        for (MethodParameter parameter : parameters) {
            parameterTypes.put(parameter.getName(), parameter.getType());
        }

        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes);
        PlanningContext context = new PlanningContext(parameters, clauses, arguments);
        List<ParameterGenerationPlan> parameterPlans = new ArrayList<>();
        for (MethodParameter parameter : parameters) {
            TypeDomain domain = TypeDomain.from(parameter.getType());
            parameterPlans.add(this.planParameter(parameter, domain, context));
        }
        // The plan-level consumed set is intentionally empty: the generated supplier keeps the FULL
        // input filter as the sound fallback. Per-parameter consumed ids are reported on each
        // ParameterGenerationPlan, but using them to emit a residual-only filter (dropping checks for
        // clauses provably enforced by construction) is deferred to C-3 in
        // docs/plans/2026-06-28-pipeline-improvements.md. Do not aggregate consumed ids here without
        // that by-construction soundness proof — a prior attempt produced unsound generated tests.
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
