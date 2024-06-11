package teralizer.domain;

public interface Model {
    void accept(ModelVisitor visitor);
}
