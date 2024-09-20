package teralizer.processing;

public enum ProcessingStage {
    CLEANUP(null),

    PROJECT_DOWNLOAD(1),
    PROJECT_SETUP(2),
    PROJECT_BUILDING_ORIGINAL(3),
    TEST_EXECUTION_ORIGINAL(4),

    TEST_DETECTION(5),
    TEST_FILTERING(6),
    TEST_DATA_COLLECTION_ORIGINAL(7),

    JPF_INSTRUMENTATION(8),
    PROJECT_BUILDING_INSTRUMENTED(9),
    JPF_EXECUTION(10),

    ADD_JQWIK_DEPENDENCY(11),
    PROJECT_BUILDING_JQWIK(12),

    TEST_GENERALIZATION(13),
    PROJECT_BUILDING_GENERALIZED(14),
    TEST_EXECUTION_GENERALIZED(15),
    TEST_DATA_COLLECTION_GENERALIZED(16);

    private final Integer step;

    ProcessingStage(Integer step) {
        this.step = step;
    }

    public Integer getStep() {
        return this.step;
    }
}
