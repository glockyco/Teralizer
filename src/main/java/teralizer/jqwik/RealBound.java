package teralizer.jqwik;

public class RealBound {

    private final String value;
    private final boolean isIncluded;

    public RealBound(String value, boolean isIncluded) {
        this.value = value;
        this.isIncluded = isIncluded;
    }

    public String getValue() {
        return this.value;
    }

    public boolean getIsIncluded() {
        return this.isIncluded;
    }
}
