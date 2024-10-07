package teralizer.processing.task;

import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TaskContext;
import teralizer.processing.TestFramework;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AddDependenciesTask implements Task {

    private static final Dependency VINTAGE_DEPENDENCY = new Dependency("org.junit.vintage", "junit-vintage-engine", "5.11.0");
    private static final Dependency PITEST_DEPENDENCY = new Dependency("org.pitest", "pitest-junit5-plugin", "1.2.1");
    private static final Dependency JQWIK_DEPENDENCY = new Dependency("net.jqwik", "jqwik", "1.8.5");

    private static final Path PITEST_CONFIG_PATH_GRADLE = Paths.get("src/main/resources/pitest-config-gradle.txt");
    private static final Path PITEST_CONFIG_PATH_MAVEN = Paths.get("src/main/resources/pitest-config-maven.txt");

    private static class Dependency {
        public final String groupId;
        public final String artifactId;
        public final String version;

        public Dependency(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        @Override
        public String toString() {
            return this.groupId + ":" + this.artifactId + ":" + this.version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Dependency)) return false;
            Dependency that = (Dependency) o;
            // Only the groupId and the artifactId must match. We do not care about the version.
            return Objects.equals(this.groupId, that.groupId) && Objects.equals(this.artifactId, that.artifactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.groupId, this.artifactId);
        }
    }

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
        Path projectPath = this.projectRecord.getRootPath();

        Set<Dependency> requiredDependencies = new HashSet<>();
        switch (this.projectRecord.getTestFramework()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Test framework could not be identified.");
            case JUNIT_4:
                requiredDependencies.add(VINTAGE_DEPENDENCY);
                requiredDependencies.add(PITEST_DEPENDENCY);
                requiredDependencies.add(JQWIK_DEPENDENCY);
                break;
            case JUNIT_5:
                requiredDependencies.add(PITEST_DEPENDENCY);
                requiredDependencies.add(JQWIK_DEPENDENCY);
                break;
            default:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Unsupported test framework " + projectRecord.getTestFramework() + ".");
        }

        switch (this.projectRecord.getType()) {
            case UNKNOWN:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". No pom.xml / build.gradle found.");
            case JAIGANTIC:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". JAigantic projects are not supported yet.");
            case ANT:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Ant projects are not supported yet.");
            case GRADLE:
                this.addDependenciesToGradleProject(this.projectRecord, requiredDependencies, reportInfo);
                break;
            case MAVEN:
                this.addDependenciesToMavenProject(this.projectRecord, requiredDependencies, reportInfo);
                break;
            default:
                throw new RuntimeException("Cannot add dependencies to project " + projectPath + ". Unsupported project type " + projectRecord.getType() + ".");
        }
    }

    private void addDependenciesToGradleProject(ProjectRecord projectRecord, Set<Dependency> requiredDependencies, Consumer<String> reportInfo) throws IOException {
        // Check if the required dependencies already exist:
        Set<Dependency> identifiedDependencies = new HashSet<>();

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectRecord.getRootPath().toFile());

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject projectModel = modelBuilder.get();

            for (EclipseExternalDependency dependency: projectModel.getClasspath()) {
                GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
                Dependency identifiedDependency = new Dependency(moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
                if (requiredDependencies.contains(identifiedDependency)) {
                    identifiedDependencies.add(identifiedDependency);
                }
            }
        }

        // Add the dependencies that do not exist yet:
        Set<Dependency> addedDependencies = new HashSet<>(requiredDependencies);
        addedDependencies.removeAll(identifiedDependencies);

        if (!addedDependencies.isEmpty()) {
            Path buildFilePath = projectRecord.getRootPath().resolve("build.gradle");
            StringBuilder content = new StringBuilder(new String(Files.readAllBytes(buildFilePath)));

            content.append(String.format("\n// Added by %s - START.", TestGeneralizationRunner.TOOL_NAME));

            if (projectRecord.getTestFramework() == TestFramework.JUNIT_4) {
                content.append("\ntest { useJUnitPlatform() }");
            }

            for (Dependency addedDependency: addedDependencies) {
                content.append(String.format(
                    "\ndependencies { testImplementation '%s:%s:%s' }",
                    addedDependency.groupId,
                    addedDependency.artifactId,
                    addedDependency.version
                ));

                if (addedDependency == PITEST_DEPENDENCY) {
                    // We assume that no PIT configuration exists if the pitest-junit5-plugin is missing. This is not
                    // necessarily true for JUnit 4 projects, but probably "good enough" for our purposes considering
                    // how rarely PIT is used in the first place.
                    // @TODO: Check whether a PIT configuration exists before adding it to build.gradle.
                    content.append("\n").append(new String(Files.readAllBytes(PITEST_CONFIG_PATH_GRADLE)));
                }
            }

            content.append(String.format("\n// Added by %s - END.\n", TestGeneralizationRunner.TOOL_NAME));

            Files.write(buildFilePath, content.toString().getBytes());
        }

        this.reportDependencyInfo(requiredDependencies, identifiedDependencies, addedDependencies, reportInfo);
    }

    private void addDependenciesToMavenProject(ProjectRecord projectRecord, Set<Dependency> requiredDependencies, Consumer<String> reportInfo) throws IOException, DocumentException {
        Path pomFilePath = projectRecord.getRootPath().resolve("pom.xml");

        SAXReader reader = new SAXReader();
        Document document = reader.read(pomFilePath.toFile());

        Element root = document.getRootElement();
        Element dependencies = root.element("dependencies");

        // Check if the required dependencies already exist:
        Set<Dependency> identifiedDependencies = new HashSet<>();

        if (dependencies != null) {
            for (Iterator<Element> it = dependencies.elementIterator(); it.hasNext(); ) {
                Element dependency = it.next();
                String groupId = dependency.element("groupId").getText();
                String artifactId = dependency.element("artifactId").getText();
                String version = dependency.element("version").getText();

                Dependency identifiedDependency = new Dependency(groupId, artifactId, version);
                if (requiredDependencies.contains(identifiedDependency)) {
                    identifiedDependencies.add(identifiedDependency);
                }
            }
        } else {
            dependencies = root.addElement("dependencies");
        }

        // Add the dependencies that do not exist yet:
        Set<Dependency> addedDependencies = new HashSet<>(requiredDependencies);
        addedDependencies.removeAll(identifiedDependencies);

        boolean hasModifiedDocument = false;
        if (!addedDependencies.isEmpty()) {
            for (Dependency addedDependency: addedDependencies) {
                Element dependency = dependencies.addElement("dependency");
                dependency.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
                dependency.addElement("groupId").addText(addedDependency.groupId);
                dependency.addElement("artifactId").addText(addedDependency.artifactId);
                dependency.addElement("version").addText(addedDependency.version);
                dependency.addElement("scope").addText("test");
            }
            hasModifiedDocument = true;
        }

        // Add the PIT configuration if it does not exist yet:
        XPath xpath = DocumentHelper.createXPath("/m:project/m:build/m:plugins/m:plugin[m:groupId='org.pitest' and m:artifactId='pitest-maven']");
        Map<String, String> namespaceURIs = new HashMap<>();
        namespaceURIs.put("m", "http://maven.apache.org/POM/4.0.0");
        xpath.setNamespaceURIs(namespaceURIs);
        List<Node> nodes = xpath.selectNodes(document);

        if (nodes.isEmpty()) {
            Element buildElement = root.element("build");
            if (buildElement == null) {
                buildElement = root.addElement("build");
                buildElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            }

            Element pluginsElement = buildElement.element("plugins");
            if (pluginsElement == null) {
                pluginsElement = buildElement.addElement("plugins");
                pluginsElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            }

            Document pitestConfigDocument = reader.read(PITEST_CONFIG_PATH_MAVEN.toFile());
            Element pluginElement = pitestConfigDocument.getRootElement();
            pluginElement.addComment("Added by " + TestGeneralizationRunner.TOOL_NAME + ".");
            pluginsElement.add(pluginElement.detach());

            hasModifiedDocument = true;
        }

        // Write the changes to the pom.xml file:
        if (hasModifiedDocument) {
            XMLWriter writer = new XMLWriter(new FileWriter(pomFilePath.toFile()), OutputFormat.createPrettyPrint());
            writer.write(document);
            writer.close();
        }

        this.reportDependencyInfo(requiredDependencies, identifiedDependencies, addedDependencies, reportInfo);
    }

    private void reportDependencyInfo(
        Set<Dependency> requiredDependencies,
        Set<Dependency> identifiedDependencies,
        Set<Dependency> addedDependencies,
        Consumer<String> reportInfo
    ) {
        String requiredString = requiredDependencies.stream().map(Dependency::toString).collect(Collectors.joining("\n"));
        String identifiedString = identifiedDependencies.stream().map(Dependency::toString).collect(Collectors.joining("\n"));
        String addedString = addedDependencies.stream().map(Dependency::toString).collect(Collectors.joining("\n"));

        StringBuilder info = new StringBuilder();
        info.append("Required:\n");
        info.append(requiredString.isEmpty() ? "none" : requiredString);
        info.append("\n\nIdentified:\n");
        info.append(identifiedString.isEmpty() ? "none" : identifiedString);
        info.append("\n\nAdded:\n");
        info.append(addedString.isEmpty() ? "none" : addedString);

        reportInfo.accept(info.toString());
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
