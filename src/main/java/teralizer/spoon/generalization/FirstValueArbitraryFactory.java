package teralizer.spoon.generalization;

import java.io.StringWriter;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

public class FirstValueArbitraryFactory {

    static final String TEMPLATE_NAME = "first-value-arbitrary.vm";

    public static CtClass<?> createFirstValueArbitraryClass(VelocityEngine velocityEngine) {
        return Launcher.parseClass(render(velocityEngine));
    }

    static String render(VelocityEngine velocityEngine) {
        StringWriter writer = new StringWriter();
        velocityEngine.getTemplate(TEMPLATE_NAME).merge(new VelocityContext(), writer);
        return writer.toString();
    }
}
