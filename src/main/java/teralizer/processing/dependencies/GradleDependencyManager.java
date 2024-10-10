package teralizer.processing.dependencies;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.jooq.generated.tables.records.ProjectRecord;
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

    private final ProjectRecord projectRecord;
    private final Consumer<String> reportInfo;

    private final Path buildFilePath;
    private final StringBuilder buildFileContent;

    private final Set<Dependency> dependencies;
    private final Set<String> tasks;

    public GradleDependencyManager(ProjectRecord projectRecord, Consumer<String> reportInfo) throws IOException {
        this.projectRecord = projectRecord;
        this.reportInfo = reportInfo;

        this.buildFilePath = this.projectRecord.getRootPath().resolve("build.gradle");
        this.buildFileContent = new StringBuilder(new String(Files.readAllBytes(this.buildFilePath)));

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(this.projectRecord.getRootPath().toFile());
        this.dependencies = this.getDependencies(connector);
        this.tasks = this.getTasks(connector);

    }

    public void addRequiredDependencies() throws IOException {
        boolean hasModifiedDocument = false;
        if (this.projectRecord.getTestFramework() == TestFramework.JUNIT_4) {
            // Deliberately using non-short-circuiting OR here. If multiple
            // dependencies are missing, we want to add all of them.
            hasModifiedDocument |= this.addUseJunitPlatform();
            hasModifiedDocument |= this.addJUnitIfOutdated();
            hasModifiedDocument |= this.addDependencyIfMissing(JUNIT_VINTAGE_DEPENDENCY);
        }
        hasModifiedDocument |= this.addJacocoPlugin();
        hasModifiedDocument |= this.addDependencyIfMissing(PITEST_DEPENDENCY);
        hasModifiedDocument |= this.addPitestPlugin();
        hasModifiedDocument |= this.addDependencyIfMissing(JQWIK_DEPENDENCY);

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

    private boolean addUseJunitPlatform() {
        if (this.buildFileContent.toString().contains("useJUnitPlatform")) {
            this.reportInfo.accept("Found: useJUnitPlatform()");
            return false;
        }
        this.appendToBuildFile("test { useJUnitPlatform() }");
        this.reportInfo.accept("Added: useJUnitPlatform()");
        return true;
    }

    private boolean addJUnitIfOutdated() {
        String testFrameworkVersion = this.projectRecord.getTestFrameworkVersion();
        // Check whether a recent enough version of JUnit 4 is used (JUnit
        // Vintage requires at least JUnit 4.12). Not a very clean solution,
        // but good enough for our purposes.
        for (int i = 12; i < 20; i++) {
            if (testFrameworkVersion.startsWith("4." + i)) {
                return false;
            }
        }
        // If the detected JUnit version is not supported, we just add a one
        // that is in addition to the one that is already present. This is easy
        // to do, but is not necessarily guaranteed to convince Maven that the
        // newly added version should be used over the existing one.
        // @TODO: Update the existing JUnit version instead of adding a new one.
        this.addDependencyIfMissing(JUNIT_4_DEPENDENCY);
        return true;
    }

    private boolean addDependencyIfMissing(Dependency dependency) {
        for (Dependency identifiedDependency : this.dependencies) {
            if (identifiedDependency.equals(dependency)) {
                this.reportInfo.accept("Found dependency: " + identifiedDependency);
                return false;
            }
        }
        this.addDependency(dependency);
        this.reportInfo.accept("Added dependency: " + dependency);
        return true;
    }

    private void addDependency(Dependency dependency) {
        this.appendToBuildFile(String.format(
            "dependencies { testImplementation '%s:%s:%s' }",
            dependency.groupId,
            dependency.artifactId,
            dependency.version
        ));
    }

    private boolean addJacocoPlugin() throws IOException {
        if (this.tasks.contains("jacocoTestReport")) {
            this.reportInfo.accept("Found plugin / config: jacocoTestReport");
            return false;
        }
        this.prependToBuildFile("plugins { id 'jacoco' }");
        this.appendToBuildFile(new String(Files.readAllBytes(JACOCO_CONFIG_PATH_GRADLE)));
        this.reportInfo.accept("Added plugin / config: jacocoTestReport");
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
        this.buildFileContent.insert(0, TOOL_COMMENT_END + "\n");
        this.buildFileContent.insert(0, content + "\n");
        this.buildFileContent.insert(0, "+\n" + TOOL_COMMENT_START + "\n");
    }

    private void appendToBuildFile(String content) {
        this.buildFileContent.append("\n").append(TOOL_COMMENT_START).append("\n");
        this.buildFileContent.append(content).append("\n");
        this.buildFileContent.append(TOOL_COMMENT_END).append("\n");
    }
}
