package teralizer.processing;

public enum GeneralizationAlgorithm {

    BASELINE(0),
    NAIVE(1),
    IMPROVED(2);

    private final Integer id;

    GeneralizationAlgorithm(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return this.id;
    }
}
