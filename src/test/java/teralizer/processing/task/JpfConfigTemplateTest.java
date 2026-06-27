package teralizer.processing.task;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import net.jqwik.api.Example;
import org.junit.Assert;

import java.io.StringWriter;
import java.util.Properties;

public class JpfConfigTemplateTest {
    @Example
    void prependsSymbcModelClassesToApplicationClasspath() {
        VelocityEngine velocity = new VelocityEngine(templateProperties());
        velocity.init();

        VelocityContext context = new VelocityContext();
        context.put("driverClassQualifiedName", "example.Driver");
        context.put("symbolicMethod", "example.Test.value(sym)");
        context.put("jpfSymbcModelClasspath", "${jpf-symbc}/build/classes");
        context.put("classpath", "project/target/classes:project/target/test-classes");
        context.put("maxExecutionTime", 30.0);
        context.put("maxPathConditionSize", 100000L);
        context.put("testClassQualifiedName", "example.Test");
        context.put("testMethodQualifiedName", "example.Test.testValue");
        context.put("testedClassQualifiedName", "example.Subject");
        context.put("testedMethodQualifiedName", "example.Subject.value");
        context.put("instrumentedClassQualifiedName", "example.InstrumentedTest");
        context.put("instrumentedMethodQualifiedName", "example.InstrumentedTest.value");
        context.put("inputValuesPath", "input-values.json");
        context.put("outputValuePath", "output-value.json");
        context.put("inputSpecificationPath", "input-spec.json");
        context.put("outputSpecificationPath", "output-spec.json");
        context.put("reportPath", "report.txt");

        StringWriter writer = new StringWriter();
        Template template = velocity.getTemplate("jpf-config.vm");
        template.merge(context, writer);

        Assert.assertTrue(writer.toString().contains("classpath=${jpf-symbc}/build/classes:project/target/classes:project/target/test-classes"));
    }

    private static Properties templateProperties() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        return properties;
    }
}
