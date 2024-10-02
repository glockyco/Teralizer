package teralizer.processing;

import java.nio.file.Path;
import java.util.List;

public class ConsoleCommandException extends Exception {

    private final int exitCode;
    private final Path outputPath;
    private final Path errorPath;

    public ConsoleCommandException(List<String> command, int exitCode, Path outputPath, Path errorPath) {
        super(buildErrorMessage(command, exitCode, outputPath, errorPath));
        this.exitCode = exitCode;
        this.outputPath = outputPath;
        this.errorPath = errorPath;
    }

    public int getExitCode() {
        return this.exitCode;
    }

    public Path getOutputPath() {
        return this.outputPath;
    }

    public Path getErrorPath() {
        return this.errorPath;
    }

    private static String buildErrorMessage(List<String> command, int exitCode, Path outputPath, Path errorPath) {
        String errorMessage = "Command '" + String.join(" ", command) + "' terminated with exit code '" + exitCode + "'.\n";
        errorMessage += "Output: " + outputPath + "\n";
        errorMessage += "Error: " + errorPath;
        return errorMessage;
    }
}
