package teralizer.processing.dependencies;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.TestFramework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static teralizer.processing.task.AddDependenciesTask.*;

public class GradleDependencyManager {

    private static final String TOOL_COMMENT_START = String.format("// Added by %s - START.", TestGeneralizationRunner.TOOL_NAME);
    private static final String TOOL_COMMENT_END = String.format("// Added by %s - END.", TestGeneralizationRunner.TOOL_NAME);

    private final Path buildFilePath;
    private final StringBuilder buildFileContent;

    private final Set<Dependency> dependencies;
    private final Set<String> tasks;
    private final TestFramework testFramework;
    private final Consumer<String> reportInfo;

    public GradleDependencyManager(Path projectPath, TestFramework testFramework, Consumer<String> reportInfo) throws IOException {
        this.buildFilePath = projectPath.resolve("build.gradle");
        this.buildFileContent = new StringBuilder(new String(Files.readAllBytes(this.buildFilePath)));

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(projectPath.toFile());
        this.dependencies = this.getDependencies(connector);
        this.tasks = this.getTasks(connector);

        this.testFramework = testFramework;
        this.reportInfo = reportInfo;
    }

    public void addRequiredDependencies() throws IOException {
        boolean hasModifiedDocument = false;
        if (this.testFramework == TestFramework.JUNIT_4) {
            if (this.buildFileContent.toString().contains("useJUnitPlatform")) {
                this.reportInfo.accept("Found: useJUnitPlatform()");
            } else {
                this.appendToBuildFile("test { useJUnitPlatform() }");
                this.reportInfo.accept("Added: useJUnitPlatform()");
                hasModifiedDocument = true;
            }
            this.addDependency(JUNIT_VINTAGE_DEPENDENCY);
        }
        hasModifiedDocument = hasModifiedDocument || this.addDependency(PITEST_DEPENDENCY);
        hasModifiedDocument = hasModifiedDocument || this.addPitestPlugin();
        hasModifiedDocument = hasModifiedDocument || this.addDependency(JQWIK_DEPENDENCY);

        if (hasModifiedDocument) {
            Files.write(this.buildFilePath, this.buildFileContent.toString().getBytes());
        }
    }

    private Set<Dependency> getDependencies(GradleConnector connector) {
        Set<Dependency> dependencies = new HashSet<>();
        try (ProjectConnection connection = connector.connect()) {
            EclipseProject projectModel = connection.getModel(EclipseProject.class);
            for (EclipseExternalDependency dependency : projectModel.getClasspath()) {
                GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
                String groupId = moduleVersion.getGroup();
                String artifactId = moduleVersion.getName();
                String version = moduleVersion.getVersion();
                dependencies.add(new Dependency(groupId, artifactId, version));
            }
        }
        return dependencies;
    }

    private Set<String> getTasks(GradleConnector connector) {
        Set<String> tasks = new HashSet<>();
        try (ProjectConnection connection = connector.connect()) {
            GradleProject project = connection.getModel(GradleProject.class);
            for (Task task : project.getTasks()) {
                tasks.add(task.getName());
            }
        }
        return tasks;
    }

    private boolean addDependency(Dependency dependency) {
        for (Dependency identifiedDependency : this.dependencies) {
            if (identifiedDependency.equals(dependency)) {
                this.reportInfo.accept("Found dependency: " + identifiedDependency);
                return false;
            }
        }
        this.appendToBuildFile(String.format(
            "dependencies { testImplementation '%s:%s:%s' }",
            dependency.groupId,
            dependency.artifactId,
            dependency.version
        ));
        this.reportInfo.accept("Added dependency: " + dependency);
        return true;
    }

    private boolean addPitestPlugin() throws IOException {
        if (this.tasks.contains("pitest")) {
            this.reportInfo.accept("Found plugin / config: pitest");
            return false;
        }
        this.prependToBuildFile("plugins { id 'info.solidsoft.pitest' version '1.15.0' }");
        this.appendToBuildFile(new String(Files.readAllBytes(PITEST_CONFIG_PATH_GRADLE)));
        this.reportInfo.accept("Added plugin / config: pitest");
        return true;
    }

    private void prependToBuildFile(String content) {
        this.buildFileContent.insert(0, TOOL_COMMENT_END + "\n\n");
        this.buildFileContent.insert(0, content + "\n");
        this.buildFileContent.insert(0, TOOL_COMMENT_START + "\n");
    }

    private void appendToBuildFile(String content) {
        this.buildFileContent.append(TOOL_COMMENT_START).append("\n");
        this.buildFileContent.append(content).append("\n");
        this.buildFileContent.append(TOOL_COMMENT_END).append("\n\n");
    }
}
