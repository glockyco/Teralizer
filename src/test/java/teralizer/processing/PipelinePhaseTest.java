package teralizer.processing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.jqwik.api.Example;
import org.jooq.SQLDialect;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;

public class PipelinePhaseTest {

    @Example
    void generationStagesAreExactlyEvoSuiteGeneration() {
        Assert.assertEquals(
            EnumSet.of(
                ProcessingStage.GENERATE_EVOSUITE_TESTS,
                ProcessingStage.POSTPROCESS_EVOSUITE_TESTS
            ),
            PipelinePhase.GENERATION.stages()
        );
    }

    @Example
    void reductionStagesAreExactlyStageFiveCollectors() {
        Assert.assertEquals(
            EnumSet.of(
                ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
                ProcessingStage.COLLECT_JACOCO_DATA_INITIAL,
                ProcessingStage.COLLECT_PIT_DATA_INITIAL,
                ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED,
                ProcessingStage.COLLECT_PIT_DATA_GENERALIZED
            ),
            PipelinePhase.REDUCTION.stages()
        );
    }

    @Example
    void reductionPreconditionFailsLoudWithoutGeneralizedTests() throws Exception {
        Path testSourceRoot = Files.createTempDirectory("teralizer-pipeline-phase-test");
        Files.write(testSourceRoot.resolve("PlainTest.java"), Arrays.asList("class PlainTest {}"));
        ProjectRecord project = project(testSourceRoot);

        PhasePreconditionException thrown = Assert.assertThrows(
            PhasePreconditionException.class,
            () -> PipelinePhase.REDUCTION.checkPreconditions(project)
        );

        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("generalized test sources"));
    }

    @Example
    void clearIsIdempotentAgainstEmptyStore() throws Exception {
        RecordingDeletes deletes = new RecordingDeletes();
        ProjectRecord project = project(Files.createTempDirectory("teralizer-pipeline-phase-clear"));

        PipelinePhase.REDUCTION.clear(deletes.dsl(), project);
        PipelinePhase.REDUCTION.clear(deletes.dsl(), project);

        Assert.assertEquals(8, deletes.sql.size());
        Assert.assertTrue(deletes.containsDelete("pit_mutation_report"));
        Assert.assertTrue(deletes.containsDelete("pit_coverage_report"));
        Assert.assertTrue(deletes.containsDelete("jacoco_coverage_report"));
        Assert.assertTrue(deletes.containsDelete("task"));
    }

    private static ProjectRecord project(Path testSourceRoot) {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setRootPath(testSourceRoot.getParent());
        project.setTestSourcePath(testSourceRoot);
        project.setUseTestGeneration(true);
        project.setUseTestGeneralization(true);
        project.setUseTestReduction(true);
        return project;
    }

    private static final class RecordingDeletes implements MockDataProvider {
        private final List<String> sql = new ArrayList<>();

        private org.jooq.DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("delete")) {
                this.sql.add(sql);
            }
            return new MockResult[] {new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())};
        }

        private boolean containsDelete(String table) {
            return this.sql.stream().anyMatch(statement -> statement.startsWith("delete") && statement.contains(table));
        }
    }
}
