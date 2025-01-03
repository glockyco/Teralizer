package teralizer.domain;

public class MethodArgument {
    private final String type;
    private final String value;

    public MethodArgument(String type, String value) {
        this.type = type;
        this.value = value;
    }

    public String getType() {
        return this.type;
    }

    public String getValue() {
        return this.value;
    }
}
