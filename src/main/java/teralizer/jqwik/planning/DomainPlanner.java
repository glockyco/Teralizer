package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;

public interface DomainPlanner {
    boolean supports(TypeDomain domain);

    ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context);
}
