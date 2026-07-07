package teralizer.processing.task;

import java.nio.file.Path;
import java.util.function.Consumer;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.BuildClasspathResolver;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.GradleDependencyManager;
import teralizer.processing.dependencies.MavenDependencyManager;
import teralizer.util.Configuration;

public class AddDependenciesTask extends AbstractTask {

    public AddDependenciesTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    protected void executeInternal(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        Path projectPath = this.projectRecord.getRootPath();
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Ant projects are not supported yet.");
            case GRADLE:
                new GradleDependencyManager(this.projectRecord, reportInfo).addRequiredDependencies();
                break;
            case MAVEN:
                new MavenDependencyManager(this.projectRecord, reportInfo).addRequiredDependencies();
                break;
            default:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Unsupported project type " + this.projectRecord.getType() + ".");
        }

        // The setup-time classpath was resolved from the original build file, before the tool's
        // dependencies (jqwik, pitest, the junit-platform runner) were added. Refresh it from the
        // custom build file so every downstream consumer (the generated-test validator, the
        // instrumented/generalized builds, Spoon) sees the classpath the pipeline actually builds
        // against, not the stale original.
        this.refreshClasspath(reportInfo);
        this.projectRecord.store();
    }

    private void refreshClasspath(Consumer<String> reportInfo) throws Exception {
        String refreshed;
        switch (this.projectRecord.getType()) {
            case GRADLE:
                refreshed = BuildClasspathResolver.resolveGradle(
                    this.projectRecord.getRootPath(),
                    Configuration.GRADLE_CUSTOM_BUILD_FILE,
                    this.projectRecord.getMainCompiledPath(),
                    this.projectRecord.getTestCompiledPath());
                break;
            case MAVEN:
                refreshed = BuildClasspathResolver.resolveMaven(
                    this.projectRecord.getRootPath(),
                    Configuration.MAVEN_CUSTOM_BUILD_FILE,
                    this.projectRecord.getMainCompiledPath(),
                    this.projectRecord.getTestCompiledPath());
                break;
            default:
                return;
        }
        this.projectRecord.setClasspath(refreshed);
        reportInfo.accept("Refreshed classpath from the custom build file (tool dependencies included).");
    }
}
