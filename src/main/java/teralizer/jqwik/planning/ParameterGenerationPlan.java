package teralizer.jqwik.planning;

import teralizer.domain.MethodParameter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ParameterGenerationPlan {
    private final MethodParameter parameter;
    private final TypeDomain domain;
    private final GenerationRecipe recipe;
    private final String originalValue;
    private final Set<Integer> consumedClauseIds;

    public ParameterGenerationPlan(MethodParameter parameter, TypeDomain domain, GenerationRecipe recipe, String originalValue, Set<Integer> consumedClauseIds) {
        this.parameter = parameter;
        this.domain = domain;
        this.recipe = recipe;
        this.originalValue = originalValue;
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

    /**
     * The original concrete argument rendered as a cast Java expression (e.g. {@code (int) (7)}),
     * or {@code null} when this parameter had no recorded original input. Consumed at the tuple
     * level to seed the exact original combination before generalization.
     */
    public String getOriginalValue() {
        return this.originalValue;
    }

    public Set<Integer> getConsumedClauseIds() {
        return Collections.unmodifiableSet(this.consumedClauseIds);
    }
}
