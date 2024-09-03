package teralizer.processing;

public enum ProcessingStage {
    CLEANUP(null),

    PROJECT_SETUP(1),

    PROJECT_BUILDING_ORIGINAL(2),
    TEST_EXECUTION_ORIGINAL(3),

    ADD_JQWIK_DEPENDENCY(4),
    PROJECT_BUILDING_JQWIK(5),

    TEST_DETECTION(6),
    TEST_FILTERING(7),

    JPF_INSTRUMENTATION(8),
    PROJECT_BUILDING_INSTRUMENTED(9),
    JPF_EXECUTION(10),

    TEST_GENERALIZATION(11),
    PROJECT_BUILDING_GENERALIZED(13),
    TEST_EXECUTION_GENERALIZED(14);

    private final Integer step;

    ProcessingStage(Integer step) {
        this.step = step;
    }

    public Integer getStep() {
        return this.step;
    }
}
