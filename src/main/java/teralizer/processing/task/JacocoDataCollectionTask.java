package teralizer.processing.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.JacocoCoverageReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;

public class JacocoDataCollectionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public JacocoDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public JacocoDataCollectionTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.stage == ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL && !Configuration.isPitestOriginalEnabled()) {
            reportInfo.accept("ORIGINAL-stage JaCoCo skipped: its only consumer is ORIGINAL PIT,"
                + " which is disabled (teralizer.pitest.original.enabled = false).");
            return;
        }
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<JacocoCoverageReportRecord> coverageReportsRecords = this.createCoverageReportRecords(create, this.consoleCommand);
        create.batchStore(coverageReportsRecords).execute();
    }

    private List<JacocoCoverageReportRecord> createCoverageReportRecords(DSLContext create, ConsoleCommand consoleCommand) throws Exception {
        this.executeCoverageReporting(consoleCommand);
        return this.collectCoverageData(create, this.projectRecord.getDataPath().resolve("project-id-" + this.getProjectId() + "/jacoco-data"));
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

    private List<JacocoCoverageReportRecord> collectCoverageData(DSLContext create, Path dataDirectory) throws IOException {
        Path reportPath = getCoverageReportPath(this.projectRecord);

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect coverage data. Report file '" + reportPath + "' does not exist.");
        }

        // Preserve the full raw data in the data directory:
        String stageName = this.getStage().getStep() + "-" + this.getStage();
        String variantName = this.getVariant() == null ? "" : ("." + this.getVariant());
        String fileName = stageName + variantName + "." + reportPath.getFileName().toString();
        dataDirectory.toFile().mkdirs();
        Files.copy(reportPath, dataDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);

        // Read (relevant parts of) the data and write it to the DB:
        List<JacocoCoverageReportRecord> coverageReportRecords = new ArrayList<>();
        CsvReportParser.CsvReport report = CsvReportParser.parse(reportPath);
        if (report.header().size() < 13) {
            throw new RuntimeException("Malformed JaCoCo report " + reportPath
                + ": expected at least 13 columns but found " + report.header().size());
        }
        for (List<String> data : report.rows()) {
            JacocoCoverageReportRecord record = create.newRecord(Tables.JACOCO_COVERAGE_REPORT);
            record.setProjectId(this.projectRecord.getId());

            record.setStep(this.getStage().getStep());
            record.setStage(this.getStage());
            record.setVariant(this.getVariant());

            // Skipping data[0], which is the project name.
            record.setCoveredPackage(data.get(1));
            record.setCoveredClass(data.get(2));

            record.setInstructionMissed(Integer.parseInt(data.get(3)));
            record.setInstructionCovered(Integer.parseInt(data.get(4)));
            record.setBranchMissed(Integer.parseInt(data.get(5)));
            record.setBranchCovered(Integer.parseInt(data.get(6)));
            record.setLineMissed(Integer.parseInt(data.get(7)));
            record.setLineCovered(Integer.parseInt(data.get(8)));
            record.setComplexityMissed(Integer.parseInt(data.get(9)));
            record.setComplexityCovered(Integer.parseInt(data.get(10)));
            record.setMethodMissed(Integer.parseInt(data.get(11)));
            record.setMethodCovered(Integer.parseInt(data.get(12)));

            coverageReportRecords.add(record);
        }
        return coverageReportRecords;
    }

    private List<String> buildGradleCommand() {
        Path preserved = preservedExecPath(this.projectRecord, this.getProjectId(), this.stage, this.getVariant());
        return new ArrayList<>(Arrays.asList(
            "./gradlew",
            "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE,
            "--info",
            "-Djacoco.skip=false",
            "-PjacocoExec=" + preserved.toAbsolutePath(),
            "jacocoTestReport"
        ));
    }

    private List<String> buildMavenCommand() {
        Path preserved = preservedExecPath(this.projectRecord, this.getProjectId(), this.stage, this.getVariant());
        return new ArrayList<>(Arrays.asList(
            "mvn",
            "--file", mavenBuildFileFor(this.stage),
            "-Djacoco.skip=false",
            "-Djacoco.dataFile=" + preserved.toAbsolutePath(),
            "jacoco:report"
        ));
    }

    static Path preservedExecPath(ProjectRecord projectRecord, long projectId, ProcessingStage stage, String variant) {
        String variantPart = variant == null ? "" : ("." + variant);
        return projectRecord.getDataPath()
            .resolve("project-id-" + projectId)
            .resolve("jacoco-data")
            .resolve(stage.name() + variantPart + ".exec");
    }

    static ProcessingStage jacocoStageFor(ProcessingStage stage) {
        switch (stage) {
            case EXECUTE_TESTS_ORIGINAL:
            case COLLECT_JACOCO_DATA_ORIGINAL:
                return ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL;
            case EXECUTE_TESTS_INITIAL:
            case COLLECT_JACOCO_DATA_INITIAL:
                return ProcessingStage.COLLECT_JACOCO_DATA_INITIAL;
            case EXECUTE_TESTS_GENERALIZED:
            case COLLECT_JACOCO_DATA_GENERALIZED:
                return ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED;
            default:
                throw new IllegalArgumentException("Cannot map stage to JaCoCo data collection stage: " + stage);
        }
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

final class CsvReportParser {

    private CsvReportParser() {
    }

    static CsvReport parse(Path path) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            boolean quotedField = false;
            boolean fieldStarted = false;
            int character;
            int rowNumber = 1;
            while ((character = reader.read()) != -1) {
                char current = (char) character;
                if (inQuotes) {
                    if (current == '"') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next == '"') {
                            field.append('"');
                        } else {
                            inQuotes = false;
                            if (next != -1) {
                                reader.reset();
                            }
                        }
                    } else {
                        field.append(current);
                    }
                } else if (current == '"') {
                    if (fieldStarted || field.length() > 0) {
                        throw malformed(path, rowNumber, "quote in an unquoted field");
                    }
                    inQuotes = true;
                    quotedField = true;
                    fieldStarted = true;
                } else if (current == ',') {
                    row.add(field.toString());
                    field.setLength(0);
                    quotedField = false;
                    fieldStarted = false;
                } else if (current == '\n' || current == '\r') {
                    if (current == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                    }
                    row.add(field.toString());
                    rows.add(row);
                    row = new ArrayList<>();
                    field.setLength(0);
                    quotedField = false;
                    fieldStarted = false;
                    rowNumber++;
                } else {
                    if (quotedField) {
                        throw malformed(path, rowNumber, "characters after a quoted field");
                    }
                    field.append(current);
                    fieldStarted = true;
                }
            }
            if (inQuotes) {
                throw malformed(path, rowNumber, "unterminated quoted field");
            }
            if (fieldStarted || !row.isEmpty()) {
                row.add(field.toString());
                rows.add(row);
            }
        }

        if (rows.isEmpty()) {
            throw new RuntimeException("Empty CSV report: " + path);
        }
        List<String> header = rows.get(0);
        if (header.isEmpty()) {
            throw malformed(path, 1, "empty header");
        }
        for (int i = 1; i < rows.size(); i++) {
            List<String> data = rows.get(i);
            if (data.size() != header.size()) {
                throw malformed(path, i + 1, "expected " + header.size() + " columns but found " + data.size());
            }
        }
        return new CsvReport(header, rows.subList(1, rows.size()));
    }

    private static RuntimeException malformed(Path path, int rowNumber, String detail) {
        return new RuntimeException("Malformed CSV report " + path + " at row " + rowNumber + ": " + detail);
    }

    static final class CsvReport {
        private final List<String> header;
        private final List<List<String>> rows;

        private CsvReport(List<String> header, List<List<String>> rows) {
            this.header = Collections.unmodifiableList(new ArrayList<>(header));
            this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
        }

        List<String> header() {
            return this.header;
        }

        List<List<String>> rows() {
            return this.rows;
        }
    }
}
