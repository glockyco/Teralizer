package teralizer.processing;

public enum ProcessingStage {
    CLEANUP(null),

    PROJECT_DOWNLOAD(1),
    PROJECT_SETUP(2),

    PROJECT_BUILDING_ORIGINAL(3),
    TEST_EXECUTION_ORIGINAL(4),

    ADD_JQWIK_DEPENDENCY(5),
    PROJECT_BUILDING_JQWIK(6),

    TEST_DETECTION(7),
    TEST_FILTERING(8),

    JPF_INSTRUMENTATION(9),
    PROJECT_BUILDING_INSTRUMENTED(10),
    JPF_EXECUTION(11),

    TEST_GENERALIZATION(12),
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
