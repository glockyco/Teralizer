package teralizer.verification.exceptionmessage;

public class ExceptionMessageCut {
    public int throwWithConcatenatedMessage(int suffix) {
        if (suffix >= 0) {
            throw new MissingLabelException("boom " + suffix);
        }
        return suffix;
    }

    public static final class MissingLabelException extends RuntimeException {
        public MissingLabelException(String message) {
            super(message);
        }
    }
}
