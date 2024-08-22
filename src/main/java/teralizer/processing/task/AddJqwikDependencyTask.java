package teralizer.processing.task;

import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class AddJqwikDependencyTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTask.class);

    private static final String JQWIK_VERSION = "1.8.5";
    private static final String DEPENDENCY_STRING = "\ndependencies { testImplementation \"net.jqwik:jqwik:" + JQWIK_VERSION + "\" } // Added by " + TestGeneralizationRunner.TOOL_NAME + ".\n";

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public AddJqwikDependencyTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        this.addJqwikDependency(this.projectRecord);

        scheduleTask.accept(new ProjectBuildTask(ProcessingStage.PROJECT_BUILDING_JQWIK, this.projectRecord));
        scheduleTask.accept(new TestDetectionTask(ProcessingStage.TEST_DETECTION, this.projectRecord));
    }

    private void addJqwikDependency(ProjectRecord projectRecord) throws IOException {
        Path projectPath = this.projectRecord.getRootPath();
        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot add jqwik dependency to project " + projectPath + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot add jqwik dependency to project " + projectPath + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot add jqwik dependency to project " + projectPath + ". Ant projects are not supported yet.");
            case GRADLE:
                this.addJqwikDependencyToGradle(projectPath);
                break;
            case MAVEN:
                throw new RuntimeException("Cannot build project " + projectPath + ". Ant projects are not supported yet.");
            default:
                throw new RuntimeException("Cannot add jqwik dependency to project " + projectPath + ". Unsupported project type " + projectRecord.getType() + ".");
        }
    }

    private void addJqwikDependencyToGradle(Path projectPath) throws IOException {
        Path buildFilePath = projectPath.resolve("build.gradle");
        String content = new String(Files.readAllBytes(buildFilePath));

        if (content.contains(DEPENDENCY_STRING)) {
            LOGGER.atWarn().log("No dependencies to add for project {}. jqwik is already listed as a dependency.", projectPath);
        } else {
            content += DEPENDENCY_STRING;
            Files.write(buildFilePath, content.getBytes());
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
}
