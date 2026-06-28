package teralizer.domain;

public interface Model {
    void accept(ModelVisitor visitor);

    /** Total bottom-up fold dispatch; see {@link ModelFolder}. */
    <T> T fold(ModelFolder<T> folder);
}
