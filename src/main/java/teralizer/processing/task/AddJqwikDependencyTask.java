package teralizer.processing.task;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.jooq.generated.tables.records.ProjectRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;

public class AddJqwikDependencyTask implements Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTask.class);

    private static final String JQWIK_GROUP_ID = "net.jqwik";
    private static final String JQWIK_ARTIFACT_ID = "jqwik";
    private static final String JQWIK_VERSION = "1.8.5";
    private static final String DEPENDENCY_STRING = "\ndependencies { testImplementation \"" + JQWIK_GROUP_ID + ":" + JQWIK_ARTIFACT_ID + ":" + JQWIK_VERSION + "\" } // Added by " + TestGeneralizationRunner.TOOL_NAME + ".\n";

    private final ProcessingStage stage;
    private final ProjectRecord projectRecord;

    public AddJqwikDependencyTask(ProcessingStage stage, ProjectRecord projectRecord) {
        this.stage = stage;
        this.projectRecord = projectRecord;
    }

    @Override
    public void execute(TaskContext context, Consumer<String> reportInfo, Consumer<Task> scheduleTask) throws Exception {
        this.addJqwikDependency(this.projectRecord);
    }

    private void addJqwikDependency(ProjectRecord projectRecord) throws Exception {
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
                this.addJqwikDependencyToMaven(projectPath);
                break;
            default:
                throw new RuntimeException("Cannot add jqwik dependency to project " + projectPath + ". Unsupported project type " + projectRecord.getType() + ".");
        }
    }

    private void addJqwikDependencyToGradle(Path projectPath) throws IOException {
        Path buildFilePath = projectPath.resolve("build.gradle");
        String content = new String(Files.readAllBytes(buildFilePath));

        // @TODO: Check whether ANY jqwik dependency is already present in the build.gradle file.
        //   Currently, we are only really checking whether a Teralizer-added jqwik dependency exists
        //   (it needs to match not only the jqwik dependency string, but also the "Added by Teralizer." comment).
        //   Ideally, the detection should be version- and comment-agnostic, work with long and short dependency declaration styles, etc.
        if (content.contains(DEPENDENCY_STRING)) {
            LOGGER.atWarn().log("No dependencies to add for project {}. jqwik is already listed as a dependency.", projectPath);
        } else {
            content += DEPENDENCY_STRING;
            Files.write(buildFilePath, content.getBytes());
        }
    }

    private void addJqwikDependencyToMaven(Path projectPath) throws IOException, DocumentException {
        Path pomFilePath = projectPath.resolve("pom.xml");

        SAXReader reader = new SAXReader();
        Document document = reader.read(pomFilePath.toFile());

        Element root = document.getRootElement();
        Element dependencies = root.element("dependencies");

        if (dependencies != null) {
            for (Iterator<Element> it = dependencies.elementIterator(); it.hasNext(); ) {
                Element dependency = it.next();
                Element groupId = dependency.element("groupId");
                Element artifactId = dependency.element("artifactId");

                if (groupId.getText().equals(JQWIK_GROUP_ID) && artifactId.getText().equals(JQWIK_ARTIFACT_ID)) {
                    LOGGER.atWarn().log("No dependencies to add for project {}. jqwik is already listed as a dependency.", projectPath);
                    return;
                }
            }
        } else {
            dependencies = root.addElement("dependencies");
        }

        Element jqwikDependency = dependencies.addElement("dependency");
        jqwikDependency.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
        jqwikDependency.addElement("groupId").addText(JQWIK_GROUP_ID);
        jqwikDependency.addElement("artifactId").addText(JQWIK_ARTIFACT_ID);
        jqwikDependency.addElement("version").addText(JQWIK_VERSION);

        XMLWriter writer = new XMLWriter(new FileWriter(pomFilePath.toFile()), OutputFormat.createPrettyPrint());
        writer.write(document);
        writer.close();
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
        return "AddJqwikDependencyTask{" +
            "stage=" + this.stage.getStep() +
            ", projectRecord=" + this.projectRecord.getId() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddJqwikDependencyTask)) return false;
        AddJqwikDependencyTask that = (AddJqwikDependencyTask) o;
        return this.stage == that.stage && Objects.equals(this.projectRecord.getId(), that.projectRecord.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.stage, this.projectRecord.getId());
    }
}
