package teralizer.processing.task;

import teralizer.processing.ProcessingStage;
import teralizer.util.Configuration;

/**
 * Fixed per-stage timeout budget (seconds) for the ConsoleCommand-driven stages. Original and
 * initial work gets the reference budget; the generalized variant gets its own larger budget. The
 * value is a config read, not an arithmetic of run inputs (no generalization count, tries, or mutant
 * count), so a stage that blows its fixed budget is an honest exclusion. JPF keeps its own
 * per-assertion budget in TestGeneralizationListener and does not route through here.
 */
public final class TimeoutBudget {

    private TimeoutBudget() {
    }

    static int forStage(ProcessingStage stage) {
        switch (stage) {
            case EXECUTE_TESTS_ORIGINAL:
            case EXECUTE_TESTS_INITIAL:
                return Configuration.getJunitTimeoutOriginalInitial();
            case EXECUTE_TESTS_GENERALIZED:
                return Configuration.getJunitTimeoutGeneralized();
            case COLLECT_PIT_DATA_ORIGINAL:
            case COLLECT_PIT_DATA_INITIAL:
                return Configuration.getPitestTimeoutOriginalInitial();
            case COLLECT_PIT_DATA_GENERALIZED:
                return Configuration.getPitestTimeoutGeneralized();
            default:
                throw new IllegalArgumentException("No timeout budget for stage: " + stage);
        }
    }
}
