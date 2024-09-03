package teralizer.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class ConsoleCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsoleCommand.class);

    public static void execute(Path projectRootPath, List<String> command) throws IOException, InterruptedException {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectRootPath.toFile());
        Process process = processBuilder.start();

        try (
            InputStreamReader outputStream = new InputStreamReader(process.getInputStream());
            BufferedReader outputReader = new BufferedReader(outputStream);
            InputStreamReader errorStream = new InputStreamReader(process.getErrorStream());
            BufferedReader errorReader = new BufferedReader(errorStream)
        ) {
            output.append(outputReader.lines().collect(Collectors.joining("\n")));
            error.append(errorReader.lines().collect(Collectors.joining("\n")));
        }

        int exitCode = process.waitFor();

        if (exitCode == 0 && error.toString().isEmpty()) {
            LOGGER.atDebug().log(output.toString());
        } else {
            String errorMessage = "Output:\n\n" + output + (error.toString().isEmpty() ? "" : "\n\nError:\n\n" + error);
            throw new RuntimeException(errorMessage);
        }
    }
}
