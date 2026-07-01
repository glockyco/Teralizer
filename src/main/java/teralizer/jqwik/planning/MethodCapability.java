package teralizer.jqwik.planning;

public final class MethodCapability {
    public final String method;
    public final String staticQualifier;
    public final boolean inputGeneratable;
    public final boolean outputRenderable;

    MethodCapability(String method, String staticQualifier, boolean inputGeneratable, boolean outputRenderable) {
        this.method = method;
        this.staticQualifier = staticQualifier;
        this.inputGeneratable = inputGeneratable;
        this.outputRenderable = outputRenderable;
    }
}
