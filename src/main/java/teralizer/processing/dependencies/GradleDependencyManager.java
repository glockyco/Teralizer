package teralizer.processing.dependencies;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import teralizer.TestGeneralizationRunner;
import teralizer.processing.TestFramework;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static teralizer.processing.task.AddDependenciesTask.PITEST_CONFIG_PATH_GRADLE;
import static teralizer.processing.task.AddDependenciesTask.PITEST_DEPENDENCY;

public class GradleDependencyManager {

    private final Path projectPath;
    private final TestFramework testFramework;

    public GradleDependencyManager(Path projectPath, TestFramework testFramework) {
        this.projectPath = projectPath;
        this.testFramework = testFramework;
    }

    public Set<Dependency> detectInProject(Set<Dependency> requiredDependencies) {
        Set<Dependency> identifiedDependencies = new HashSet<>();

        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(this.projectPath.toFile());

        try (ProjectConnection connection = connector.connect()) {
            ModelBuilder<EclipseProject> modelBuilder = connection.model(EclipseProject.class);
            EclipseProject projectModel = modelBuilder.get();

            for (EclipseExternalDependency dependency : projectModel.getClasspath()) {
                GradleModuleVersion moduleVersion = dependency.getGradleModuleVersion();
                Dependency identifiedDependency = new Dependency(moduleVersion.getGroup(), moduleVersion.getName(), moduleVersion.getVersion());
                if (requiredDependencies.contains(identifiedDependency)) {
                    identifiedDependencies.add(identifiedDependency);
                }
            }
        }

        return identifiedDependencies;
    }

    public void addToProject(Set<Dependency> missingDependencies) throws IOException {
        if (missingDependencies.isEmpty()) {
            return;
        }

        Path buildFilePath = this.projectPath.resolve("build.gradle");
        StringBuilder content = new StringBuilder(new String(Files.readAllBytes(buildFilePath)));

        content.append(String.format("\n// Added by %s - START.", TestGeneralizationRunner.TOOL_NAME));

        if (this.testFramework == TestFramework.JUNIT_4) {
            content.append("\ntest { useJUnitPlatform() }");
        }

        for (Dependency missingDependency : missingDependencies) {
            content.append(String.format(
                "\ndependencies { testImplementation '%s:%s:%s' }",
                missingDependency.groupId,
                missingDependency.artifactId,
                missingDependency.version
            ));

            if (missingDependency == PITEST_DEPENDENCY) {
                // We assume that no PIT plugin / configuration exists if the pitest-junit5-plugin is missing.
                // This is not necessarily true but probably "good enough" for our purposes considering how rarely
                // PIT is used in the first place.
                // @TODO: Check whether a PIT plugin / configuration exists before adding it to build.gradle.
                content.insert(0, String.format("// Added by %s - END.\n\n", TestGeneralizationRunner.TOOL_NAME));
                content.insert(0, "plugins { id 'info.solidsoft.pitest' version '1.15.0' }\n");
                content.insert(0, String.format("// Added by %s - START.\n", TestGeneralizationRunner.TOOL_NAME));

                content.append("\n").append(new String(Files.readAllBytes(PITEST_CONFIG_PATH_GRADLE)));
            }
        }

        content.append(String.format("\n// Added by %s - END.\n", TestGeneralizationRunner.TOOL_NAME));

        Files.write(buildFilePath, content.toString().getBytes());
    }
}
