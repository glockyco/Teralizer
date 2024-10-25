package teralizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConsoleCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommand.class);

    private final ProcessingStage stage;
    private final GeneralizationVariant variant;
    private final Path commandOutputsPath;

    private int executionCount = 0;

    public ConsoleCommand(ProcessingStage stage, GeneralizationVariant variant, Path projectDataPath) {
        this.stage = stage;
        this.variant = variant;
        this.commandOutputsPath = projectDataPath.resolve("command-outputs");
    }

    public void execute(List<String> command) throws IOException, InterruptedException, ConsoleCommandException {
        this.execute(null, command);
    }

    public void execute(Path projectRootPath, List<String> command) throws IOException, InterruptedException, ConsoleCommandException {
        this.executionCount++;
        String stageName = this.stage.getStep() + "-" + this.stage;
        String variantName = this.variant == null ? "" : ("." + this.variant.getId() + "-" + this.variant);
        String executionName = "." + this.executionCount;
        String baseName = stageName + variantName + executionName;
        Path outputPath = this.commandOutputsPath.resolve(baseName + ".output.txt");
        Path errorPath = this.commandOutputsPath.resolve(baseName + ".error.txt");
        outputPath.toFile().getParentFile().mkdirs();

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectRootPath == null ? null : projectRootPath.toFile());
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputPath.toFile()));
        processBuilder.redirectError(ProcessBuilder.Redirect.to(errorPath.toFile()));
        Process process = processBuilder.start();

        Thread shutdownHook = new Thread(() -> {
            LOGGER.atDebug().log("Terminating command '" + String.join(" ", command) + "' due to shutdown.");
            process.destroy();
        });

        int exitCode;
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            exitCode = process.waitFor();
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }

        if (exitCode != 0) {
            throw new ConsoleCommandException(command, exitCode, outputPath, errorPath);
        }
    }
}
