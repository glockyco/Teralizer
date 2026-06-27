package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ParameterGenerationPlan {
    private final MethodParameter parameter;
    private final TypeDomain domain;
    private final GenerationRecipe recipe;
    private final Set<Integer> consumedClauseIds;

    public ParameterGenerationPlan(MethodParameter parameter, TypeDomain domain, GenerationRecipe recipe, Set<Integer> consumedClauseIds) {
        this.parameter = parameter;
        this.domain = domain;
        this.recipe = recipe;
        this.consumedClauseIds = new LinkedHashSet<>(consumedClauseIds);
    }

    public MethodParameter getParameter() {
        return this.parameter;
    }

    public TypeDomain getDomain() {
        return this.domain;
    }

    public GenerationRecipe getRecipe() {
        return this.recipe;
    }

    public Set<Integer> getConsumedClauseIds() {
        return Collections.unmodifiableSet(this.consumedClauseIds);
    }
}
