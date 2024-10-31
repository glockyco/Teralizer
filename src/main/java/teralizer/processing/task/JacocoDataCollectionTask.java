package teralizer.processing.task;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.JacocoCoverageReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.ConsoleCommand;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class JacocoDataCollectionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public JacocoDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public JacocoDataCollectionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<JacocoCoverageReportRecord> coverageReportsRecords = createCoverageReportRecords(create, this.variant, this.projectRecord, this.consoleCommand);
        create.batchStore(coverageReportsRecords).execute();
    }

    private static List<JacocoCoverageReportRecord> createCoverageReportRecords(DSLContext create, GeneralizationVariant variant, ProjectRecord projectRecord, ConsoleCommand consoleCommand) throws Exception {
        executeCoverageReporting(projectRecord, consoleCommand);
        return collectCoverageData(create, projectRecord, variant);
    }

    private static void executeCoverageReporting(ProjectRecord projectRecord, ConsoleCommand consoleCommand) throws Exception {
        List<String> command;
        switch (projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot execute coverage reporting for project " + projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot execute coverage reporting for project " + projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot execute coverage reporting for project " + projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = buildGradleCommand();
                break;
            case MAVEN:
                command = buildMavenCommand();
                break;
            default:
                throw new RuntimeException("Cannot execute coverage reporting for project " + projectRecord.getRootPath() + ". Unsupported project type " + projectRecord.getType() + ".");
        }
        consoleCommand.execute(projectRecord.getRootPath(), command);
    }

    private static List<JacocoCoverageReportRecord> collectCoverageData(DSLContext create, ProjectRecord projectRecord, GeneralizationVariant variant) throws IOException {
        Path reportPath = getCoverageReportPath(projectRecord);

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect coverage data. Report file '" + reportPath + "' does not exist.");
        }

        List<JacocoCoverageReportRecord> coverageReportRecords = new ArrayList<>();
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(reportPath.toFile()))) {
            reader.readLine(); // Skip the line with the column labels.
            while ((line = reader.readLine()) != null) {
                // Not a robust solution for splitting the columns, but good
                // enough for the JaCoCo reports that we are reading from.
                String[] data = line.split(",");

                JacocoCoverageReportRecord coverageReportRecord = create.newRecord(Tables.JACOCO_COVERAGE_REPORT);
                coverageReportRecord.setProjectId(projectRecord.getId());
                coverageReportRecord.setVariant(variant);

                // Skipping data[0], which is the project name.
                coverageReportRecord.setCoveredPackage(data[1]);
                coverageReportRecord.setCoveredClass(data[2]);

                coverageReportRecord.setInstructionMissed(data[3]);
                coverageReportRecord.setInstructionCovered(data[4]);
                coverageReportRecord.setBranchMissed(data[5]);
                coverageReportRecord.setBranchCovered(data[6]);
                coverageReportRecord.setLineMissed(data[7]);
                coverageReportRecord.setLineCovered(data[8]);
                coverageReportRecord.setComplexityMissed(data[9]);
                coverageReportRecord.setComplexityCovered(data[10]);
                coverageReportRecord.setMethodMissed(data[11]);
                coverageReportRecord.setMethodCovered(data[12]);

                coverageReportRecords.add(coverageReportRecord);
            }
        }
        return coverageReportRecords;
    }

    private static List<String> buildGradleCommand() {
        return new ArrayList<>(Arrays.asList("./gradlew", "--build-file", ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE, "--info", "-Djacoco.skip=false", "jacocoTestReport"));
    }

    private static List<String> buildMavenCommand() {
        return new ArrayList<>(Arrays.asList("mvn", "--file", ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE, "-Djacoco.skip=false", "jacoco:report"));
    }

    private static Path getCoverageReportPath(ProjectRecord projectRecord) {
        String reportFileName;
        switch (projectRecord.getType()) {
            case GRADLE:
                reportFileName = "jacocoTestReport.csv";
                break;
            case MAVEN:
                reportFileName = "jacoco.csv";
                break;
            case JAIGANTIC:
            case ANT:
            case UNKNOWN:
            default:
                throw new RuntimeException("Cannot read coverage reports for project " + projectRecord.getRootPath() + ". Unsupported project type " + projectRecord.getType() + ".");
        }
        return projectRecord.getCoverageReportsPath().resolve(reportFileName);
    }
}
