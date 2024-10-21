package teralizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.ProcessingStage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConsoleCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommand.class);

    private final ProcessingStage stage;
    private final Path commandOutputsPath;

    private int executionCount = 0;

    public ConsoleCommand(ProcessingStage stage, Path projectDataPath) {
        this.stage = stage;
        this.commandOutputsPath = projectDataPath.resolve("command-outputs");
    }

    public void execute(List<String> command) throws IOException, InterruptedException, ConsoleCommandException {
        this.execute(null, command);
    }

    public void execute(Path projectRootPath, List<String> command) throws IOException, InterruptedException, ConsoleCommandException {
        this.executionCount++;
        Path outputPath = this.commandOutputsPath.resolve(this.stage.getStep() + "-" + this.stage + "-" + this.executionCount + ".output.txt");
        Path errorPath = this.commandOutputsPath.resolve(this.stage.getStep() + "-" + this.stage + "-" + this.executionCount + ".error.txt");
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
