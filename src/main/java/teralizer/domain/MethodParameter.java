package teralizer.domain;

public class MethodParameter {
    private final String type;
    private final String name;

    public MethodParameter(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return this.type;
    }

    public String getName() {
        return this.name;
    }
}
