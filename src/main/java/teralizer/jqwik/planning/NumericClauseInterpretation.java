package teralizer.jqwik.planning;

import teralizer.jqwik.VariableConstraints;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The numeric constraints derived for one parameter from the input clauses, plus the ids of the clauses that contributed. */
final class NumericClauseInterpretation {
    private final VariableConstraints constraints;
    private final Set<Integer> consumedClauseIds;

    NumericClauseInterpretation(VariableConstraints constraints, Set<Integer> consumedClauseIds) {
        this.constraints = constraints;
        this.consumedClauseIds = new LinkedHashSet<>(consumedClauseIds);
    }

    VariableConstraints getConstraints() {
        return this.constraints;
    }

    Set<Integer> getConsumedClauseIds() {
        return Collections.unmodifiableSet(this.consumedClauseIds);
    }
}
