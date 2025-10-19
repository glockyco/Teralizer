package teralizer.processing.task;

import com.google.gson.Gson;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.tools.json.JSONArray;
import teralizer.domain.Model;
import teralizer.jpf.ModelStatistics;
import teralizer.jpf.ModelStatisticsExtractor;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;
import teralizer.transformer.JsonToModelTransformer;
import teralizer.transformer.ModelToJavaTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
            this.updateEquivalencies(create);
            this.scheduleTasks(create, scheduleTask);
        } else {
            Gson gson = context.get(TaskContext.GSON);
            this.updateModelStatistics(gson);
        }
    }

    private void scheduleTasks(DSLContext create, Consumer<Task> scheduleTask) {
        Result<Record> records = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId());

        if (records.isEmpty()) {
            throw new RuntimeException(
                "All assertions were excluded during specification extraction (SPF failures). " +
                "No specifications available for test generalization."
            );
        }

        for (Record record : records) {
            TestRecord testRecord = record.into(TestRecord.class);
            AssertionRecord assertionRecord = record.into(AssertionRecord.class);
            scheduleTask.accept(new JpfAnalysisTask(this.stage, this.projectRecord, testRecord, assertionRecord));
        }
    }

    private void updateEquivalencies(DSLContext create) {
        Map<EquivalencyKey, List<Long>> groups = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId())
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
        ModelStatistics inputStatistics = this.calculateModelStatistics(Paths.get(this.assertionRecord.getInputSpecificationPath()));
        ModelStatistics outputStatistics = this.calculateModelStatistics(Paths.get(this.assertionRecord.getOutputSpecificationPath()));
        this.assertionRecord.setInputModelStatistics(gson.toJson(inputStatistics));
        this.assertionRecord.setOutputModelStatistics(gson.toJson(outputStatistics));
        this.assertionRecord.store();
    }

    private ModelStatistics calculateModelStatistics(Path modelPath) throws IOException {
        String modelString = new String(Files.readAllBytes(modelPath));
        Model model = new JsonToModelTransformer().transform(modelString);
        String modelJava = new ModelToJavaTransformer().transform(model);
        return new ModelStatisticsExtractor().process(model, modelJava);
    }
}
