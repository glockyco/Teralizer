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
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<JacocoCoverageReportRecord> coverageReportsRecords = this.createCoverageReportRecords(create, this.consoleCommand);
        create.batchStore(coverageReportsRecords).execute();
    }

    private List<JacocoCoverageReportRecord> createCoverageReportRecords(DSLContext create, ConsoleCommand consoleCommand) throws Exception {
        this.executeCoverageReporting(consoleCommand);
        return this.collectCoverageData(create);
    }

    private void executeCoverageReporting(ConsoleCommand consoleCommand) throws Exception {
        List<String> command;
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot execute coverage reporting for project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot execute coverage reporting for project " + this.projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot execute coverage reporting for project " + this.projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = buildGradleCommand();
                break;
            case MAVEN:
                command = buildMavenCommand();
                break;
            default:
                throw new RuntimeException("Cannot execute coverage reporting for project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }
        consoleCommand.execute(this.projectRecord.getRootPath(), command);
    }

    private List<JacocoCoverageReportRecord> collectCoverageData(DSLContext create) throws IOException {
        Path reportPath = getCoverageReportPath(this.projectRecord);

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

                JacocoCoverageReportRecord record = create.newRecord(Tables.JACOCO_COVERAGE_REPORT);
                record.setProjectId(this.projectRecord.getId());

                record.setStep(this.getStage().getStep());
                record.setStage(this.getStage());
                record.setVariant(this.getVariant());

                // Skipping data[0], which is the project name.
                record.setCoveredPackage(data[1]);
                record.setCoveredClass(data[2]);

                record.setInstructionMissed(Integer.parseInt(data[3]));
                record.setInstructionCovered(Integer.parseInt(data[4]));
                record.setBranchMissed(Integer.parseInt(data[5]));
                record.setBranchCovered(Integer.parseInt(data[6]));
                record.setLineMissed(Integer.parseInt(data[7]));
                record.setLineCovered(Integer.parseInt(data[8]));
                record.setComplexityMissed(Integer.parseInt(data[9]));
                record.setComplexityCovered(Integer.parseInt(data[10]));
                record.setMethodMissed(Integer.parseInt(data[11]));
                record.setMethodCovered(Integer.parseInt(data[12]));

                coverageReportRecords.add(record);
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
