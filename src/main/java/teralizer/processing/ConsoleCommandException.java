package teralizer.processing;

public class ConsoleCommandException extends Exception {

    private final int exitCode;
    private final String output;
    private final String error;

    public ConsoleCommandException(int exitCode, String output, String error) {
        super(buildErrorMessage(exitCode, output, error));
        this.exitCode = exitCode;
        this.output = output;
        this.error = error;
    }

    public int getExitCode() {
        return this.exitCode;
    }

    public String getOutput() {
        return this.output;
    }

    public String getError() {
        return this.error;
    }

    private static String buildErrorMessage(int exitCode, String output, String error) {
        String errorMessage = "Output:\n\n" + output;
        errorMessage += "\n\nError: terminated with exit code " + exitCode + ".";
        errorMessage += error.isEmpty() ? "" : "\n\n" + error;
        return errorMessage;
    }
}
