package teralizer.jqwik.planning;

import teralizer.domain.TypeDomain;

public final class MethodCapability {
    public enum InputConstraintKind {
        NONE,
        EQUALITY,
        PREFIX,
        SUFFIX,
        CONTAINS,
        EMPTY,
        PARSE_INTEGER,
        PARSE_LONG,
        PARSE_FLOAT,
        PARSE_DOUBLE
    }

    public final String method;
    public final String staticQualifier;
    public final TypeDomain receiverDomain;
    public final TypeDomain returnDomain;
    public final boolean inputGeneratable;
    public final boolean outputRenderable;
    public final InputConstraintKind inputConstraintKind;

    MethodCapability(
        String method,
        String staticQualifier,
        TypeDomain receiverDomain,
        TypeDomain returnDomain,
        boolean inputGeneratable,
        boolean outputRenderable,
        InputConstraintKind inputConstraintKind) {
        this.method = method;
        this.staticQualifier = staticQualifier;
        this.receiverDomain = receiverDomain;
        this.returnDomain = returnDomain;
        this.inputGeneratable = inputGeneratable;
        this.outputRenderable = outputRenderable;
        this.inputConstraintKind = inputConstraintKind;
    }
}
