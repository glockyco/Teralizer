package teralizer.processing;

public enum MutationStatus {
    // MutationStatus values represent the corresponding values of PIT's DetectionStatus class:
    // https://github.com/hcoles/pitest/blob/d23455e34dd6660d7df617fdeb55ab31b636870d/pitest/src/main/java/org/pitest/mutationtest/DetectionStatus.java
    KILLED,
    SURVIVED,
    TIMED_OUT,
    NON_VIABLE,
    MEMORY_ERROR,
    RUN_ERROR,
    NO_COVERAGE,
}
