package teralizer.spoon.generalization;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodParameter;
import teralizer.spoon.SpoonUtils;

import java.util.*;
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class NaiveTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(Factory factory, List<MethodParameter> parameters, String inputJava) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        List<String> supplierBodies = createSupplierBodies(parameters, inputJava);

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

    private static List<String> createSupplierBodies(List<MethodParameter> parameters, String inputJava) {
        List<String> supplierBodies = new ArrayList<>();
        if (parameters.isEmpty()) {
            supplierBodies.add("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)");
        } else {
            for (int i = 0; i < parameters.size(); i++) {
                boolean isFirst = i == 0;
                boolean isLast = i == parameters.size() - 1;

                String body = createArbitrary(parameters.get(i));

                if (!isLast) {
                    String parameterNames = parameters.stream().limit(i + 1).map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".flatMap(" + parameters.get(i).getName() + " -> { return get" + (i + 1) + "(" + parameterNames + "); })";
                } else {
                    String parameterNames = parameters.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));
                    body += ".map(" + parameters.get(i).getName() + " -> new " + TEST_PARAMETERS_CLASS_NAME + "(" + parameterNames + "))";
                }

                if (isFirst) {
                    body += " .filter(_p_ -> " + (inputJava == null ? "true" : inputJava) + ")";
                }

                supplierBodies.add(body);
            }
        }
        return supplierBodies;
    }

    private static String createArbitrary(MethodParameter parameter) {
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte": {
                return "return net.jqwik.api.Arbitraries.bytes()";
            }
            case "short":
            case "java.lang.Short": {
                return "return net.jqwik.api.Arbitraries.shorts()";
            }
            case "int":
            case "java.lang.Integer": {
                return "return net.jqwik.api.Arbitraries.integers()";
            }
            case "long":
            case "java.lang.Long": {
                return "return net.jqwik.api.Arbitraries.longs()";
            }
            case "float":
            case "java.lang.Float": {
                return "return net.jqwik.api.Arbitraries.floats()";
            }
            case "double":
            case "java.lang.Double": {
                return "return net.jqwik.api.Arbitraries.doubles()";
            }
            case "char":
            case "java.lang.Character":
                return "return net.jqwik.api.Arbitraries.chars()";
            case "boolean":
            case "java.lang.Boolean":
                return "return net.jqwik.api.Arbitraries.of(true, false)";
            case "String":
            case "java.lang.String":
                return "return net.jqwik.api.Arbitraries.strings()";
            default:
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
        }
    }
}
