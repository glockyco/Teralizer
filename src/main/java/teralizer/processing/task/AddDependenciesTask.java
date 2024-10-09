package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.Dependency;
import teralizer.processing.dependencies.GradleDependencyManager;
import teralizer.processing.dependencies.MavenDependencyManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

public class AddDependenciesTask implements Task {

    public static final Dependency JUNIT_VINTAGE_DEPENDENCY = new Dependency("org.junit.vintage", "junit-vintage-engine", "5.11.0");
    public static final Dependency PITEST_DEPENDENCY = new Dependency("org.pitest", "pitest-junit5-plugin", "1.2.1");
    public static final Dependency JQWIK_DEPENDENCY = new Dependency("net.jqwik", "jqwik", "1.8.5");

    public static final Path PITEST_CONFIG_PATH_GRADLE = Paths.get("src/main/resources/pitest-config-gradle.txt");
    public static final Path PITEST_CONFIG_PATH_MAVEN = Paths.get("src/main/resources/pitest-config-maven.txt");

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public AddDependenciesTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        this.addDependencies(this.projectRecord, reportInfo);
    }

    private void addDependencies(ProjectRecord projectRecord, Consumer<String> reportInfo) throws Exception {
        Path projectPath = projectRecord.getRootPath();
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Ant projects are not supported yet.");
            case GRADLE:
                new GradleDependencyManager(
                    projectRecord.getRootPath(),
                    projectRecord.getTestFramework(),
                    reportInfo
                ).addRequiredDependencies();
                break;
            case MAVEN:
                new MavenDependencyManager(
                    projectRecord.getRootPath(),
                    projectRecord.getTestFramework(),
                    reportInfo
                ).addRequiredDependencies();
                break;
            default:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Unsupported project type " + projectRecord.getType() + ".");
        }
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
        return "AddDependenciesTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddDependenciesTask)) return false;
        AddDependenciesTask that = (AddDependenciesTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
