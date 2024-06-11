package teralizer.tasks;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.jooq.DSLContext;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.generated.tables.records.TestRecord;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class JpfInstrumentationTask {

    private final Task task = Task.JPF_INSTRUMENTATION;

    private final VelocityEngine velocityEngine;

    public JpfInstrumentationTask(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }

    public void run(DSLContext create, ProjectRecord projectRecord, TestRecord testRecord) throws IOException {
        // @TODO: We might have to make all existing test classes and methods public.
        //   Otherwise (if the tests have any non-public visibility),
        //   we cannot have the created driver classes in a separate namespace.

        this.createDriverClassFile(testRecord);
        this.createJpfConfigFile(projectRecord, testRecord);
    }

    private void createDriverClassFile(TestRecord testRecord) throws IOException {
        VelocityContext context = new VelocityContext();
        context.put("driverPackageName", testRecord.getDriverClassPackage());
        context.put("driverClassName", testRecord.getDriverClassName());
        context.put("testClassQualifiedName", testRecord.getTestedClassPackage() + "." + testRecord.getTestClassName());
        context.put("testClassName", testRecord.getTestClassName());
        context.put("testMethodName", testRecord.getTestMethodName());

        File driverClassFile = new File(testRecord.getDriverClassPath());
        driverClassFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(driverClassFile)) {
            Template template = velocityEngine.getTemplate("driver-class.vm");
            template.merge(context, fileWriter);
        }
    }

    private void createJpfConfigFile(ProjectRecord projectRecord, TestRecord testRecord) throws IOException {
        String driverClassQualifiedName = testRecord.getDriverClassPackage() + "." + testRecord.getDriverClassName();
        String testClassQualifiedName = testRecord.getTestClassPackage() + "." + testRecord.getTestClassName();
        String testMethodQualifiedName = testClassQualifiedName + "." + testRecord.getTestMethodName();
        String testedClassQualifiedName = testRecord.getTestedClassPackage() + "." + testRecord.getTestedClassName();
        // @TODO: Include method parameter types in the qualified name of the tested method.
        String testedMethodQualifiedName = testedClassQualifiedName + "." + testRecord.getTestedMethodName();

        VelocityContext context = new VelocityContext();
        context.put("classpath", projectRecord.getClasspath());

        context.put("driverClassQualifiedName", driverClassQualifiedName);
        context.put("testClassQualifiedName", testClassQualifiedName);
        context.put("testMethodQualifiedName", testMethodQualifiedName);
        context.put("testedClassQualifiedName", testedClassQualifiedName);
        context.put("testedMethodQualifiedName", testedMethodQualifiedName);
        context.put("inputSpecificationPath", testRecord.getInputSpecificationPath());
        context.put("outputSpecificationPath", testRecord.getOutputSpecificationPath());

        File jpfConfigFile = new File(testRecord.getJpfConfigPath());
        jpfConfigFile.getParentFile().mkdirs();

        try (FileWriter fileWriter = new FileWriter(jpfConfigFile)) {
            // @TODO: Add execution of @BeforeAll, @Before, @After, @AfterAll to the template.
            // @TODO: How to handle methods (without parameters) that depend on object state?
            Template template = this.velocityEngine.getTemplate("jpf-config.vm");
            template.merge(context, fileWriter);
        }
    }
}
