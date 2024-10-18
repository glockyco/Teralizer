package teralizer.processing.task;

import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.*;
import teralizer.processing.MutationStatus;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class MutationDataCollectionTask implements Task {

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;
    private final String generalizationVariant;

    public MutationDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null);
    }

    public MutationDataCollectionTask(ProcessingStage stage, ProjectRecord projectRecord, String generalizationVariant) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.generalizationVariant = generalizationVariant;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        List<MutationReportRecord> mutationReportRecords = this.createMutationReportRecords(create, this.projectRecord, this.generalizationVariant);
        create.batchStore(mutationReportRecords).execute();
    }

    private List<MutationReportRecord> createMutationReportRecords(DSLContext create, ProjectRecord projectRecord, String generalizationVariant) throws Exception {
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
            mutationReportRecord.setGeneralizationVariant(generalizationVariant);

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

    @Override
    public ProcessingStage getStage() {
        return this.stage;
    }

    @Override
    public Integer getProjectId() {
        return this.projectRecord.getId();
    }

    @Override
    public Integer getTestId() {
        return null;
    }

    @Override
    public Integer getGeneralizationId() {
        return null;
    }

    @Override
    public String toString() {
        return "MutationDataCollectionTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            ", generalizationVariant=" + this.generalizationVariant +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MutationDataCollectionTask)) return false;
        MutationDataCollectionTask that = (MutationDataCollectionTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId()) && Objects.equals(this.generalizationVariant, that.generalizationVariant);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId(), this.generalizationVariant);
    }
}
