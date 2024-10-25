package teralizer.processing.task;

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.MutationReportRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.GeneralizationVariant;
import teralizer.processing.MutationStatus;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MutationDataCollectionTask extends AbstractTask {

    public MutationDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public MutationDataCollectionTask(ProcessingStage stage, GeneralizationVariant variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.variant = variant;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<MutationReportRecord> mutationReportRecords = this.createMutationReportRecords(create, this.variant, this.projectRecord);
        create.batchStore(mutationReportRecords).execute();
    }

    private List<MutationReportRecord> createMutationReportRecords(DSLContext create, GeneralizationVariant variant, ProjectRecord projectRecord) throws Exception {
        Path reportPath = projectRecord.getMutationReportsPath().resolve("mutations.xml");

        if (!reportPath.toFile().exists()) {
            throw new RuntimeException("Failed to collect mutation data. Report file '" + reportPath + "' does not exist.");
        }

        Document mutationsDocument = new SAXReader().read(reportPath.toFile());
        Element mutationsElement = mutationsDocument.getRootElement();

        List<MutationReportRecord> mutationReportRecords = new ArrayList<>();
        for (Element mutationElement : mutationsElement.elements("mutation")) {
            MutationReportRecord mutationReportRecord = create.newRecord(Tables.MUTATION_REPORT);
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
}
