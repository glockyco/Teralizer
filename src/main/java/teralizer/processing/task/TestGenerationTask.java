package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.ConsoleCommand;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class TestGenerationTask extends AbstractTask {

    public static final Path EVOSUITE_JAR_PATH = Paths.get("src/main/resources/evosuite/evosuite-1.2.0.jar");

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGenerationTask.class);

    private final ConsoleCommand consoleCommand;

    public TestGenerationTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public TestGenerationTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (!this.projectRecord.getUseTestGeneration()) {
            LOGGER.atDebug().log("Skipping test generation. Setting project.useTestGeneration is set to false.");
            return;
        }

        Path evoSuiteDataDir = this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/evosuite");
        evoSuiteDataDir.toFile().mkdirs();

        String projectCP = Arrays.stream(this.projectRecord.getClasspath().split(File.pathSeparator))
            .filter(p -> Files.exists(Paths.get(p)))
            .map(p -> Paths.get(p).toAbsolutePath().toString())
            .collect(Collectors.joining(File.pathSeparator));

        List<String> command = new ArrayList<>(Arrays.asList(
            "java",
            "-jar", EVOSUITE_JAR_PATH.toAbsolutePath().toString(),
            "-base_dir", evoSuiteDataDir.toAbsolutePath().toString(),
            "-target", this.projectRecord.getMainCompiledPath().toAbsolutePath().toString(),
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

        Path evoSuiteReportDir = Paths.get("evosuite-report");
        Files.move(evoSuiteReportDir, evoSuiteDataDir.resolve(evoSuiteReportDir));

        Path evoSuiteTestsDir = evoSuiteDataDir.resolve("evosuite-tests");
        Path evoSuiteProcessedTestsDir = evoSuiteDataDir.resolve("evosuite-tests-processed");

        Files.walkFileTree(evoSuiteTestsDir, new EvoSuiteTestVisitor(
            evoSuiteTestsDir,
            evoSuiteProcessedTestsDir,
            this.projectRecord.getTestSourcePath()
        ));
    }

    private static class EvoSuiteTestVisitor extends SimpleFileVisitor<Path> {

        private static final Logger LOGGER = LoggerFactory.getLogger(EvoSuiteTestVisitor.class);

        private final Path evoSuiteTestsDir;
        private final Path evoSuiteProcessedTestsDir;
        private final Path projectTestsDir;

        public EvoSuiteTestVisitor(
            Path evoSuiteTestsDir,
            Path evoSuiteProcessedTestsDir,
            Path projectTestsDir
        ) {
            this.evoSuiteTestsDir = evoSuiteTestsDir;
            this.evoSuiteProcessedTestsDir = evoSuiteProcessedTestsDir;
            this.projectTestsDir = projectTestsDir;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (file.toString().endsWith("ESTest.java")) {
                LOGGER.atDebug().log("Preparing EvoSuite test file " + file + ".");
                this.prepareEvoSuiteTestFile(file);
            } else if (file.toString().endsWith("ESTest_scaffolding.java")) {
                // Keep the scaffolding file as-is.
            }
            return FileVisitResult.CONTINUE;
        }

        private void prepareEvoSuiteTestFile(Path file) throws IOException {
            // PIT does not play well with the isolation features of
            // EvoSuite-generated tests. To be able to use EvoSuite tests
            // anyway, we remove all EvoSuite dependencies from the tests.

            Launcher launcher = new Launcher();
            launcher.addInputResource(file.toString());
            launcher.getEnvironment().setSourceClasspath(new String[]{EVOSUITE_JAR_PATH.toString()});
            CtModel model = launcher.buildModel();

            for (CtClass<?> clazz : model.getElements(new TypeFilter<>(CtClass.class))) {
                clazz.getFields().stream()
                    .filter(field -> field.getType().toString().startsWith("org.evosuite"))
                    .forEach(clazz::removeField);

                clazz.getMethods().stream()
                    .filter(method -> {
                        boolean containsEvoSuiteInvocations = method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                            .anyMatch(invocation -> {
                                CtExecutableReference<?> executable = invocation.getExecutable();
                                String qualifiedName = executable.getDeclaringType() != null ? executable.getDeclaringType().getQualifiedName() : "";
                                return qualifiedName.startsWith("org.evosuite");
                            });

                        boolean containsEvoSuiteVariables = method.getElements(new TypeFilter<>(CtLocalVariable.class)).stream()
                            .anyMatch(localVar -> localVar.getType().toString().startsWith("org.evosuite"));

                        return containsEvoSuiteInvocations || containsEvoSuiteVariables;
                    }).forEach(clazz::removeMethod);

                CtCompilationUnit compilationUnit = clazz.getPosition().getCompilationUnit();
                compilationUnit.getImports().removeIf(i -> i.toString().contains("org.evosuite"));

                clazz.setAnnotations(Collections.emptyList());
                clazz.setSuperclass(null);

                DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(launcher.getEnvironment());
                printer.calculate(compilationUnit, Collections.singletonList(clazz));
                byte[] processedFileBytes = printer.getResult().getBytes();

                Path relativeFilePath = this.evoSuiteTestsDir.relativize(file);

                // Write the processed file to the data directory for cross-run storage:
                Path processedFilePath = this.evoSuiteProcessedTestsDir.resolve(relativeFilePath);
                processedFilePath.getParent().toFile().mkdirs();
                Files.write(processedFilePath, processedFileBytes);

                // Copy the file to the project directory for further use in this run:
                Path targetFilePath = this.projectTestsDir.resolve(relativeFilePath);
                targetFilePath.getParent().toFile().mkdirs();
                Files.copy(processedFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
