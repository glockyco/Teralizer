package teralizer.processing.task;

import java.io.StringWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import net.jqwik.api.Example;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class DriverClassTemplateTest {

    @Example
    void rendersTeardownAfterTestInvocation() {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(
            "package smoke; public class SubjectTest {"
                + " public void testProperty() {}"
                + " public void tearDown() {}"
                + "}",
            "SubjectTest.java"
        ));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        Set<CtMethod<?>> afterMethods = new LinkedHashSet<>(testClass.getMethodsByName("tearDown"));

        VelocityEngine velocity = new VelocityEngine(templateProperties());
        velocity.init();
        VelocityContext context = new VelocityContext();
        context.put("driverPackageName", "smoke");
        context.put("driverClassName", "Driver");
        context.put("instrumentedClassQualifiedName", "smoke.SubjectTest");
        context.put("instrumentedClassName", "SubjectTest");
        context.put("testMethodName", "testProperty");
        context.put("beforeMethods", Collections.emptySet());
        context.put("afterMethods", afterMethods);

        StringWriter writer = new StringWriter();
        Template template = velocity.getTemplate("driver-class.vm");
        template.merge(context, writer);
        String rendered = writer.toString();

        Assert.assertTrue(rendered.indexOf("instance.testProperty();") < rendered.indexOf("instance.tearDown();"));
    }

    private static Properties templateProperties() {
        Properties properties = new Properties();
        properties.setProperty("resource.loader", "file");
        properties.setProperty("file.resource.loader.path", "src/main/resources/templates");
        properties.setProperty("runtime.references.strict", "true");
        return properties;
    }
}
