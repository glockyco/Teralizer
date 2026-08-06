package teralizer.processing.task;

import static teralizer.util.Configuration.EVOSUITE_JAR_PATH;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.EvosuiteReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

public class EvoSuitePostprocessingTask extends AbstractTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvoSuitePostprocessingTask.class);

    private static final Set<String> EXPECTED_STATISTICS_COLUMNS = new HashSet<>(Arrays.asList(
        "TARGET_CLASS", "criterion", "Coverage", "Total_Goals", "Covered_Goals"
    ));

    public EvoSuitePostprocessingTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public EvoSuitePostprocessingTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Path evoSuiteDataDir = EvoSuiteGenerationTask.buildEvoSuiteDataPath(this.projectRecord);

        if (!Files.exists(evoSuiteDataDir)) {
            LOGGER.atDebug().log("Skipping EvoSuite test postprocessing. EvoSuite data directory '" + evoSuiteDataDir + "' does not exist.");
            return;
        }

        Path evoSuiteReportDir = this.projectRecord.getRootPath().resolve("evosuite-report");
        Path statisticsFilePath = evoSuiteReportDir.resolve("statistics.csv");

        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        if (Files.exists(statisticsFilePath)) {
            List<EvosuiteReportRecord> records = this.parseEvoSuiteStatistics(create, statisticsFilePath);
            create.batchInsert(records).execute();
        } else {
            throw new RuntimeException("EvoSuite statistics file not found at: " + statisticsFilePath);
        }

        Files.move(evoSuiteReportDir, evoSuiteDataDir.resolve(evoSuiteReportDir.getFileName()));

        Path evoSuiteTestsDir = evoSuiteDataDir.resolve("evosuite-tests");
        Path evoSuiteProcessedTestsDir = evoSuiteDataDir.resolve("evosuite-tests-processed");

        Files.walkFileTree(evoSuiteTestsDir, new EvoSuiteTestVisitor(
            this,
            create,
            this.getProjectId(),
            evoSuiteTestsDir,
            evoSuiteProcessedTestsDir,
            this.projectRecord.getTestSourcePath()
        ));
    }

    private List<EvosuiteReportRecord> parseEvoSuiteStatistics(DSLContext create, Path statisticsFilePath) throws IOException {
        List<EvosuiteReportRecord> records = new ArrayList<>();
        CsvReportParser.CsvReport report = CsvReportParser.parse(statisticsFilePath);
        List<String> columns = report.header();
        Set<String> columnSet = new HashSet<>(columns);

        if (!columnSet.containsAll(EXPECTED_STATISTICS_COLUMNS)) {
            Set<String> missingColumns = new HashSet<>(EXPECTED_STATISTICS_COLUMNS);
            missingColumns.removeAll(columnSet);
            throw new RuntimeException(
                "Statistics file is missing expected columns: " + missingColumns + ". " +
                    "Found columns: " + columnSet + "."
            );
        }

        int classNameIndex = this.getIndexOfColumn(columns, "TARGET_CLASS");
        int criterionIndex = this.getIndexOfColumn(columns, "criterion");
        int coverageIndex = this.getIndexOfColumn(columns, "Coverage");
        int totalGoalsIndex = this.getIndexOfColumn(columns, "Total_Goals");
        int coveredGoalsIndex = this.getIndexOfColumn(columns, "Covered_Goals");

        for (List<String> parts : report.rows()) {
            String className = parts.get(classNameIndex).trim();
            String criterion = parts.get(criterionIndex).trim();
            float coverage = Float.parseFloat(parts.get(coverageIndex).trim());
            int totalGoals = Integer.parseInt(parts.get(totalGoalsIndex).trim());
            int coveredGoals = Integer.parseInt(parts.get(coveredGoalsIndex).trim());

            EvosuiteReportRecord record = create.newRecord(Tables.EVOSUITE_REPORT);
            record.setProjectId(this.projectRecord.getId());
            record.setClassName(className);
            record.setCriterion(criterion);
            record.setCoverage(coverage);
            record.setTotalGoals(totalGoals);
            record.setCoveredGoals(coveredGoals);

            records.add(record);
        }
        return records;
    }

    private int getIndexOfColumn(List<String> columns, String value) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).equals(value)) {
                return i;
            }
        }
        return -1;
    }

    private static class EvoSuiteTestVisitor extends SimpleFileVisitor<Path> {

        private static final Logger LOGGER = LoggerFactory.getLogger(EvoSuiteTestVisitor.class);

        private final Task task;
        private final DSLContext create;
        private final long projectId;
        private final Path evoSuiteTestsDir;
        private final Path evoSuiteProcessedTestsDir;
        private final Path projectTestsDir;

        public EvoSuiteTestVisitor(
            Task task,
            DSLContext create,
            long projectId,
            Path evoSuiteTestsDir,
            Path evoSuiteProcessedTestsDir,
            Path projectTestsDir
        ) {
            this.task = task;
            this.create = create;
            this.projectId = projectId;
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

            Path relativeFilePath = this.evoSuiteTestsDir.relativize(file);
            Path processedFilePath = this.evoSuiteProcessedTestsDir.resolve(relativeFilePath);
            Path targetFilePath = this.projectTestsDir.resolve(relativeFilePath);

            for (CtClass<?> clazz : model.getElements(new TypeFilter<>(CtClass.class))) {
                if (clazz.isAnonymous()) {
                    continue;
                }

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

                        boolean containsUndeclaredExceptionComment = method.getElements(new TypeFilter<>(CtComment.class)).stream()
                            .anyMatch(comment -> comment.getContent().contains("Undeclared exception!"));

                        if (containsEvoSuiteInvocations || containsEvoSuiteVariables || containsUndeclaredExceptionComment) {
                            // EvoSuite tests that pass this filter are entered
                            // into the DB during later processing stages, so
                            // we only need to store the filtered ones here.

                            TestRecord record = this.create.newRecord(Tables.TEST);
                            record.setProjectId(this.projectId);
                            record.setTestFilePath(targetFilePath.toString());
                            record.setTestClassQualifiedName(clazz.getQualifiedName());
                            record.setTestMethodQualifiedName(clazz.getQualifiedName() + "." + method.getSimpleName());
                            record.setTestPackageName(clazz.getPackage().getQualifiedName());
                            record.setTestClassName(clazz.getSimpleName());
                            record.setTestMethodName(method.getSimpleName());

                            String exclusionInfo = "Excluded by " + this.task + ".\n\n"
                                + "containsEvoSuiteInvocations = " + containsEvoSuiteInvocations + "\n"
                                + "containsEvoSuiteVariables = " + containsEvoSuiteVariables + "\n"
                                + "containsUndeclaredExceptionComment = " + containsUndeclaredExceptionComment + "\n";

                            record.setExclusionInfo(exclusionInfo);
                            record.setIsIncluded(false);
                            record.store();

                            return true;
                        }

                        return false;
                    }).forEach(clazz::removeMethod);

                CtCompilationUnit compilationUnit = clazz.getPosition().getCompilationUnit();
                compilationUnit.getImports().removeIf(i -> i.toString().contains("org.evosuite"));

                clazz.setAnnotations(Collections.emptyList());
                clazz.setSuperclass(null);

                DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(launcher.getEnvironment());
                printer.calculate(compilationUnit, Collections.singletonList(clazz));
                byte[] processedFileBytes = printer.getResult().getBytes();

                // Write the processed file to the data directory for cross-run storage:
                Files.createDirectories(processedFilePath.getParent());
                Files.write(processedFilePath, processedFileBytes);

                // Copy the file to the project directory for further use in this run:
                Files.createDirectories(targetFilePath.getParent());
                Files.copy(processedFilePath, targetFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
