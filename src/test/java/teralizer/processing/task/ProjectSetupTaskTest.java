package teralizer.processing.task;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.ProjectRecord;
import org.junit.Assert;
import teralizer.processing.ProcessingStage;
import teralizer.processing.TestFramework;

public class ProjectSetupTaskTest {

    @Example
    void identifiesJunit3FromClasspathVersion() {
        ProjectRecord project = project("/cache/junit-3.8.2.jar");

        setup(project);

        Assert.assertEquals(TestFramework.JUNIT_3, project.getTestFramework());
        Assert.assertEquals("3.8.2", project.getTestFrameworkVersion());
    }

    @Example
    void identifiesJunit4FromClasspathVersion() {
        ProjectRecord project = project("/cache/junit-4.13.1.jar");

        setup(project);

        Assert.assertEquals(TestFramework.JUNIT_4, project.getTestFramework());
        Assert.assertEquals("4.13.1", project.getTestFrameworkVersion());
    }

    private static void setup(ProjectRecord project) {
        new ProjectSetupTask(ProcessingStage.SETUP_PROJECT, project, false).setupTestFramework(project);
    }

    private static ProjectRecord project(String classpath) {
        ProjectRecord project = new ProjectRecord();
        project.setClasspath(classpath);
        return project;
    }
}
