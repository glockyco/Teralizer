package teralizer.spoon;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.VariableConstraintExtractor.IntegerConstraints;
import teralizer.jqwik.VariableConstraintExtractor.RealConstraints;
import teralizer.jqwik.VariableConstraintExtractor.VariableConstraints;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class ImprovedTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(
        Factory factory,
        List<MethodParameter> parameters,
        Map<String, VariableConstraints> constraints,
        String inputJava
    ) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        createGetMethod(supplierClass, parameters, inputJava);

        for (int i = 0; i < parameters.size(); i++) {
            List<MethodParameter> params = parameters.subList(0, i);
            createGetParameterMethod(supplierClass, parameters.get(i), params, constraints);
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
        List<MethodParameter> previousParameters,
        Map<String, VariableConstraints> constraints
    ) {
        Factory factory = supplierClass.getFactory();

        String body;
        String arbitraryType;
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte": {
                body = createByteArbitrary(parameter, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Byte";
                break;
            }
            case "short":
            case "java.lang.Short": {
                body = createShortArbitrary(parameter, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Short";
                break;
            }
            case "int":
            case "java.lang.Integer": {
                body = createIntegerArbitrary(parameter, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Integer";
                break;
            }
            case "long":
            case "java.lang.Long": {
                body = createLongArbitrary(parameter, (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Long";
                break;
            }
            case "float":
            case "java.lang.Float": {
                body = createFloatArbitrary(parameter, (RealConstraints) constraints.getOrDefault(parameter.getName(), null));
                arbitraryType = "Float";
                break;
            }
            case "double":
            case "java.lang.Double": {
                body = createDoubleArbitrary(parameter, (RealConstraints) constraints.getOrDefault(parameter.getName(), null));
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
                body = "return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)";
                arbitraryType = "Object";
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

    private static String createByteArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, constraint, "Byte", "bytes", "Byte.MIN_VALUE", "Byte.MAX_VALUE");
    }

    private static String createShortArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, constraint, "Short", "shorts", "Short.MIN_VALUE", "Short.MAX_VALUE");
    }

    private static String createIntegerArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, constraint, "Integer", "integers", "Integer.MIN_VALUE", "Integer.MAX_VALUE");
    }

    private static String createLongArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        return createNumberArbitrary(parameter, constraint, "Long", "longs", "Long.MIN_VALUE", "Long.MAX_VALUE");
    }

    private static String createFloatArbitrary(MethodParameter parameter, RealConstraints constraint) {
        return createRealArbitrary(parameter, constraint, "Float", "floats", "-Float.MAX_VALUE", "Float.MAX_VALUE", 46);
    }

    private static String createDoubleArbitrary(MethodParameter parameter, RealConstraints constraint) {
        return createRealArbitrary(parameter, constraint, "Double", "doubles", "-Double.MAX_VALUE", "Double.MAX_VALUE", 325);
    }

    private static String createNumberArbitrary(
        MethodParameter parameter,
        IntegerConstraints constraint,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue
    ) {
        if (constraint == null) {
            return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> ", " + b).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> ", " + b).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);\n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(String.format("%s %s = java.util.Collections.min(%s);\n", parameter.getType(), n.max(), n.upperBounds()));
        result.append(String.format("return (%s > %s)%n    ? net.jqwik.api.Arbitraries.of()%n", n.min(), n.max()));
        result.append(String.format("    : net.jqwik.api.Arbitraries.%s().between(%s, %s)", arbitraryType, n.min(), n.max()));
        return result.toString();
    }

    private static String createRealArbitrary(
        MethodParameter parameter,
        RealConstraints constraint,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue,
        int scale
    ) {
        if (constraint == null) {
            return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;\n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);\n", n.lowerBoundIncluded(), constraint.getLowerBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);\n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);\n", n.upperBoundIncluded(), constraint.getUpperBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);\n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(String.format("boolean %s = java.util.stream.IntStream.range(0, %s.size()).filter(i -> %s.get(i) == %s).allMatch(%s::get);\n", n.minIncluded(), n.lowerBounds(), n.lowerBounds(), n.min(), n.lowerBoundIncluded()));
        result.append(String.format("%s %s = java.util.Collections.min(%s);\n", parameter.getType(), n.max(), n.upperBounds()));
        result.append(String.format("boolean %s = java.util.stream.IntStream.range(0, %s.size()).filter(i -> %s.get(i) == %s).allMatch(%s::get);\n", n.maxIncluded(), n.upperBounds(), n.upperBounds(), n.max(), n.upperBoundIncluded()));
        result.append(String.format("return ((%s > %s) || (%s == %s && (!%s || !%s)))%n    ? net.jqwik.api.Arbitraries.of()%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded()));
        result.append(String.format("    : net.jqwik.api.Arbitraries.%s().ofScale(%d).between(%s, %s, %s, %s)", arbitraryType, scale, n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
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
