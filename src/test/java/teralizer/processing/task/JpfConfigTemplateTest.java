package teralizer.processing.task;

import java.io.StringWriter;
import java.util.Properties;
import net.jqwik.api.Example;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import teralizer.util.Configuration;

public class JpfConfigTemplateTest {
    @Example
    void prependsSymbcModelClassesToApplicationClasspath() {
        VelocityEngine velocity = new VelocityEngine(templateProperties());
        velocity.init();

        String sep = ";";  // sentinel: distinct from Unix's default `:`, proving the template uses ${pathSeparator}

        VelocityContext context = baseContext();
        context.put("classpath", "project/target/classes" + sep + "project/target/test-classes");
        context.put("symbolicDp", "z3");
        context.put("symbolicFp", false);
        context.put("symbolicBvLength", 32);

        StringWriter writer = new StringWriter();
        Template template = velocity.getTemplate("jpf-config.vm");
        template.merge(context, writer);

        Assert.assertTrue(writer.toString().contains("classpath=" + Configuration.JPF_SYMBC_MODEL_CLASSPATH + sep + "project/target/classes" + sep + "project/target/test-classes"));
    }

    @Example
    void rendersPerProbeSolverConfigFromContext() {
        VelocityEngine velocity = new VelocityEngine(templateProperties());
        velocity.init();

        VelocityContext context = baseContext();
        context.put("symbolicDp", "z3bitvector");
        context.put("symbolicFp", true);
        context.put("symbolicBvLength", 64);
        context.put("symbolicStrings", true);

        StringWriter writer = new StringWriter();
        Template template = velocity.getTemplate("jpf-config.vm");
        template.merge(context, writer);

        String rendered = writer.toString();
        Assert.assertTrue("dp must come from context", rendered.contains("symbolic.dp=z3bitvector"));
        Assert.assertTrue("fp must come from context", rendered.contains("symbolic.fp=true"));
        Assert.assertTrue("bvlength must come from context", rendered.contains("symbolic.bvlength=64"));
        Assert.assertTrue("strings must come from context", rendered.contains("symbolic.strings=true"));
        Assert.assertFalse("dp must not be hardcoded to z3", rendered.contains("symbolic.dp=z3\n"));
    }

    private static VelocityContext baseContext() {
        VelocityContext context = new VelocityContext();
        context.put("driverClassQualifiedName", "example.Driver");
        context.put("symbolicMethod", "example.Test.value(sym)");
        context.put("jpfSymbcModelClasspath", Configuration.JPF_SYMBC_MODEL_CLASSPATH);
        context.put("pathSeparator", ";");
        context.put("classpath", "project/target/classes");
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
        context.put("symbolicStrings", false);
        return context;
    }

    private static Properties templateProperties() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");  // match production (TestGeneralizationRunner)
        return properties;
    }
}
