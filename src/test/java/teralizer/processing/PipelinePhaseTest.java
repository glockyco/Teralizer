package teralizer.processing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Example;
import org.jooq.SQLDialect;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.processing.task.Task;
import teralizer.util.Configuration;

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
    void reductionStagesIncludeStageFiveCollectorsAndRestore() {
        Assert.assertEquals(
            EnumSet.of(
                ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
                ProcessingStage.COLLECT_JACOCO_DATA_INITIAL,
                ProcessingStage.COLLECT_PIT_DATA_INITIAL,
                ProcessingStage.RESTORE_GENERALIZED_BUILD,
                ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED,
                ProcessingStage.COLLECT_PIT_DATA_GENERALIZED
            ),
            PipelinePhase.REDUCTION.stages()
        );
    }

    @Example
    void reductionPreconditionRequiresGeneralizedSourceArchiveEvenWhenWorkspaceHasSources() throws Exception {
        Path testSourceRoot = Files.createTempDirectory("teralizer-pipeline-phase-test");
        Path packageRoot = testSourceRoot.resolve("com/example");
        Files.createDirectories(packageRoot);
        Files.write(
            packageRoot.resolve("_Calculator_Generalized_IMPROVED_100_TRIES_Test.java"),
            Arrays.asList("class _Calculator_Generalized_IMPROVED_100_TRIES_Test {}")
        );
        ProjectRecord project = project(testSourceRoot);

        PhasePreconditionException thrown = Assert.assertThrows(
            PhasePreconditionException.class,
            () -> PipelinePhase.REDUCTION.checkPreconditions(project)
        );

        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("generalized source archive"));

        for (String variant : Configuration.getGeneralizationVariants()) {
            seedArchive(project, variant);
        }

        PipelinePhase.REDUCTION.checkPreconditions(project);
    }

    @Example
    void phasesScheduleStagesInCurrentPipelineOrder() throws Exception {
        ProjectRecord project = project(Files.createTempDirectory("teralizer-pipeline-phase-schedule"));

        List<Task> tasks = new ArrayList<>();
        PipelinePhase.GENERATION.schedule(project, tasks::add);
        Assert.assertEquals(
            Arrays.asList(
                "EvoSuiteGenerationTask:GENERATE_EVOSUITE_TESTS:null",
                "EvoSuitePostprocessingTask:POSTPROCESS_EVOSUITE_TESTS:null"
            ),
            taskSignatures(tasks)
        );

        tasks.clear();
        PipelinePhase.GENERALIZATION.schedule(project, tasks::add);
        List<String> expectedGeneralization = new ArrayList<>(Arrays.asList(
            "SpoonModelBuildingTask:BUILD_SPOON_MODEL:null",
            "TestExecutionTask:EXECUTE_TESTS_ORIGINAL:null",
            "JunitDataCollectionTask:COLLECT_JUNIT_REPORTS_ORIGINAL:null",
            "JacocoDataCollectionTask:COLLECT_JACOCO_DATA_ORIGINAL:null",
            "TestFilteringTask:FILTER_TESTS_ORIGINAL:null",
            "TestAnalysisTask:ANALYZE_TESTS:null",
            "TestFilteringTask:FILTER_TESTS:null",
            "TestFilteringTask:FILTER_ASSERTIONS:null",
            "JpfInstrumentationTask:ADD_JPF_INSTRUMENTATION:null",
            "ProjectBuildTask:BUILD_PROJECT_INSTRUMENTED:null",
            "JpfExecutionTask:EXECUTE_JPF:null",
            "JpfAnalysisTask:ANALYZE_JPF:null",
            "CleanupTask:CLEANUP_JPF_INSTRUMENTATION:null",
            "ProjectBuildTask:BUILD_PROJECT_INITIAL:null",
            "TestExecutionTask:EXECUTE_TESTS_INITIAL:null",
            "JunitDataCollectionTask:COLLECT_JUNIT_REPORTS_INITIAL:null"
        ));
        for (String variant : Configuration.getGeneralizationVariants()) {
            expectedGeneralization.add("CleanupTask:CLEANUP_GENERALIZATION:" + variant);
            expectedGeneralization.add("TestGeneralizationTask:GENERALIZE_TESTS:" + variant);
            expectedGeneralization.add("ProjectBuildTask:BUILD_PROJECT_GENERALIZED:" + variant);
            expectedGeneralization.add("TestExecutionTask:EXECUTE_TESTS_GENERALIZED:" + variant);
            expectedGeneralization.add("JunitDataCollectionTask:COLLECT_JUNIT_REPORTS_GENERALIZED:" + variant);
            expectedGeneralization.add("TestFilteringTask:FILTER_GENERALIZATIONS:" + variant);
        }
        Assert.assertEquals(expectedGeneralization, taskSignatures(tasks));

        tasks.clear();
        PipelinePhase.REDUCTION.schedule(project, tasks::add);
        List<String> expectedReduction = new ArrayList<>(Arrays.asList(
            "PitDataCollectionTask:COLLECT_PIT_DATA_ORIGINAL:null",
            "JacocoDataCollectionTask:COLLECT_JACOCO_DATA_INITIAL:null",
            "PitDataCollectionTask:COLLECT_PIT_DATA_INITIAL:null"
        ));
        for (String variant : Configuration.getGeneralizationVariants()) {
            expectedReduction.add("GeneralizedSourceRestoreTask:RESTORE_GENERALIZED_BUILD:" + variant);
            expectedReduction.add("JacocoDataCollectionTask:COLLECT_JACOCO_DATA_GENERALIZED:" + variant);
            expectedReduction.add("PitDataCollectionTask:COLLECT_PIT_DATA_GENERALIZED:" + variant);
        }
        Assert.assertEquals(expectedReduction, taskSignatures(tasks));
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
        project.setDataPath(testSourceRoot.resolve("data"));
        project.setUseTestGeneration(true);
        project.setUseTestGeneralization(true);
        project.setUseTestReduction(true);
        return project;
    }

    private static List<String> taskSignatures(List<Task> tasks) {
        List<String> signatures = new ArrayList<>();
        for (Task task : tasks) {
            signatures.add(task.getClass().getSimpleName() + ":" + task.getStage() + ":" + task.getVariant());
        }
        return signatures;
    }

    private static void seedArchive(ProjectRecord project, String variant) throws Exception {
        Path archive = project.getDataPath()
            .resolve("project-id-" + project.getId())
            .resolve("generalized-sources")
            .resolve(variant)
            .resolve("com/example");
        Files.createDirectories(archive);
        Files.write(
            archive.resolve("_Calculator_Generalized_" + variant + "_Test.java"),
            Arrays.asList("class _Calculator_Generalized_" + variant + "_Test {}")
        );
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
