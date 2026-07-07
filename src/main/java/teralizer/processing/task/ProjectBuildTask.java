package teralizer.processing.task;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.generated.Tables;
import org.jooq.generated.tables.records.AssertionRecord;
import org.jooq.generated.tables.records.GeneralizationRecord;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.diagnostics.BuildEnvironmentObservationWriter;
import teralizer.processing.diagnostics.GeneralizationLifecycleWriter;
import teralizer.processing.filter.FilterReasonCodes;
import teralizer.repository.PipelineQueries;
import teralizer.spoon.codegen.GeneratedTestValidator;
import teralizer.util.Configuration;
import teralizer.util.ConsoleCommand;
import teralizer.util.ConsoleCommandException;

public class ProjectBuildTask extends AbstractTask {

    private final ConsoleCommand consoleCommand;

    public ProjectBuildTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this(stage, null, projectRecord);
    }

    public ProjectBuildTask(ProcessingStage stage, String variant, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
        this.variant = variant;
        this.consoleCommand = new ConsoleCommand(stage, variant, projectRecord.getId(), projectRecord.getDataPath());
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        this.buildProject(context, reportInfo);
    }

    void buildProject(TaskContext context, Consumer<String> reportInfo) throws Exception {
        DSLContext create = context.get(TaskContext.DSL_CONTEXT);
        BuildEnvironmentObservationWriter.record(create, this.projectRecord, this.stage);

        this.quarantineUncompilableGeneratedTests(create, reportInfo);

        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot build project " + this.projectRecord.getRootPath() + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
            case ANT:
                this.buildAnt(this.projectRecord.getRootPath());
                break;
            case GRADLE:
                this.buildGradle(this.projectRecord.getRootPath());
                break;
            case MAVEN:
                this.buildMaven(this.projectRecord.getRootPath());
                break;
            default:
                throw new RuntimeException("Cannot build project " + this.projectRecord.getRootPath() + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        if (this.projectRecord.getMainCompiledPath() == null || !Files.exists(this.projectRecord.getMainCompiledPath())) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Main compiled path '" + this.projectRecord.getMainCompiledPath() + "' does not exist.");
        }
        if ((this.projectRecord.getTestCompiledPath() == null || !Files.exists(this.projectRecord.getTestCompiledPath())) && !this.projectRecord.getUseTestGeneration()) {
            throw new RuntimeException("Cannot setup project " + this.projectRecord.getRootPath() + ". Test compiled path '" + this.projectRecord.getTestCompiledPath() + "' does not exist.");
        }

        if (this.stage == ProcessingStage.BUILD_PROJECT_GENERALIZED) {
            GeneralizationLifecycleWriter.recordProjectStageSucceeded(create, this.stage, this.getProjectId(), this.getVariant());
        }
    }

    private void buildAnt(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("ant", "-f", "build.xml", "compile");
        this.consoleCommand.execute(projectRootPath, command);
    }

    private void buildGradle(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("./gradlew", "--build-file", Configuration.GRADLE_CUSTOM_BUILD_FILE, "--info", "clean", "compileJava", "compileTestJava");
        this.consoleCommand.execute(projectRootPath, command);
    }

    private void buildMaven(Path projectRootPath) throws IOException, InterruptedException, ConsoleCommandException {
        List<String> command = Arrays.asList("mvn", "--file", Configuration.MAVEN_CUSTOM_BUILD_FILE, "clean", "compile", "test-compile");
        this.consoleCommand.execute(projectRootPath, command);
    }

    /*
     * Generated wrappers and generalized tests are extracted out of their original test bodies, so
     * a shape the codegen cannot yet express soundly compiles to a file javac rejects. The build
     * below compiles the whole test tree atomically, so one such file would drop the entire
     * project. Validate the generated files first, then quarantine the ones that do not compile:
     * delete them from the source set and mark the owning row excluded with a typed reason, so the
     * loss is one assertion (or one generalization), recorded, not the project.
     */
    private void quarantineUncompilableGeneratedTests(DSLContext create, Consumer<String> reportInfo) {
        if (this.stage == ProcessingStage.BUILD_PROJECT_INSTRUMENTED) {
            this.quarantineInstrumentedWrappers(create, reportInfo);
        } else if (this.stage == ProcessingStage.BUILD_PROJECT_GENERALIZED) {
            this.quarantineGeneralizedTests(create, reportInfo);
        }
    }

    private void quarantineInstrumentedWrappers(DSLContext create, Consumer<String> reportInfo) {
        Result<Record> assertions = PipelineQueries.fetchIncludedAssertions(create, this.getProjectId());
        List<Path> wrappers = new ArrayList<>();
        Map<Path, AssertionRecord> byWrapper = new HashMap<>();
        for (Record record : assertions) {
            AssertionRecord assertion = record.into(AssertionRecord.class);
            String wrapperPath = assertion.getInstrumentedFilePath();
            if (wrapperPath == null) {
                continue;
            }
            Path wrapper = Paths.get(wrapperPath);
            if (Files.exists(wrapper)) {
                wrappers.add(wrapper);
                byWrapper.put(wrapper, assertion);
            }
        }
        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            wrappers, this.projectRecord.getClasspath(), this.sourceRoots());
        for (Path wrapper : uncompilable) {
            AssertionRecord assertion = byWrapper.get(wrapper);
            deleteQuietly(wrapper);
            if (assertion.getDriverFilePath() != null) {
                deleteQuietly(Paths.get(assertion.getDriverFilePath()));
            }
            create.update(Tables.ASSERTION)
                .set(Tables.ASSERTION.IS_INCLUDED, false)
                .set(Tables.ASSERTION.EXCLUSION_INFO, FilterReasonCodes.UNCOMPILABLE_INSTRUMENTED_WRAPPER)
                .where(Tables.ASSERTION.ID.eq(assertion.getId()))
                .execute();
        }
        if (!uncompilable.isEmpty()) {
            reportInfo.accept("Quarantined " + uncompilable.size() + " uncompilable instrumented wrapper(s) of "
                + wrappers.size() + " (" + FilterReasonCodes.UNCOMPILABLE_INSTRUMENTED_WRAPPER + ").");
        }
    }

    private void quarantineGeneralizedTests(DSLContext create, Consumer<String> reportInfo) {
        Result<Record> generalizations = PipelineQueries.fetchIncludedGeneralizations(create, this.getVariant(), this.getProjectId());
        List<Path> tests = new ArrayList<>();
        Map<Path, GeneralizationRecord> byTest = new HashMap<>();
        for (Record record : generalizations) {
            GeneralizationRecord generalization = record.into(GeneralizationRecord.class);
            String testPath = generalization.getFilePath();
            if (testPath == null || testPath.isEmpty()) {
                continue;
            }
            Path test = Paths.get(testPath);
            if (Files.exists(test)) {
                tests.add(test);
                byTest.put(test, generalization);
            }
        }
        Set<Path> uncompilable = GeneratedTestValidator.uncompilableFiles(
            tests, this.projectRecord.getClasspath(), this.sourceRoots());
        for (Path test : uncompilable) {
            GeneralizationRecord generalization = byTest.get(test);
            deleteQuietly(test);
            create.update(Tables.GENERALIZATION)
                .set(Tables.GENERALIZATION.IS_INCLUDED, false)
                .set(Tables.GENERALIZATION.EXCLUSION_INFO, FilterReasonCodes.UNCOMPILABLE_GENERALIZED_TEST)
                .where(Tables.GENERALIZATION.ID.eq(generalization.getId()))
                .execute();
        }
        if (!uncompilable.isEmpty()) {
            reportInfo.accept("Quarantined " + uncompilable.size() + " uncompilable generalized test(s) of "
                + tests.size() + " (" + FilterReasonCodes.UNCOMPILABLE_GENERALIZED_TEST + ").");
        }
    }

    private List<Path> sourceRoots() {
        return Arrays.asList(this.projectRecord.getMainSourcePath(), this.projectRecord.getTestSourcePath());
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete quarantined generated test " + path, e);
        }
    }
}
