package teralizer.jqwik.planning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InputGenerationPlan {
    private final List<ParameterGenerationPlan> parameterPlans;
    private final List<ConstraintClause> clauses;
    private final Set<Integer> consumedClauseIds;
    private final Set<Integer> residualClauseIds;

    public InputGenerationPlan(List<ParameterGenerationPlan> parameterPlans, List<ConstraintClause> clauses, Set<Integer> consumedClauseIds) {
        this.parameterPlans = new ArrayList<>(parameterPlans);
        this.clauses = new ArrayList<>(clauses);
        this.consumedClauseIds = new LinkedHashSet<>(consumedClauseIds);
        this.residualClauseIds = clauses.stream()
            .map(ConstraintClause::getId)
            .filter(id -> !this.consumedClauseIds.contains(id))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public List<ParameterGenerationPlan> getParameterPlans() {
        return Collections.unmodifiableList(this.parameterPlans);
    }

    public List<ConstraintClause> getClauses() {
        return Collections.unmodifiableList(this.clauses);
    }

    public Set<Integer> getConsumedClauseIds() {
        return Collections.unmodifiableSet(this.consumedClauseIds);
    }

    public List<ConstraintClause> getResidualClauses() {
        return this.clauses.stream()
            .filter(clause -> this.residualClauseIds.contains(clause.getId()))
            .collect(Collectors.toList());
    }

    public Set<Integer> getResidualClauseIds() {
        return Collections.unmodifiableSet(this.residualClauseIds);
    }

    public int getTotalConstraintCount() {
        return this.clauses.size();
    }

    public int getUsedConstraintCount() {
        return this.consumedClauseIds.size();
    }

    public boolean hasConsumedClauses() {
        return !this.consumedClauseIds.isEmpty();
    }

    public boolean hasResidualClauses() {
        return !this.residualClauseIds.isEmpty();
    }

    public String getResidualPredicate() {
        List<ConstraintClause> residual = this.getResidualClauses();
        if (residual.isEmpty()) {
            return "true";
        }
        return residual.stream()
            .map(ConstraintClause::getJavaExpression)
            .collect(Collectors.joining(" && "));
    }
}
