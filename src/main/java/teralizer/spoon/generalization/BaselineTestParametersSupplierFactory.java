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
import teralizer.transformer.ModelToJavaTransformer;

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

            List<CtParameter<?>> supplierParameters = parameters.stream().limit(i).map(p -> {
                CtParameter<?> param = factory.createParameter(null, SpoonUtils.getTypeReference(factory, p.getType()), p.getName());
                param.addModifier(ModifierKind.FINAL);
                return param;
            }).collect(Collectors.toList());

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

                String body = createArbitrary(argument);

                if (!isLast) {
                    String parameterNames = parameters.stream().limit(i + 1).map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".flatMap(new java.util.function.Function<" + getBoxedType(parameter.getType()) + ", net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">>() {\n";
                    body += "    public net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + "> apply(final " + getBoxedType(parameter.getType()) + " " + parameter.getName() + ") {\n";
                    body += "        return get" + (i + 1) + "(" + parameterNames + ");\n";
                    body += "    }\n";
                    body += "})";
                } else {
                    String parameterNames = parameters.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".map(new java.util.function.Function<" + getBoxedType(parameter.getType()) + ", " + TEST_PARAMETERS_CLASS_NAME + ">() {\n";
                    body += "    public " + TEST_PARAMETERS_CLASS_NAME + " apply(final " + getBoxedType(parameter.getType()) + " " + parameter.getName() + ") {\n";
                    body += "        return new " + TEST_PARAMETERS_CLASS_NAME + "(" + parameterNames + ");\n";
                    body += "    }\n";
                    body += "})";
                }

                supplierBodies.add(body);
            }
        }
        return supplierBodies;
    }

    private static String createArbitrary(MethodArgument argument) {
        String value = new ModelToJavaTransformer().transform(argument);
        if (argument.getType().equals("boolean") || argument.getType().equals("java.lang.Boolean")) {
            return "return net.jqwik.api.Arbitraries.just(" + value + ")";
        }
        return "return net.jqwik.api.Arbitraries.just((" + argument.getType() + ") " + value + ")";
    }

    private static String getBoxedType(String type) {
        switch (type) {
            case "byte": return "Byte";
            case "short": return "Short";
            case "int": return "Integer";
            case "long": return "Long";
            case "float": return "Float";
            case "double": return "Double";
            case "char": return "Character";
            case "boolean": return "Boolean";
            default: return type;
        }
    }
}
