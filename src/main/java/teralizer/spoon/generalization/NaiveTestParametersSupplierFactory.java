package teralizer.spoon.generalization;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.Value;
import teralizer.domain.MethodParameter;
import teralizer.spoon.SpoonUtils;
import teralizer.transformer.ModelToJavaTransformer;

import java.util.*;
import java.util.stream.Collectors;

import static teralizer.util.Configuration.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.util.Configuration.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class NaiveTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(
        Factory factory,
        List<MethodParameter> parameters,
        Map<String, Value> arguments,
        String inputJava
    ) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        List<String> supplierBodies = createSupplierBodies(parameters, arguments, inputJava);

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

    private static List<String> createSupplierBodies(
        List<MethodParameter> parameters,
        Map<String, Value> arguments,
        String inputJava
    ) {
        List<String> supplierBodies = new ArrayList<>();
        if (parameters.isEmpty()) {
            supplierBodies.add("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)");
            return supplierBodies;
        }

        // Tuple-level seed: the exact original input combination, injected once around the whole
        // tuple arbitrary. Per-parameter injection inside flatMap re-emits each inner parameter's
        // seed on every outer draw, collapsing inner-parameter diversity.
        String originalTuple = buildOriginalTuple(parameters, arguments);

        for (int i = 0; i < parameters.size(); i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == parameters.size() - 1;

            MethodParameter parameter = parameters.get(i);
            String chain = createArbitrary(parameter);

            if (!isLast) {
                String parameterNames = parameters.stream().limit(i + 1).map(MethodParameter::getName).collect(Collectors.joining(", "));
                chain += ".flatMap(new java.util.function.Function<" + SpoonUtils.getBoxedType(parameter.getType()) + ", net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">>() {\n";
                chain += "    public net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + "> apply(final " + SpoonUtils.getBoxedType(parameter.getType()) + " " + parameter.getName() + ") {\n";
                chain += "        return get" + (i + 1) + "(" + parameterNames + ");\n";
                chain += "    }\n";
                chain += "})";
            } else {
                String parameterNames = parameters.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));
                chain += ".map(new java.util.function.Function<" + SpoonUtils.getBoxedType(parameter.getType()) + ", " + TEST_PARAMETERS_CLASS_NAME + ">() {\n";
                chain += "    public " + TEST_PARAMETERS_CLASS_NAME + " apply(final " + SpoonUtils.getBoxedType(parameter.getType()) + " " + parameter.getName() + ") {\n";
                chain += "        return new " + TEST_PARAMETERS_CLASS_NAME + "(" + parameterNames + ");\n";
                chain += "    }\n";
                chain += "})";
            }

            if (isFirst) {
                if (originalTuple != null) {
                    chain = "new FirstValueArbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">(" + originalTuple + ", " + chain + ")";
                }
                chain += "\n.filter(new java.util.function.Predicate<" + TEST_PARAMETERS_CLASS_NAME + ">() {\n";
                chain += "    public boolean test(final " + TEST_PARAMETERS_CLASS_NAME + " _p_) {\n";
                chain += "        return " + (inputJava == null ? "true" : inputJava) + ";\n";
                chain += "    }\n";
                chain += "})";
            }

            supplierBodies.add("return " + chain);
        }
        return supplierBodies;
    }

    private static String buildOriginalTuple(List<MethodParameter> parameters, Map<String, Value> arguments) {
        if (arguments == null) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (MethodParameter parameter : parameters) {
            Value argument = arguments.get(parameter.getName());
            if (argument == null) {
                return null;
            }
            String firstValue = new ModelToJavaTransformer().transform(argument);
            values.add("(" + parameter.getType() + ") (" + firstValue + ")");
        }
        return "new " + TEST_PARAMETERS_CLASS_NAME + "(" + String.join(", ", values) + ")";
    }

    private static String createArbitrary(MethodParameter parameter) {
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte":
                return "net.jqwik.api.Arbitraries.bytes()";
            case "short":
            case "java.lang.Short":
                return "net.jqwik.api.Arbitraries.shorts()";
            case "int":
            case "java.lang.Integer":
                return "net.jqwik.api.Arbitraries.integers()";
            case "long":
            case "java.lang.Long":
                return "net.jqwik.api.Arbitraries.longs()";
            case "float":
            case "java.lang.Float":
                return "net.jqwik.api.Arbitraries.floats()";
            case "double":
            case "java.lang.Double":
                return "net.jqwik.api.Arbitraries.doubles()";
            case "char":
            case "java.lang.Character":
                return "net.jqwik.api.Arbitraries.chars()";
            case "boolean":
            case "java.lang.Boolean":
                return "net.jqwik.api.Arbitraries.of(true, false)";
            case "String":
            case "java.lang.String":
                return "net.jqwik.api.Arbitraries.strings()";
            default:
                return "net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
        }
    }
}
