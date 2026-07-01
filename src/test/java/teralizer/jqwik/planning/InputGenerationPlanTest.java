package teralizer.jqwik.planning;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.jqwik.api.Example;
import org.junit.Assert;

public class InputGenerationPlanTest {

    private static ConstraintClause clause(int id, String java) {
        return new ConstraintClause(id, null, java);
    }

    @Example
    void fullPredicateJoinsAllClausesRegardlessOfConsumed() {
        ConstraintClause c0 = clause(0, "(_p_.x > 0)");
        ConstraintClause c1 = clause(1, "(_p_.y < 10)");
        Set<Integer> consumed = new LinkedHashSet<>(Collections.singleton(0));

        InputGenerationPlan plan = new InputGenerationPlan(
            Collections.emptyList(),
            Arrays.asList(c0, c1),
            consumed
        );

        Assert.assertEquals("(_p_.x > 0) && (_p_.y < 10)", plan.getFullPredicate());
    }

    @Example
    void fullPredicateIsTrueWhenNoClausesExist() {
        InputGenerationPlan plan = new InputGenerationPlan(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet()
        );

        Assert.assertEquals("true", plan.getFullPredicate());
    }

    @Example
    void fullPredicateIncludesConsumedClauses() {
        ConstraintClause c0 = clause(0, "(_p_.x > 0)");
        Set<Integer> consumed = new LinkedHashSet<>(Collections.singleton(0));

        InputGenerationPlan plan = new InputGenerationPlan(
            Collections.emptyList(),
            Collections.singletonList(c0),
            consumed
        );

        Assert.assertEquals("(_p_.x > 0)", plan.getFullPredicate());
        Assert.assertTrue("residual is empty (clause consumed)", plan.getResidualClauses().isEmpty());
        Assert.assertEquals("true", plan.getResidualPredicate());
    }

    @Example
    void hasClausesTrueWhenAnyClausesExist() {
        ConstraintClause c0 = clause(0, "(_p_.x > 0)");
        Set<Integer> consumed = new LinkedHashSet<>(Collections.singleton(0));

        InputGenerationPlan plan = new InputGenerationPlan(
            Collections.emptyList(),
            Collections.singletonList(c0),
            consumed
        );

        Assert.assertTrue(plan.hasClauses());
    }

    @Example
    void hasClausesFalseWhenNoClausesExist() {
        InputGenerationPlan plan = new InputGenerationPlan(
            Collections.emptyList(),
            Collections.emptyList(),
            Collections.emptySet()
        );

        Assert.assertFalse(plan.hasClauses());
    }
}
