package teralizer.spoon.generalization;

import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import teralizer.domain.MethodParameter;
import teralizer.spoon.SpoonUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_CLASS_NAME;

public class TestParametersFactory {

    public static CtClass<?> createParametersClass(Factory factory, List<MethodParameter> parameters) {
        // Create the class:

        CtClass<?> parametersClass = factory.Class().create(TEST_PARAMETERS_CLASS_NAME);
        parametersClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        // Create the fields:

        for (MethodParameter parameter : parameters) {
            parametersClass.addField(
                factory.Field().create(
                    parametersClass,
                    new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
                    factory.Type().createReference(parameter.getType()),
                    parameter.getName()
                )
            );
        }

        // Create the constructor:

        List<CtParameter<?>> constructorParameters = parameters.stream().map(p ->
            factory.createParameter(null, SpoonUtils.getTypeReference(factory, p.getType()), p.getName())
        ).collect(Collectors.toList());

        CtConstructor<?> constructor = factory.Constructor().create(
            parametersClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            constructorParameters,
            Collections.emptySet(),
            factory.Core().createBlock()
        );

        constructor.getBody().addStatement(factory.Code().createCodeSnippetStatement(createConstructorBody(parameters)));

        // Create the toString method:

        CtMethod<?> toStringMethod = factory.Method().create(
            parametersClass,
            new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC)),
            factory.Type().STRING,
            "toString",
            Collections.emptyList(),
            Collections.emptySet(),
            factory.Core().createBlock()
        );

        toStringMethod.getBody().addStatement(factory.Code().createCodeSnippetStatement(createToStringBody(parameters)));

        // Return the class:

        return parametersClass;
    }

    private static String createConstructorBody(List<MethodParameter> parameters) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            String parameterName = parameters.get(i).getName();
            builder.append(String.format("this.%s=%s", parameterName, parameterName));
            if (i < parameters.size() - 1) {
                builder.append(";");
            }
        }
        return builder.toString();
    }

    private static String createToStringBody(List<MethodParameter> parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("return \"TestParameters{");
        for (int i = 0; i < parameters.size(); i++) {
            String parameterName = parameters.get(i).getName();
            builder.append(String.format("%s=\" + this.%s + \"", parameterName, parameterName));
            if (i < parameters.size() - 1) {
                builder.append(",");
            }
        }
        builder.append("}\"");
        return builder.toString();
    }
}
