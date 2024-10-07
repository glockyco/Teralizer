package teralizer.processing;

public enum ProcessingStage {
    CLEANUP(null),

    PROJECT_DOWNLOAD(1),
    PROJECT_SETUP(2),
    PROJECT_BUILDING_ORIGINAL(3),
    TEST_EXECUTION_ORIGINAL(4),

    ADD_DEPENDENCIES(5),
    PROJECT_BUILDING_WITH_DEPENDENCIES(6),
    TEST_EXECUTION_WITH_DEPENDENCIES(7),

    TEST_DETECTION(8),
    TEST_FILTERING(9),
    TEST_DATA_COLLECTION_ORIGINAL(10),

    JPF_INSTRUMENTATION(11),
    PROJECT_BUILDING_INSTRUMENTED(12),
    JPF_EXECUTION(13),

    TEST_GENERALIZATION(14),
    PROJECT_BUILDING_GENERALIZED(15),
    TEST_EXECUTION_GENERALIZED(16),
    TEST_DATA_COLLECTION_GENERALIZED(17);

    private final Integer step;

    ProcessingStage(Integer step) {
        this.step = step;
    }

    public Integer getStep() {
        return this.step;
    }
}
