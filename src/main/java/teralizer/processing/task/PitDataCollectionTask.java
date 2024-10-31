package teralizer.processing.task;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.PitMutationReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.MutationStatus;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.util.ConsoleCommand;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class PitDataCollectionTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public PitDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public PitDataCollectionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<PitMutationReportRecord> mutationReportRecords = createMutationReportRecords(create, this.stage, this.variant, this.projectRecord, this.consoleCommand);
        create.batchStore(mutationReportRecords).execute();
    }

    private static List<PitMutationReportRecord> createMutationReportRecords(
        DSLContext create,
        ProcessingStage stage,
        GeneralizationVariant variant,
        ProjectRecord projectRecord,
        ConsoleCommand consoleCommand
    ) throws Exception {
        executeMutationTesting(create, stage, variant, projectRecord, consoleCommand);
        return collectMutationData(create, projectRecord, variant);
    }

    private static void executeMutationTesting(
        DSLContext create,
        ProcessingStage stage,
        GeneralizationVariant variant,
        ProjectRecord projectRecord,
        ConsoleCommand consoleCommand
    ) throws Exception {
        List<String> includedTests;
        switch (stage) {
            case COLLECT_PIT_DATA_INITIAL:
                includedTests = SQLiteRepository.fetchIncludedTestClasses(create, projectRecord.getId());
                break;
            case COLLECT_PIT_DATA_GENERALIZED:
                includedTests = SQLiteRepository.fetchIncludedTestClasses(create, projectRecord.getId());
                includedTests.addAll(SQLiteRepository.fetchIncludedGeneralizedClasses(create, variant, projectRecord.getId()));
                break;
            default:
                throw new RuntimeException("Unsupported processing stage " + stage + ".");
        }

        if (includedTests.isEmpty()) {
            throw new RuntimeException("Failed mutation testing. All tests of the project are excluded.");
        }

        List<String> command;
        switch (projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot execute mutation testing for project " + projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot execute mutation testing for project " + projectRecord.getRootPath() + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot execute mutation testing for project " + projectRecord.getRootPath() + ". Ant projects are not supported yet.");
            case GRADLE:
                command = buildGradleCommand(includedTests);
                break;
            case MAVEN:
                command = buildMavenCommand(includedTests);
                break;
            default:
                throw new RuntimeException("Cannot execute mutation testing for project " + projectRecord.getRootPath() + ". Unsupported project type " + projectRecord.getType() + ".");
        }
        consoleCommand.execute(projectRecord.getRootPath(), command);
    }

    private static List<PitMutationReportRecord> collectMutationData(DSLContext create, ProjectRecord projectRecord, GeneralizationVariant variant) throws Exception {
        Path reportPath = projectRecord.getMutationReportsPath().resolve("mutations.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect mutation data. Report file '" + reportPath + "' does not exist.");
        }

        Document mutationsDocument = new SAXReader().read(reportPath.toFile());
        Element mutationsElement = mutationsDocument.getRootElement();

        List<PitMutationReportRecord> mutationReportRecords = new ArrayList<>();
        for (Element mutationElement : mutationsElement.elements("mutation")) {
            PitMutationReportRecord mutationReportRecord = create.newRecord(Tables.PIT_MUTATION_REPORT);
            mutationReportRecord.setProjectId(projectRecord.getId());
            mutationReportRecord.setVariant(variant);

            mutationReportRecord.setIsDetected(Boolean.parseBoolean(mutationElement.attributeValue("detected")));
            mutationReportRecord.setStatus(MutationStatus.valueOf(mutationElement.attributeValue("status")));
            mutationReportRecord.setNumberOfTestsRun(Integer.parseInt(mutationElement.attributeValue("numberOfTestsRun")));

            mutationReportRecord.setSourceFile(mutationElement.element("sourceFile").getText());
            mutationReportRecord.setMutatedClass(mutationElement.element("mutatedClass").getText());
            mutationReportRecord.setMutatedMethod(mutationElement.element("mutatedMethod").getText());
            mutationReportRecord.setMethodDescription(mutationElement.element("methodDescription").getText());
            mutationReportRecord.setLineNumber(Integer.parseInt(mutationElement.element("lineNumber").getText()));
            mutationReportRecord.setMutator(mutationElement.element("mutator").getText());
            mutationReportRecord.setDescription(mutationElement.element("description").getText());

            mutationReportRecords.add(mutationReportRecord);
        }
        return mutationReportRecords;
    }

    private static List<String> buildGradleCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList("./gradlew", "--build-file", ProjectSetupTask.GRADLE_CUSTOM_BUILD_FILE, "--info", "pitest"));
        if (includedTests != null) {
            command.add("-PtargetTests=" + String.join(",", includedTests));
        }
        return command;
    }

    private static List<String> buildMavenCommand(List<String> includedTests) {
        List<String> command = new ArrayList<>(Arrays.asList("mvn", "--file", ProjectSetupTask.MAVEN_CUSTOM_BUILD_FILE, "pitest:mutationCoverage"));
        if (includedTests != null) {
            command.add("-DtargetTests=" + String.join(",", includedTests));
        }
        return command;
    }
}
