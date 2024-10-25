package teralizer.processing.task;

import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.CoverageReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CoverageDataCollectionTask extends AbstractTask {

    public CoverageDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public CoverageDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, String tool) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.tool = tool;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<CoverageReportRecord> coverageReportsRecords = this.createCoverageReportRecords(create, this.projectRecord, this.tool);
        create.batchStore(coverageReportsRecords).execute();
    }

    private List<CoverageReportRecord> createCoverageReportRecords(DSLContext create, ProjectRecord projectRecord, String generalizationVariant) throws Exception {
        Path reportPath = this.getCoverageReportPath(projectRecord);

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect mutation data. Report file '" + reportPath + "' does not exist.");
        }

        List<CoverageReportRecord> coverageReportRecords = new ArrayList<>();
        String line;
        try (BufferedReader reader = new BufferedReader(new FileReader(reportPath.toFile()))) {
            reader.readLine(); // Skip the line with the column labels.
            while ((line = reader.readLine()) != null) {
                // Not a robust solution for splitting the columns, but good
                // enough for the JaCoCo reports that we are reading from.
                String[] data = line.split(",");

                CoverageReportRecord coverageReportRecord = create.newRecord(Tables.COVERAGE_REPORT);
                coverageReportRecord.setProjectId(projectRecord.getId());
                coverageReportRecord.setGeneralizationVariant(generalizationVariant);

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

    private Path getCoverageReportPath(ProjectRecord projectRecord) {
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
                throw new RuntimeException("Cannot read coverage reports for project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }
        return projectRecord.getCoverageReportsPath().resolve(reportFileName);
    }
}
