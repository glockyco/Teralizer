package teralizer.domain;

public class MethodParameter {
    private String type;
    private String name;

    public MethodParameter(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
