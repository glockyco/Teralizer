package teralizer.spoon.generalization;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.spoon.SpoonUtils;

import java.util.*;
import java.util.stream.Collectors;

import static teralizer.util.Configuration.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.util.Configuration.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class BaselineTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(Factory factory, List<MethodParameter> parameters, Map<String, MethodArgument> arguments) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        List<String> supplierBodies = createSupplierBodies(parameters, arguments);

        for (int i = 0; i < parameters.size(); i++) {
            Set<ModifierKind> modifiers = new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC));
            CtTypeReference<?> returnType = factory.Type().createReference("net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">");

            List<CtParameter<?>> supplierParameters = parameters.stream().limit(i).map(p ->
                factory.createParameter(null, SpoonUtils.getTypeReference(factory, p.getType()), p.getName())
            ).collect(Collectors.toList());

            CtMethod<?> supplierMethod = factory.Method().create(supplierClass, modifiers, returnType, "get" + (i == 0 ? "" : i), supplierParameters, Collections.emptySet(), factory.Core().createBlock());
            supplierMethod.setBody(factory.createCodeSnippetStatement(supplierBodies.get(i)));
        }

        return supplierClass;
    }

    private static List<String> createSupplierBodies(List<MethodParameter> parameters, Map<String, MethodArgument> arguments) {
        List<String> supplierBodies = new ArrayList<>();
        if (parameters.isEmpty()) {
            supplierBodies.add("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)");
        } else {
            for (int i = 0; i < parameters.size(); i++) {
                boolean isLast = i == parameters.size() - 1;

                MethodParameter parameter = parameters.get(i);
                MethodArgument argument = arguments.get(parameter.getName());

                String body = createArbitrary(parameter, argument);

                if (!isLast) {
                    String parameterNames = parameters.stream().limit(i + 1).map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".flatMap(" + parameter.getName() + " -> { return get" + (i + 1) + "(" + parameterNames + "); })";
                } else {
                    String parameterNames = parameters.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".map(" + parameter.getName() + " -> new " + TEST_PARAMETERS_CLASS_NAME + "(" + parameterNames + "))";
                }

                supplierBodies.add(body);
            }
        }
        return supplierBodies;
    }

    private static String createArbitrary(MethodParameter parameter, MethodArgument argument) {
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "float":
            case "java.lang.Float":
            case "double":
            case "java.lang.Double":
            case "char":
            case "java.lang.Character":
            case "boolean":
            case "java.lang.Boolean":
            case "String":
            case "java.lang.String":
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") " + argument.getValue() + ")";
            case "long":
            case "java.lang.Long":
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") " + argument.getValue() + "L)";
            default:
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
        }
    }
}
