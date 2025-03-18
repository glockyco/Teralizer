package teralizer.processing;

public enum GeneralizationVariant {

    BASELINE(0),
    NAIVE(1),
    IMPROVED(2),
    COMBINED(3);

    private final Integer id;

    GeneralizationVariant(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return this.id;
    }
}
