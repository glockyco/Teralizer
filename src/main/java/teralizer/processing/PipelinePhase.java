package teralizer.processing;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.task.CleanupTask;
import teralizer.processing.task.EvoSuiteGenerationTask;
import teralizer.processing.task.EvoSuitePostprocessingTask;
import teralizer.processing.task.GeneralizedSourceRestoreTask;
import teralizer.processing.task.JacocoDataCollectionTask;
import teralizer.processing.task.JpfAnalysisTask;
import teralizer.processing.task.JpfExecutionTask;
import teralizer.processing.task.JpfInstrumentationTask;
import teralizer.processing.task.JunitDataCollectionTask;
import teralizer.processing.task.PitDataCollectionTask;
import teralizer.processing.task.ProjectBuildTask;
import teralizer.processing.task.SpoonModelBuildingTask;
import teralizer.processing.task.Task;
import teralizer.processing.task.TestAnalysisTask;
import teralizer.processing.task.TestExecutionTask;
import teralizer.processing.task.TestFilteringTask;
import teralizer.processing.task.TestGeneralizationTask;
import teralizer.util.Configuration;

public enum PipelinePhase implements Phase {
    GENERATION {
        @Override
        public Set<ProcessingStage> stages() {
            return GENERATION_STAGES;
        }

        @Override
        public boolean isRequested(ProjectRecord project) {
            return Boolean.TRUE.equals(project.getUseTestGeneration());
        }

        @Override
        public void checkPreconditions(ProjectRecord project) {
        }

        @Override
        public void schedule(ProjectRecord project, Consumer<Task> schedule) {
            schedule.accept(new EvoSuiteGenerationTask(ProcessingStage.GENERATE_EVOSUITE_TESTS, project));
            schedule.accept(new EvoSuitePostprocessingTask(ProcessingStage.POSTPROCESS_EVOSUITE_TESTS, project));
        }

        @Override
        public void clear(DSLContext create, ProjectRecord project) {
            Long projectId = project.getId();
            create.deleteFrom(Tables.EVOSUITE_REPORT)
                .where(Tables.EVOSUITE_REPORT.PROJECT_ID.eq(projectId))
                .execute();
            create.deleteFrom(Tables.EVOSUITE_RUNTIME)
                .where(Tables.EVOSUITE_RUNTIME.PROJECT_ID.eq(projectId))
                .execute();
            if (Boolean.TRUE.equals(project.getUseTestGeneration())) {
                deleteFiles(project, PipelinePhase::isEvoSuiteTestFile);
            }
            deletePhaseTasks(create, project, this.stages());
        }
    },
    GENERALIZATION {
        @Override
        public Set<ProcessingStage> stages() {
            return GENERALIZATION_STAGES;
        }

        @Override
        public boolean isRequested(ProjectRecord project) {
            return Boolean.TRUE.equals(project.getUseTestGeneralization());
        }

        @Override
        public void checkPreconditions(ProjectRecord project) {
            if (!hasFile(project, PipelinePhase::isJavaSourceFile)) {
                throw new PhasePreconditionException("no project test sources under " + resolvedTestSourcePath(project));
            }
        }

        @Override
        public void schedule(ProjectRecord project, Consumer<Task> schedule) {
            schedule.accept(new SpoonModelBuildingTask(ProcessingStage.BUILD_SPOON_MODEL, project));

            schedule.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_ORIGINAL, project));
            schedule.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL, project));
            schedule.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL, project));
            schedule.accept(new TestFilteringTask(ProcessingStage.FILTER_TESTS_ORIGINAL, project));

            schedule.accept(new TestAnalysisTask(ProcessingStage.ANALYZE_TESTS, project));
            schedule.accept(new TestFilteringTask(ProcessingStage.FILTER_TESTS, project));
            schedule.accept(new TestFilteringTask(ProcessingStage.FILTER_ASSERTIONS, project));

            schedule.accept(new JpfInstrumentationTask(ProcessingStage.ADD_JPF_INSTRUMENTATION, project));
            schedule.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_INSTRUMENTED, project));
            schedule.accept(new JpfExecutionTask(ProcessingStage.EXECUTE_JPF, project));
            schedule.accept(new JpfAnalysisTask(ProcessingStage.ANALYZE_JPF, project));
            schedule.accept(new CleanupTask(ProcessingStage.CLEANUP_JPF_INSTRUMENTATION, project));

            schedule.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_INITIAL, project));
            schedule.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_INITIAL, project));
            schedule.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_INITIAL, project));

            for (String variant : Configuration.getGeneralizationVariants()) {
                schedule.accept(new CleanupTask(ProcessingStage.CLEANUP_GENERALIZATION, variant, project));
                schedule.accept(new TestGeneralizationTask(ProcessingStage.GENERALIZE_TESTS, variant, project));
                schedule.accept(new ProjectBuildTask(ProcessingStage.BUILD_PROJECT_GENERALIZED, variant, project));
                schedule.accept(new TestExecutionTask(ProcessingStage.EXECUTE_TESTS_GENERALIZED, variant, project));
                schedule.accept(new JunitDataCollectionTask(ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED, variant, project));
                schedule.accept(new TestFilteringTask(ProcessingStage.FILTER_GENERALIZATIONS, variant, project));
            }
        }

        @Override
        public void clear(DSLContext create, ProjectRecord project) {
            Long projectId = project.getId();
            create.deleteFrom(Tables.JQWIK_EXECUTION_RUN)
                .where(Tables.JQWIK_EXECUTION_RUN.PROJECT_ID.eq(projectId))
                .and(Tables.JQWIK_EXECUTION_RUN.STAGE.in(stageNames(this.stages())))
                .execute();
            create.deleteFrom(Tables.FILTER_RESULT)
                .where(Tables.FILTER_RESULT.PROJECT_ID.eq(projectId))
                .execute();
            create.deleteFrom(Tables.GENERALIZATION)
                .where(Tables.GENERALIZATION.PROJECT_ID.eq(projectId))
                .execute();
            deleteFiles(project, PipelinePhase::isGeneralizedTestSourceFile);
            deletePhaseTasks(create, project, this.stages());
        }
    },
    REDUCTION {
        @Override
        public Set<ProcessingStage> stages() {
            return REDUCTION_STAGES;
        }

        @Override
        public boolean isRequested(ProjectRecord project) {
            return Boolean.TRUE.equals(project.getUseTestReduction());
        }

        @Override
        public void checkPreconditions(ProjectRecord project) {
            requireGeneralizedSourceArchives(project);
        }

        @Override
        public void schedule(ProjectRecord project, Consumer<Task> schedule) {
            schedule.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_ORIGINAL, project));
            schedule.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_INITIAL, project));
            schedule.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_INITIAL, project));

            for (String variant : Configuration.getGeneralizationVariants()) {
                schedule.accept(new GeneralizedSourceRestoreTask(variant, project));
                schedule.accept(new JacocoDataCollectionTask(ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED, variant, project));
                schedule.accept(new PitDataCollectionTask(ProcessingStage.COLLECT_PIT_DATA_GENERALIZED, variant, project));
            }
        }

        @Override
        public void clear(DSLContext create, ProjectRecord project) {
            Long projectId = project.getId();
            create.deleteFrom(Tables.PIT_MUTATION_REPORT)
                .where(Tables.PIT_MUTATION_REPORT.PROJECT_ID.eq(projectId))
                .and(Tables.PIT_MUTATION_REPORT.STAGE.in(this.stages()))
                .execute();
            create.deleteFrom(Tables.PIT_COVERAGE_REPORT)
                .where(Tables.PIT_COVERAGE_REPORT.PROJECT_ID.eq(projectId))
                .and(Tables.PIT_COVERAGE_REPORT.STAGE.in(this.stages()))
                .execute();
            create.deleteFrom(Tables.JACOCO_COVERAGE_REPORT)
                .where(Tables.JACOCO_COVERAGE_REPORT.PROJECT_ID.eq(projectId))
                .and(Tables.JACOCO_COVERAGE_REPORT.STAGE.in(this.stages()))
                .execute();
            deletePhaseTasks(create, project, this.stages());
        }
    };

    private static final Set<ProcessingStage> GENERATION_STAGES = immutableStages(
        ProcessingStage.GENERATE_EVOSUITE_TESTS,
        ProcessingStage.POSTPROCESS_EVOSUITE_TESTS
    );

    private static final Set<ProcessingStage> GENERALIZATION_STAGES = Collections.unmodifiableSet(EnumSet.of(
        ProcessingStage.BUILD_SPOON_MODEL,
        ProcessingStage.EXECUTE_TESTS_ORIGINAL,
        ProcessingStage.COLLECT_JUNIT_REPORTS_ORIGINAL,
        ProcessingStage.COLLECT_JACOCO_DATA_ORIGINAL,
        ProcessingStage.FILTER_TESTS_ORIGINAL,
        ProcessingStage.ANALYZE_TESTS,
        ProcessingStage.FILTER_TESTS,
        ProcessingStage.FILTER_ASSERTIONS,
        ProcessingStage.ADD_JPF_INSTRUMENTATION,
        ProcessingStage.BUILD_PROJECT_INSTRUMENTED,
        ProcessingStage.EXECUTE_JPF,
        ProcessingStage.ANALYZE_JPF,
        ProcessingStage.CLEANUP_JPF_INSTRUMENTATION,
        ProcessingStage.BUILD_PROJECT_INITIAL,
        ProcessingStage.EXECUTE_TESTS_INITIAL,
        ProcessingStage.COLLECT_JUNIT_REPORTS_INITIAL,
        ProcessingStage.CLEANUP_GENERALIZATION,
        ProcessingStage.GENERALIZE_TESTS,
        ProcessingStage.BUILD_PROJECT_GENERALIZED,
        ProcessingStage.EXECUTE_TESTS_GENERALIZED,
        ProcessingStage.COLLECT_JUNIT_REPORTS_GENERALIZED,
        ProcessingStage.FILTER_GENERALIZATIONS
    ));

    private static final Set<ProcessingStage> REDUCTION_STAGES = Collections.unmodifiableSet(EnumSet.of(
        ProcessingStage.COLLECT_PIT_DATA_ORIGINAL,
        ProcessingStage.COLLECT_JACOCO_DATA_INITIAL,
        ProcessingStage.COLLECT_PIT_DATA_INITIAL,
        ProcessingStage.RESTORE_GENERALIZED_BUILD,
        ProcessingStage.COLLECT_JACOCO_DATA_GENERALIZED,
        ProcessingStage.COLLECT_PIT_DATA_GENERALIZED
    ));

    public abstract Set<ProcessingStage> stages();

    public abstract boolean isRequested(ProjectRecord project);

    public abstract void checkPreconditions(ProjectRecord project);

    public abstract void schedule(ProjectRecord project, Consumer<Task> schedule);

    public abstract void clear(DSLContext create, ProjectRecord project);

    private static Set<ProcessingStage> immutableStages(ProcessingStage first, ProcessingStage second) {
        return Collections.unmodifiableSet(EnumSet.of(first, second));
    }

    private static void deletePhaseTasks(DSLContext create, ProjectRecord project, Set<ProcessingStage> stages) {
        create.deleteFrom(Tables.TASK)
            .where(Tables.TASK.PROJECT_ID.eq(project.getId()))
            .and(Tables.TASK.STAGE.in(stages))
            .execute();
    }

    private static Set<String> stageNames(Set<ProcessingStage> stages) {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (ProcessingStage stage : stages) {
            names.add(stage.name());
        }
        return names;
    }

    private static void requireGeneralizedSourceArchives(ProjectRecord project) {
        for (String variant : Configuration.getGeneralizationVariants()) {
            Path archive = generalizedSourceArchivePath(project, variant);
            if (!hasAnyFile(archive)) {
                throw new PhasePreconditionException(
                    "no generalized source archive for variant " + variant + " under " + archive
                );
            }
        }
    }

    private static Path generalizedSourceArchivePath(ProjectRecord project, String variant) {
        Path dataPath = project.getDataPath();
        Long projectId = project.getId();
        if (dataPath == null || projectId == null) {
            throw new PhasePreconditionException("no generalized source archive because project data path or id is missing");
        }
        return dataPath.resolve("project-id-" + projectId)
            .resolve("generalized-sources")
            .resolve(variant);
    }

    private static boolean hasAnyFile(Path root) {
        if (root == null || !Files.exists(root)) {
            return false;
        }
        FindingVisitor visitor = new FindingVisitor(file -> true);
        try {
            Files.walkFileTree(root, visitor);
        } catch (StopWalk stop) {
            return true;
        } catch (IOException e) {
            throw new PhasePreconditionException("failed to inspect generalized source archive under " + root, e);
        }
        return false;
    }

    private static boolean hasFile(ProjectRecord project, PathPredicate predicate) {
        Path root = resolvedTestSourcePath(project);
        if (root == null || !Files.exists(root)) {
            return false;
        }
        FindingVisitor visitor = new FindingVisitor(predicate);
        try {
            Files.walkFileTree(root, visitor);
        } catch (StopWalk stop) {
            return true;
        } catch (IOException e) {
            throw new PhasePreconditionException("failed to inspect test sources under " + root, e);
        }
        return false;
    }

    private static void deleteFiles(ProjectRecord project, PathPredicate predicate) {
        Path root = cleanupTestSourcePath(project);
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new DeletingVisitor(predicate));
        } catch (IOException e) {
            throw new PhasePreconditionException("failed to delete phase sources under " + root, e);
        }
    }

    private static Path resolvedTestSourcePath(ProjectRecord project) {
        Path testSourcePath = project.getTestSourcePath();
        if (testSourcePath == null && project.getRootPath() != null) {
            return project.getRootPath().resolve("src/test/java");
        }
        return testSourcePath;
    }

    private static Path cleanupTestSourcePath(ProjectRecord project) {
        Path testSourcePath = resolvedTestSourcePath(project);
        if (testSourcePath != null && Files.exists(testSourcePath)) {
            return testSourcePath;
        }
        return project.getRootPath();
    }

    private static boolean isEvoSuiteTestFile(Path file) {
        return file.getFileName().toString().endsWith("ESTest.java");
    }

    private static boolean isGeneralizedTestSourceFile(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.startsWith("_") && fileName.contains("_Generalized_");
    }

    private static boolean isJavaSourceFile(Path file) {
        return file.getFileName().toString().endsWith(".java");
    }

    private interface PathPredicate {
        boolean test(Path file);
    }

    private static final class FindingVisitor extends SimpleFileVisitor<Path> {
        private final PathPredicate predicate;

        private FindingVisitor(PathPredicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (this.predicate.test(file)) {
                throw new StopWalk();
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class DeletingVisitor extends SimpleFileVisitor<Path> {
        private final PathPredicate predicate;

        private DeletingVisitor(PathPredicate predicate) {
            this.predicate = predicate;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (this.predicate.test(file)) {
                Files.deleteIfExists(file);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private static final class StopWalk extends IOException {
    }
}
