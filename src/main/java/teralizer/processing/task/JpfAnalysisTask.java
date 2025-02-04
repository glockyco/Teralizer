package teralizer.processing.task;

import org.jooq.DSLContext;
import org.jooq.Query;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;
import org.jooq.tools.json.JSONArray;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.repository.SQLiteRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
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
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);

        this.updateEquivalencies(create);
    }

    private void updateEquivalencies(DSLContext create) {
        Map<String, List<Integer>> hashGroups = SQLiteRepository.fetchIncludedAssertions(create, this.getProjectId())
            .map(r -> r.into(AssertionRecord.class)).stream().parallel().collect(Collectors.groupingByConcurrent(
                this::computeFileHash, Collectors.mapping(AssertionRecord::getId, Collectors.toList())
            ));

        List<Query> updates = hashGroups.values().stream()
            .flatMap(hashGroup -> {
                String sortedIds = JSONArray.toJSONString(hashGroup.stream().sorted().collect(Collectors.toList()));
                return hashGroup.stream().map(id -> create.update(Tables.ASSERTION)
                    .set(Tables.ASSERTION.EQUIVALENT_ASSERTIONS, sortedIds)
                    .where(Tables.ASSERTION.ID.eq(id)));
            })
            .collect(Collectors.toList());

        create.batch(updates).execute();
    }

    private String computeFileHash(AssertionRecord record) {
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
}
