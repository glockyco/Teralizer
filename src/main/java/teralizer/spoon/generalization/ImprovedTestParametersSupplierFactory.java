package teralizer.spoon.generalization;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.IntegerConstraints;
import teralizer.jqwik.RealConstraints;
import teralizer.jqwik.VariableConstraints;
import teralizer.spoon.SpoonUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static teralizer.util.Configuration.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.util.Configuration.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class ImprovedTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(
        Factory factory,
        List<MethodParameter> parameters,
        Map<String, MethodArgument> arguments,
        Map<String, VariableConstraints> constraints,
        String inputJava
    ) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        createGetMethod(supplierClass, parameters, inputJava);

        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            Optional<MethodArgument> argument = arguments.containsKey(parameter.getName())
                ? Optional.of(arguments.get(parameter.getName()))
                : Optional.empty();

            createGetParameterMethod(supplierClass, parameters.get(i), argument, parameters.subList(0, i), constraints);
        }

        return supplierClass;
    }

    private static void createGetMethod(
        CtClass<?> supplierClass,
        List<MethodParameter> parameters,
        String inputJava
    ) {
        Factory factory = supplierClass.getFactory();

        Set<ModifierKind> modifiers = new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC));
        CtTypeReference<?> returnType = factory.Type().createReference("net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">");

        CtMethod<?> supplierMethod = factory.Method().create(supplierClass, modifiers, returnType, "get", Collections.emptyList(), Collections.emptySet(), factory.Core().createBlock());

        if (parameters.isEmpty()) {
            supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)"));
            return;
        }

        // Build a method body that looks like:
        //     return
        //         getX().flatMap(x ->
        //             getY(x).flatMap(y ->
        //                 getZ(x, y).map(z -> new TestParameters(x, y, z))
        //             )
        //         ).filter(_p_ -> {inputJava});

        Function<List<MethodParameter>, String> paramNames = (List<MethodParameter> params) -> params.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));

        StringBuilder builder = new StringBuilder();
        builder.append("return ");
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter currentParameter = parameters.get(i);
            List<MethodParameter> previousParameters = parameters.subList(0, i);

            builder.append("get_" + currentParameter.getName() + "(" + paramNames.apply(previousParameters) + ")");

            if (i < parameters.size() - 1) {
                builder.append(".flatMap(" + currentParameter.getName() + " -> ");
            } else {
                builder.append(".map(" + currentParameter.getName() + " -> new " + TEST_PARAMETERS_CLASS_NAME + "(" + paramNames.apply(parameters) + "))");
                // Close the parentheses opened by the flatMaps calls:
                builder.append(String.join("", Collections.nCopies(i, ")")));
                builder.append("\n    .filter(_p_ -> " + (inputJava == null ? "true" : inputJava) + ")");
            }
        }

        supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement(builder.toString()));
    }

    private static void createGetParameterMethod(
        CtClass<?> supplierClass,
        MethodParameter parameter,
        Optional<MethodArgument> argument,
        List<MethodParameter> previousParameters,
        Map<String, VariableConstraints> constraints
    ) {
        Factory factory = supplierClass.getFactory();

        String body;
        String arbitraryType;
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte": {
                body = createByteArbitrary(parameter, argument, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Byte";
                break;
            }
            case "short":
            case "java.lang.Short": {
                body = createShortArbitrary(parameter, argument, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Short";
                break;
            }
            case "int":
            case "java.lang.Integer": {
                body = createIntegerArbitrary(parameter, argument, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Integer";
                break;
            }
            case "long":
            case "java.lang.Long": {
                body = createLongArbitrary(parameter, argument, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Long";
                break;
            }
            case "float":
            case "java.lang.Float": {
                body = createFloatArbitrary(parameter, argument, (RealConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Float";
                break;
            }
            case "double":
            case "java.lang.Double": {
                body = createDoubleArbitrary(parameter, argument, (RealConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Double";
                break;
            }
            case "char":
            case "java.lang.Character":
                body = "return net.jqwik.api.Arbitraries.chars()";
                arbitraryType = "Character";
                break;
            case "boolean":
            case "java.lang.Boolean":
                body = "return net.jqwik.api.Arbitraries.of(true, false)";
                arbitraryType = "Boolean";
                break;
            case "String":
            case "java.lang.String":
                body = "return net.jqwik.api.Arbitraries.strings()";
                arbitraryType = "String";
                break;
            default:
                body = "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
                arbitraryType = parameter.getType();
                break;
        }

        Set<ModifierKind> modifiers = new HashSet<>(Collections.singletonList(ModifierKind.PRIVATE));
        CtTypeReference<?> returnType = factory.Type().createReference("net.jqwik.api.Arbitrary<" + arbitraryType + ">");

        List<CtParameter<?>> params = previousParameters.stream().map(p ->
            factory.createParameter(null, SpoonUtils.getTypeReference(factory, p.getType()), p.getName())
        ).collect(Collectors.toList());

        CtMethod<?> supplierMethod = factory.Method().create(supplierClass, modifiers, returnType, "get_" + parameter.getName(), params, Collections.emptySet(), factory.Core().createBlock());
        supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement(body));
    }

    private static String createByteArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, argument, constraint, "byte", "Byte", "bytes", "Byte.MIN_VALUE", "Byte.MAX_VALUE");
    }

    private static String createShortArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, argument, constraint, "short", "Short", "shorts", "Short.MIN_VALUE", "Short.MAX_VALUE");
    }

    private static String createIntegerArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, argument, constraint, "int", "Integer", "integers", "Integer.MIN_VALUE", "Integer.MAX_VALUE");
    }

    private static String createLongArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, argument, constraint, "long", "Long", "longs", "Long.MIN_VALUE", "Long.MAX_VALUE");
    }

    private static String createFloatArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, RealConstraints constraint) {
        return createRealArbitrary(parameter, argument, constraint, "float", "Float", "floats", "-Float.MAX_VALUE", "Float.MAX_VALUE", 46);
    }

    private static String createDoubleArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, RealConstraints constraint) {
        return createRealArbitrary(parameter, argument, constraint, "double", "Double", "doubles", "-Double.MAX_VALUE", "Double.MAX_VALUE", 325);
    }

    private static String createNumberArbitrary(
        MethodParameter parameter,
        Optional<MethodArgument> argument,
        IntegerConstraints constraint,
        String unboxedType,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue
    ) {
        if (constraint == null) {
            if (argument.isPresent()) {
                return String.format("return new FirstValueArbitrary<>((%s) (%s), net.jqwik.api.Arbitraries.%s())", argument.get().getType(), argument.get().getValue(), arbitraryType);
            } else {
                return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
            }
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.just(" + String.format("(%s) (%s)", unboxedType, constraint.getEquality()) + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b)).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b)).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);\n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(String.format("%s %s = java.util.Collections.min(%s);\n", parameter.getType(), n.max(), n.upperBounds()));

        if (argument.isPresent()) {
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), argument.get().getType(), argument.get().getValue()));
            result.append(String.format("return new FirstValueArbitrary<>((%s) (%s), net.jqwik.api.Arbitraries.%s().between(%s, %s))", argument.get().getType(), argument.get().getValue(), arbitraryType, n.min(), n.max()));
        } else {
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.of(); }%n", n.min(), n.max()));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().between(%s, %s)", arbitraryType, n.min(), n.max()));
        }

        return result.toString();
    }

    private static String createRealArbitrary(
        MethodParameter parameter,
        Optional<MethodArgument> argument,
        RealConstraints constraint,
        String unboxedType,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue,
        int scale
    ) {
        if (constraint == null) {
            if (argument.isPresent()) {
                return String.format("return new FirstValueArbitrary<>((%s) (%s), net.jqwik.api.Arbitraries.%s())", argument.get().getType(), argument.get().getValue(), arbitraryType);
            } else {
                return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
            }
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.just(" + String.format("(%s) (%s)", unboxedType, constraint.getEquality()) + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b.getValue())).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);\n", n.lowerBoundIncluded(), constraint.getLowerBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b.getValue())).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);\n", n.upperBoundIncluded(), constraint.getUpperBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);\n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(String.format("boolean %s = java.util.stream.IntStream.range(0, %s.size()).filter(i -> %s.get(i) == %s).allMatch(%s::get);\n", n.minIncluded(), n.lowerBounds(), n.lowerBounds(), n.min(), n.lowerBoundIncluded()));
        result.append(String.format("%s %s = java.util.Collections.min(%s);\n", parameter.getType(), n.max(), n.upperBounds()));
        result.append(String.format("boolean %s = java.util.stream.IntStream.range(0, %s.size()).filter(i -> %s.get(i) == %s).allMatch(%s::get);\n", n.maxIncluded(), n.upperBounds(), n.upperBounds(), n.max(), n.upperBoundIncluded()));

        if (argument.isPresent()) {
            result.append(String.format("if ((%s > %s) || (%s == %s && (!%s || !%s))) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded(), argument.get().getType(), argument.get().getValue()));
            result.append(String.format("return new FirstValueArbitrary<>((%s) (%s), net.jqwik.api.Arbitraries.%s().ofScale(%d).between(%s, %s, %s, %s))", argument.get().getType(), argument.get().getValue(), arbitraryType, scale, n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
        } else {
            result.append(String.format("if ((%s > %s) || (%s == %s && (!%s || !%s))) { return net.jqwik.api.Arbitraries.of(); }%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded()));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().ofScale(%d).between(%s, %s, %s, %s)", arbitraryType, scale, n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
        }

        return result.toString();
    }


    private static class Names {
        private final String baseName;

        public Names(String baseName) {
            this.baseName = baseName;
        }

        public String defaultMin() {
            return this.baseName + "DefaultMin";
        }

        public String defaultMax() {
            return this.baseName + "DefaultMax";
        }

        public String lowerBounds() {
            return this.baseName + "LowerBounds";
        }

        public String lowerBoundIncluded() {
            return this.baseName + "LowerBoundsIncluded";
        }

        public String upperBounds() {
            return this.baseName + "UpperBounds";
        }

        public String upperBoundIncluded() {
            return this.baseName + "UpperBoundsIncluded";
        }

        public String min() {
            return this.baseName + "Min";
        }

        public String minIncluded() {
            return this.baseName + "MinIncluded";
        }

        public String max() {
            return this.baseName + "Max";
        }

        public String maxIncluded() {
            return this.baseName + "MaxIncluded";
        }
    }
}
