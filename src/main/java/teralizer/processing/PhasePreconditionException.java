package teralizer.processing;

public class PhasePreconditionException extends RuntimeException {

    public PhasePreconditionException(String message) {
        super(message);
    }

    public PhasePreconditionException(String message, Throwable cause) {
        super(message, cause);
    }
}
