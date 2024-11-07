package teralizer.processing.task;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            "-Duse_separate_classloader=false"
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
        JavaParser javaParser = context.get(this.getProjectId(), TaskContext.JAVA_PARSER);
        Files.walkFileTree(evoSuiteTestsDir, new EvoSuiteTestVisitor(javaParser, evoSuiteTestsDir, this.projectRecord.getTestSourcePath()));
    }

    private static class EvoSuiteTestVisitor extends SimpleFileVisitor<Path> {

        private static final Logger LOGGER = LoggerFactory.getLogger(EvoSuiteTestVisitor.class);

        private final JavaParser javaParser;
        private final Path evoSuiteTestsDir;
        private final Path projectTestsDir;

        public EvoSuiteTestVisitor(JavaParser javaParser, Path evoSuiteTestsDir, Path projectTestsDir) {
            this.javaParser = javaParser;
            this.evoSuiteTestsDir = evoSuiteTestsDir;
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

            CompilationUnit compilationUnit = this.javaParser.parse(file).getResult().get();
            compilationUnit.getImports().removeIf((i) -> i.getNameAsString().startsWith("org.evosuite"));
            for (ClassOrInterfaceDeclaration classDeclaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                classDeclaration.getAnnotations().removeIf((a) -> true);
                classDeclaration.getExtendedTypes().removeIf((t) -> true);

                classDeclaration.getMembers().removeIf(m -> m instanceof FieldDeclaration
                    && ((FieldDeclaration) m).getElementType().toString().equals("EvoRunnerJUnit5"));

                classDeclaration.getMembers().removeIf(m -> m instanceof MethodDeclaration
                    && m.findAll(MethodCallExpr.class).stream()
                    .map(NodeWithSimpleName::getNameAsString)
                    .anyMatch(n -> n.equals("verifyException") || n.equals("assertThrownBy")));

                classDeclaration.getMembers().removeIf(m -> m instanceof MethodDeclaration
                    && m.getAllContainedComments().stream().anyMatch(c -> c.getContent().equals(" Undeclared exception!")));
            }

            Path relativeFilePath = this.evoSuiteTestsDir.relativize(file);
            Path targetFilePath = this.projectTestsDir.resolve(relativeFilePath);
            targetFilePath.getParent().toFile().mkdirs();
            Files.write(targetFilePath, compilationUnit.toString().getBytes());
        }
    }
}
