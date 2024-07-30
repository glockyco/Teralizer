package teralizer.processing;

public enum ProcessingStage {
    CLEANUP(null),

    PROJECT_SETUP(1),

    PROJECT_BUILDING_ORIGINAL(2),
    TEST_EXECUTION_ORIGINAL(3),
    TEST_DETECTION(4),

    JPF_INSTRUMENTATION(5),
    PROJECT_BUILDING_INSTRUMENTED(6),
    JPF_EXECUTION(7),

    TEST_GENERALIZATION(8),
    PROJECT_BUILDING_GENERALIZED(9),
    TEST_EXECUTION_GENERALIZED(10);

    private final Integer step;

    ProcessingStage(Integer step) {
        this.step = step;
    }

    public Integer getStep() {
        return this.step;
    }

    // @TODO: Create cleanup tasks to remove created files and database entries.
    //   1. Full project cleanup.
    //   2. Single test cleanup.
    //   3. Single step cleanup.
    //   3.5. Single tool cleanup (of the generalization step).
    // @TODO: How to handle cleanup of files that are created in the same locations across different runs?
    //   Of course the most obvious solution is to create different files for different runs.
    //   However, this makes it difficult to measure the runtime of the generalized test suite,
    //   because we would have many duplicated outputs that just increase the runtime without adding anything useful.
    //   -
    //   A better approach might be to keep all files that might still be associated with some non-cleaned-up run.
    //   We could even check whether such a non-cleaned-up run actually exists in the DB, but it seems like this
    //   isn't really worth the effort for something (i.e., partial cleanup) that will only ever see use during
    //   development, but won't see any use during the actual evaluation.
}
