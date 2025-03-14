package teralizer.util;

import java.io.IOException;
import java.nio.file.Path;

public class ConsoleCommandResult {

    private final Path outputPath;
    private final Path errorPath;

    public ConsoleCommandResult(Path outputPath, Path errorPath) {
        this.outputPath = outputPath;
        this.errorPath = errorPath;
    }

    public Path getOutputPath() throws IOException {
        return this.outputPath;
    }

    public Path getErrorPath() throws IOException {
        return this.errorPath;
    }
}
