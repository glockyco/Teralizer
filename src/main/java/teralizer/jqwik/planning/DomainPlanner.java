package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;

public interface DomainPlanner {
    boolean supports(TypeDomain domain);

    ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context);

    /**
     * Whether this type may be used as the symbolic output oracle — a query distinct from input
     * generation, declared explicitly by every planner (a type can be input-generatable yet not
     * capturable as a return, e.g. String until symbolic return capture lands).
     */
    boolean supportsReturn(TypeDomain domain);
}
