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
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
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
    void reductionPreconditionRequiresGeneralizationToHaveRun() throws Exception {
        Path testSourceRoot = Files.createTempDirectory("teralizer-pipeline-phase-test");
        ProjectRecord project = project(testSourceRoot);
        ReductionStore store = new ReductionStore(false);

        PhasePreconditionException thrown = Assert.assertThrows(
            PhasePreconditionException.class,
            () -> PipelinePhase.REDUCTION.checkPreconditions(store.dsl(), project)
        );

        Assert.assertEquals(
            "generalization has not run for project 7; run generalization before reduction",
            thrown.getMessage()
        );
    }

    @Example
    void reductionPreconditionRequiresArchivesOnlyForVariantsWithIncludedGeneralizations() throws Exception {
        Path testSourceRoot = Files.createTempDirectory("teralizer-pipeline-phase-test");
        ProjectRecord project = project(testSourceRoot);
        PipelinePhase.REDUCTION.checkPreconditions(new ReductionStore(true).dsl(), project);

        String includedVariant = Configuration.getGeneralizationVariants()[0];
        ReductionStore partialStore = new ReductionStore(true, includedVariant);
        PhasePreconditionException thrown = Assert.assertThrows(
            PhasePreconditionException.class,
            () -> PipelinePhase.REDUCTION.checkPreconditions(partialStore.dsl(), project)
        );

        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("variant " + includedVariant));
        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("generalized source archive"));

        seedArchive(project, includedVariant);
        PipelinePhase.REDUCTION.checkPreconditions(partialStore.dsl(), project);
    }

    @Example
    void phasesScheduleStagesInCurrentPipelineOrder() throws Exception {
        ProjectRecord project = project(Files.createTempDirectory("teralizer-pipeline-phase-schedule"));

        List<Task> tasks = new ArrayList<>();
        PipelinePhase.GENERATION.schedule(new ReductionStore(true).dsl(), project, tasks::add);
        Assert.assertEquals(
            Arrays.asList(
                "EvoSuiteGenerationTask:GENERATE_EVOSUITE_TESTS:null",
                "EvoSuitePostprocessingTask:POSTPROCESS_EVOSUITE_TESTS:null"
            ),
            taskSignatures(tasks)
        );

        tasks.clear();
        PipelinePhase.GENERALIZATION.schedule(new ReductionStore(true).dsl(), project, tasks::add);
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
        PipelinePhase.REDUCTION.schedule(
            new ReductionStore(true, Configuration.getGeneralizationVariants()).dsl(),
            project,
            tasks::add
        );
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

    @Example
    void generalizationClearDeletesTestsAndGeneralizedSourceArchives() throws Exception {
        RecordingDeletes deletes = new RecordingDeletes();
        ProjectRecord project = project(Files.createTempDirectory("teralizer-pipeline-phase-generalization-clear"));
        seedArchive(project, "variant-a");
        Path archiveRoot = project.getDataPath()
            .resolve("project-id-" + project.getId())
            .resolve("generalized-sources");

        PipelinePhase.GENERALIZATION.clear(deletes.dsl(), project);
        PipelinePhase.GENERALIZATION.clear(deletes.dsl(), project);

        Assert.assertTrue(deletes.containsDelete("test"));
        Assert.assertFalse(Files.exists(archiveRoot));
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

    private static final class ReductionStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final boolean hasFilterTasks;
        private final Set<String> includedVariants;

        private ReductionStore(boolean hasFilterTasks, String... includedVariants) {
            this.hasFilterTasks = hasFilterTasks;
            this.includedVariants = new java.util.LinkedHashSet<>(Arrays.asList(includedVariants));
        }

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase(Locale.ROOT);
            if (sql.startsWith("select") && sql.contains("count") && sql.contains("task")) {
                Field<Integer> count = DSL.field("count", Integer.class);
                Result<Record1<Integer>> result = this.records.newResult(count);
                result.add(this.records.newRecord(count).values(this.hasFilterTasks ? 1 : 0));
                return new MockResult[] {new MockResult(1, result)};
            }
            if (sql.startsWith("select") && sql.contains("class_qualified_name") && sql.contains("generalization")) {
                Field<String> field = DSL.field("class_qualified_name", String.class);
                Result<Record1<String>> result = this.records.newResult(field);
                String variant = variantFrom(context.bindings());
                if (this.includedVariants.contains(variant)) {
                    result.add(this.records.newRecord(field).values("_Calculator_Generalized_" + variant + "_Test"));
                }
                return new MockResult[] {new MockResult(result.size(), result)};
            }
            return new MockResult[] {new MockResult(0, this.records.newResult())};
        }

        private static String variantFrom(Object[] bindings) {
            for (Object binding : bindings) {
                if (binding instanceof String) {
                    return binding.toString();
                }
            }
            return null;
        }
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
