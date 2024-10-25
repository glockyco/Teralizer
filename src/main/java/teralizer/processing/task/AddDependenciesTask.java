package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.Dependency;
import teralizer.processing.dependencies.GradleDependencyManager;
import teralizer.processing.dependencies.MavenDependencyManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class AddDependenciesTask extends AbstractTask {

    public static final Dependency JUNIT_4_DEPENDENCY = new Dependency("junit", "junit", "4.12");
    public static final Dependency JUNIT_VINTAGE_DEPENDENCY = new Dependency("org.junit.vintage", "junit-vintage-engine", "5.11.0");
    public static final Dependency PITEST_DEPENDENCY = new Dependency("org.pitest", "pitest-junit5-plugin", "1.2.1");
    public static final Dependency JQWIK_DEPENDENCY = new Dependency("net.jqwik", "jqwik", "1.8.5");

    public static final Path JACOCO_CONFIG_PATH_GRADLE = Paths.get("src/main/resources/jacoco-config-gradle.txt");
    public static final Path JACOCO_CONFIG_PATH_MAVEN = Paths.get("src/main/resources/jacoco-config-maven.txt");
    public static final Path PITEST_CONFIG_PATH_GRADLE = Paths.get("src/main/resources/pitest-config-gradle.txt");
    public static final Path PITEST_CONFIG_PATH_MAVEN = Paths.get("src/main/resources/pitest-config-maven.txt");

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
    }
}
