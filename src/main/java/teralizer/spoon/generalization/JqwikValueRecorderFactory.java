package teralizer.spoon.generalization;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

import java.io.StringWriter;
import java.nio.file.Path;

public class JqwikValueRecorderFactory {

    static final String TEMPLATE_NAME = "jqwik-value-recorder.vm";

    public static CtClass<?> createRecorderClass(VelocityEngine velocityEngine, Path valueLogPath) {
        return Launcher.parseClass(render(velocityEngine, valueLogPath));
    }

    static String render(VelocityEngine velocityEngine, Path valueLogPath) {
        VelocityContext context = new VelocityContext();
        context.put("valueLogPath", escapePath(valueLogPath));

        StringWriter writer = new StringWriter();
        velocityEngine.getTemplate(TEMPLATE_NAME).merge(context, writer);
        return writer.toString();
    }

    private static String escapePath(Path valueLogPath) {
        return valueLogPath.toString().replace("\\", "/").replace("\"", "\\\"");
    }
}
