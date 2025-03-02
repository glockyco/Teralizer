package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EvoSuiteGenerationTask extends AbstractTask {

    public static final Path EVOSUITE_JAR_PATH = Paths.get("src/main/resources/evosuite/evosuite-1.2.0.jar");

    private static final Logger LOGGER = LoggerFactory.getLogger(EvoSuiteGenerationTask.class);

    private final ConsoleCommand consoleCommand;

    public EvoSuiteGenerationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public EvoSuiteGenerationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    public static Path buildEvoSuiteDataPath(ProjectRecord projectRecord) {
        return projectRecord.getDataPath().resolve("project-id-" + projectRecord.getId() + "/evosuite");
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (!this.projectRecord.getUseTestGeneration()) {
            LOGGER.atDebug().log("Skipping EvoSuite test generation. Setting project.useTestGeneration is set to false.");
            return;
        }

        Path startPath = this.projectRecord.getMainCompiledPath();

        Predicate<Path> isClassFile = path ->
            path.toString().endsWith(".class")
                && !path.getFileName().toString().contains("$");

        try (Stream<Path> paths = Files.walk(startPath)) {
            List<String> targetClasses = paths.filter(isClassFile)
                .map(path -> startPath.relativize(path).toString()
                    .replace(File.separator, ".")
                    .replace(".class", "")
                ).collect(Collectors.toList());

            for (int i = 0; i < targetClasses.size(); i++) {
                String targetClass = targetClasses.get(i);
                try {
                    this.generateTests(targetClass);
                    reportInfo.accept("Successfully generated tests for '" + targetClass + "' (#" + (i + 1) + ").");
                } catch (Exception e) {
                    reportInfo.accept("Failed to generate tests for '" + targetClass + "' (#" + (i + 1) + ").");
                }
            }
        }
    }

    private void generateTests(String targetClass) throws ConsoleCommandException, IOException, InterruptedException {
        Path evoSuiteDataDir = buildEvoSuiteDataPath(this.projectRecord);
        evoSuiteDataDir.toFile().mkdirs();

        String projectCP = Arrays.stream(this.projectRecord.getClasspath().split(File.pathSeparator))
            .filter(p -> Files.exists(Paths.get(p)))
            .map(p -> Paths.get(p).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));

        List<String> command = new ArrayList<>(Arrays.asList(
            "java",
            "-jar", EVOSUITE_JAR_PATH.toAbsolutePath().toString(),
            "-base_dir", evoSuiteDataDir.toAbsolutePath().toString(),
            "-class", targetClass,
            "-projectCP", projectCP,
            "-seed", "0",
            "-Dstopping_condition=MAXTIME",
            "-Dsearch_budget=1",
            "-Djunit_check=false",
            "-Dcoverage=false",
            "-Dfilter_sandbox_tests=true",
            "-Duse_separate_classloader=false",
            "-Dassertion_strategy=UNIT",
            "-Dcriterion=LINE:BRANCH"
        ));

        switch (this.projectRecord.getTestFramework()) {
            case JUNIT_4:
                command.add("-Dtest_format=JUNIT4");
                break;
            case JUNIT_5:
                command.add("-Dtest_format=JUNIT5");
                break;
            default:
                throw new RuntimeException("Unsupported test framework " + this.projectRecord.getTestFramework() + ".");
        }

        this.consoleCommand.execute(command);
    }
}
