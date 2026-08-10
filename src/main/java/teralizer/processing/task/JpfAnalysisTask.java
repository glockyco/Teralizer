package teralizer.processing.task;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.JpfExtractionSummaryRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TaskDiagnosticRecord;
import org.jooq.generated.tables.records.TaskRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.tools.json.JSONArray;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.jpf.ModelStatistics;
import teralizer.jpf.ModelStatisticsExtractor;
import teralizer.processing.ProcessingStage;
import teralizer.processing.ProcessingStatus;
import teralizer.processing.TaskContext;
import teralizer.repository.PipelineQueries;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

public class JpfAnalysisTask extends AbstractTask {

    public JpfAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, projectRecord, null, null);
    }

    public JpfAnalysisTask(ProcessingStage stage, ProjectRecord projectRecord, TestRecord testRecord, AssertionRecord assertionRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.testRecord = testRecord;
        this.assertionRecord = assertionRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        if (this.assertionRecord == null) {
            DSLContext create = context.get(TaskContext.DSL_CONTEXT);
            Gson gson = context.get(TaskContext.GSON);
            this.writeExtractionSummaries(create, gson);
            this.updateEquivalencies(create);
            this.scheduleTasks(create, scheduleTask);
        } else {
            Gson gson = context.get(TaskContext.GSON);
            this.updateModelStatistics(gson);
        }
    }

    private void scheduleTasks(DSLContext create, Consumer<Task> scheduleTask) {
        Result<Record> records = PipelineQueries.fetchIncludedAssertions(create, this.getProjectId());

        if (records.isEmpty()) {
            // Naming a stage here without reading the rejections blames whichever one this task
            // happens to sit behind. Assertions are excluded by the filter that rejects them,
            // which usually runs long before specification extraction.
            List<String> rejections = PipelineQueries.fetchAssertionRejectionSummary(create, this.getProjectId());
            throw new RuntimeException(
                "No assertion of project " + this.getProjectId() + " reached test generalization. "
                + "Recorded rejections: "
                + (rejections.isEmpty() ? "none, so no assertion was ever analyzed" : String.join(", ", rejections))
                + ". Per-assertion reasons are in filter_result."
            );
        }

        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfAnalysisTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void writeExtractionSummaries(DSLContext create, Gson gson) {
        List<AssertionRecord> assertions = create.selectFrom(Tables.ASSERTION)
            .where(Tables.ASSERTION.PROJECT_ID.eq(this.getProjectId()))
            .fetch();
        List<TaskRecord> tasks = create.selectFrom(Tables.TASK)
            .where(Tables.TASK.PROJECT_ID.eq(this.getProjectId()))
            .and(Tables.TASK.STAGE.in(ProcessingStage.ADD_JPF_INSTRUMENTATION, ProcessingStage.EXECUTE_JPF))
            .fetch();
        List<TaskDiagnosticRecord> diagnostics = create.selectFrom(Tables.TASK_DIAGNOSTIC)
            .where(Tables.TASK_DIAGNOSTIC.PROJECT_ID.eq(this.getProjectId()))
            .and(Tables.TASK_DIAGNOSTIC.STAGE.in(
                ProcessingStage.ADD_JPF_INSTRUMENTATION.name(),
                ProcessingStage.EXECUTE_JPF.name(),
                ProcessingStage.ANALYZE_JPF.name()
            ))
            .fetch();
        Set<Long> testIds = assertions.stream().map(AssertionRecord::getTestId).collect(Collectors.toSet());
        testIds.addAll(tasks.stream().map(TaskRecord::getTestId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<JpfExtractionSummaryRecord> summaries = new ArrayList<>();
        summaries.add(this.summaryRecord(create, gson, null, assertions, tasks, diagnostics));
        for (Long testId : testIds) {
            summaries.add(this.summaryRecord(create, gson, testId, assertions, tasks, diagnostics));
        }
        create.batchInsert(summaries).execute();
    }

    private JpfExtractionSummaryRecord summaryRecord(
        DSLContext create,
        Gson gson,
        Long testId,
        List<AssertionRecord> assertions,
        List<TaskRecord> tasks,
        List<TaskDiagnosticRecord> diagnostics
    ) {
        List<AssertionRecord> scopedAssertions = assertions.stream()
            .filter(assertion -> testId == null || testId.equals(assertion.getTestId()))
            .collect(Collectors.toList());
        List<TaskRecord> scopedTasks = tasks.stream()
            .filter(task -> testId == null || testId.equals(task.getTestId()))
            .collect(Collectors.toList());
        Map<String, Long> failureCounts = diagnostics.stream()
            .filter(diagnostic -> testId == null || testId.equals(diagnostic.getTestId()))
            .collect(Collectors.groupingBy(
                TaskDiagnosticRecord::getReasonCode,
                LinkedHashMap::new,
                Collectors.counting()
            ));

        JpfExtractionSummaryRecord record = create.newRecord(Tables.JPF_EXTRACTION_SUMMARY);
        record.setProjectId(this.getProjectId());
        record.setTestId(testId);
        record.setAssertionsScheduled((int) scopedTasks.stream()
            .filter(task -> task.getStage() == ProcessingStage.EXECUTE_JPF)
            .count());
        record.setAssertionsInstrumented((int) scopedTasks.stream()
            .filter(task -> task.getStage() == ProcessingStage.ADD_JPF_INSTRUMENTATION)
            .filter(task -> task.getStatus() == ProcessingStatus.SUCCEEDED)
            .count());
        record.setAssertionsJpfSucceeded((int) scopedTasks.stream()
            .filter(task -> task.getStage() == ProcessingStage.EXECUTE_JPF)
            .filter(task -> task.getStatus() == ProcessingStatus.SUCCEEDED)
            .count());
        record.setAssertionsJpfFailed((int) scopedTasks.stream()
            .filter(task -> task.getStage() == ProcessingStage.EXECUTE_JPF)
            .filter(task -> task.getStatus() == ProcessingStatus.FAILED)
            .count());
        record.setAssertionsWithInputSpec((int) scopedAssertions.stream()
            .filter(assertion -> assertion.getOutputSpecClass() != null)
            .count());
        record.setAssertionsWithOutputSpec((int) scopedAssertions.stream()
            .filter(assertion -> assertion.getOutputSpecClass() != null)
            .count());
        record.setAssertionsWithCompleteSpec((int) scopedAssertions.stream()
            .filter(assertion -> assertion.getOutputSpecClass() != null)
            .count());
        record.setFailureCounts(JSONB.valueOf(gson.toJson(failureCounts)));
        return record;
    }

    private void updateEquivalencies(DSLContext create) {
        Map<EquivalencyKey, List<Long>> groups = PipelineQueries.fetchIncludedAssertions(create, this.getProjectId())
            .map(r -> r.into(AssertionRecord.class)).stream().parallel().collect(Collectors.groupingByConcurrent(
                record -> new EquivalencyKey(computeFileHash(record), record.getTestedMethodQualifiedName()),
                Collectors.mapping(AssertionRecord::getId, Collectors.toList())
            ));

        List<Query> updates = groups.values().stream()
            .flatMap(group -> {
                String sortedIds = JSONArray.toJSONString(group.stream().sorted().collect(Collectors.toList()));
                return group.stream().map(id -> create.update(Tables.ASSERTION)
                    .set(Tables.ASSERTION.EQUIVALENT_ASSERTIONS, sortedIds)
                    .where(Tables.ASSERTION.ID.eq(id)));
            })
            .collect(Collectors.toList());

        create.batch(updates).execute();
    }

    private static String computeFileHash(AssertionRecord record) {
        try (
            InputStream is = Files.newInputStream(Paths.get(record.getInputSpecificationPath()));
            DigestInputStream dis = new DigestInputStream(is, MessageDigest.getInstance("MD5"))
        ) {
            while (dis.read() != -1) ; // empty loop to consume the stream
            return bytesToHex(dis.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static class EquivalencyKey {

        private final String fileHash;
        private final String methodName;

        EquivalencyKey(String fileHash, String methodName) {
            this.fileHash = fileHash;
            this.methodName = methodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || this.getClass() != o.getClass()) return false;
            EquivalencyKey that = (EquivalencyKey) o;
            return Objects.equals(this.fileHash, that.fileHash) && Objects.equals(this.methodName, that.methodName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.fileHash, this.methodName);
        }
    }

    private void updateModelStatistics(Gson gson) throws IOException {
        Type type = new TypeToken<List<MethodParameter>>() {}.getType();
        List<MethodParameter> parameters = gson.fromJson(this.assertionRecord.getTestedMethodParameters(), type);
        Map<String, String> variableTypes = parameters == null
            ? Collections.emptyMap()
            : parameters.stream().collect(Collectors.toMap(MethodParameter::getName, MethodParameter::getType));
        ModelStatistics inputStatistics = this.calculateModelStatistics(Paths.get(this.assertionRecord.getInputSpecificationPath()), variableTypes);
        ModelStatistics outputStatistics = this.calculateModelStatistics(Paths.get(this.assertionRecord.getOutputSpecificationPath()), variableTypes);
        this.assertionRecord.setInputModelStatistics(gson.toJson(inputStatistics));
        this.assertionRecord.setOutputModelStatistics(gson.toJson(outputStatistics));
        this.assertionRecord.store();
    }

    private ModelStatistics calculateModelStatistics(Path modelPath, Map<String, String> variableTypes) throws IOException {
        String modelString = new String(Files.readAllBytes(modelPath));
        Model model = new JsonToModelTransformer().transform(modelString);
        String modelJava = new ModelToJavaTransformer(variableTypes).transform(model);
        return new ModelStatisticsExtractor().process(model, modelJava);
    }
}
