package teralizer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ConsoleCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommand.class);

    private final ProcessingStage stage;
    private final GeneralizationVariant variant;
    private final Path commandDataPath;

    private int executionCount = 0;

    public ConsoleCommand(ProcessingStage stage, GeneralizationVariant variant, int projectId, Path projectDataPath) {
        this.stage = stage;
        this.variant = variant;
        this.commandDataPath = projectDataPath.resolve("project-id-" + projectId + "/command-data");
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
        Path commandPath = this.commandDataPath.resolve(baseName + ".command.txt");
        Path outputPath = this.commandDataPath.resolve(baseName + ".output.txt");
        Path errorPath = this.commandDataPath.resolve(baseName + ".error.txt");
        outputPath.toFile().getParentFile().mkdirs();

        String commandString = String.join(" ", command);
        Files.write(commandPath, commandString.getBytes());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectRootPath == null ? null : projectRootPath.toFile());
        processBuilder.redirectOutput(ProcessBuilder.Redirect.to(outputPath.toFile()));
        processBuilder.redirectError(ProcessBuilder.Redirect.to(errorPath.toFile()));
        Process process = processBuilder.start();

        Thread shutdownHook = new Thread(() -> {
            LOGGER.atDebug().log("Terminating command '" + commandString + "' due to shutdown.");
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
