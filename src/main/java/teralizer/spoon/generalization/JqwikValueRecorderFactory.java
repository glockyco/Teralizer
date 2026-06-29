package teralizer.spoon.generalization;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

import java.io.StringWriter;
import java.nio.file.Path;

public class JqwikValueRecorderFactory {

    static final String TEMPLATE_NAME = "jqwik-value-recorder.vm";

    public static CtClass<?> createRecorderClass(
        VelocityEngine velocityEngine,
        Path baseDirectory,
        long projectId,
        long generalizationId,
        String variant,
        String testCaseName
    ) {
        return Launcher.parseClass(render(velocityEngine, baseDirectory, projectId, generalizationId, variant, testCaseName));
    }

    static String render(
        VelocityEngine velocityEngine,
        Path baseDirectory,
        long projectId,
        long generalizationId,
        String variant,
        String testCaseName
    ) {
        VelocityContext context = new VelocityContext();
        context.put("projectId", projectId);
        context.put("generalizationId", generalizationId);
        context.put("variant", escapeJava(variant));
        context.put("testCaseName", escapeJava(testCaseName));
        context.put("baseDirectory", escapePath(baseDirectory));

        StringWriter writer = new StringWriter();
        velocityEngine.getTemplate(TEMPLATE_NAME).merge(context, writer);
        return writer.toString();
    }

    private static String escapePath(Path path) {
        return path.toString().replace("\\", "/").replace("\"", "\\\"");
    }

    private static String escapeJava(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
