package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.dependencies.GradleDependencyManager;
import teralizer.processing.dependencies.MavenDependencyManager;

import java.nio.file.Path;
import java.util.function.Consumer;

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
    }
}
