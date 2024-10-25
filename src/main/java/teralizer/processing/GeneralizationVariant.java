package teralizer.processing;

public enum GeneralizationVariant {

    NAIVE(1);

    private final Integer id;

    GeneralizationVariant(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return this.id;
    }
}
