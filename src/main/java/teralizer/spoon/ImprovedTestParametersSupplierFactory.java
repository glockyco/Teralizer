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
import java.util.stream.Collectors;

import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.processing.task.TestGeneralizationTask.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

public class ImprovedTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(Factory factory, List<MethodParameter> parameters, Map<String, VariableConstraints> constraints, String inputJava) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        List<String> supplierBodies = createSupplierBodies(parameters, constraints, inputJava);

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

    private static List<String> createSupplierBodies(List<MethodParameter> parameters, Map<String, VariableConstraints> constraints, String inputJava) {
        List<String> supplierBodies = new ArrayList<>();
        if (parameters.isEmpty()) {
            supplierBodies.add("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)");
        } else {
            for (int i = 0; i < parameters.size(); i++) {
                boolean isFirst = i == 0;
                boolean isLast = i == parameters.size() - 1;

                String body = createArbitrary(parameters.get(i), constraints);

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

    private static String createArbitrary(MethodParameter parameter, Map<String, VariableConstraints> constraints) {
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte": {
                IntegerConstraints constraint = (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createByteArbitrary(parameter, constraint);
            }
            case "short":
            case "java.lang.Short": {
                IntegerConstraints constraint = (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createShortArbitrary(parameter, constraint);
            }
            case "int":
            case "java.lang.Integer": {
                IntegerConstraints constraint = (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createIntegerArbitrary(parameter, constraint);
            }
            case "long":
            case "java.lang.Long": {
                IntegerConstraints constraint = (IntegerConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createLongArbitrary(parameter, constraint);
            }
            case "float":
            case "java.lang.Float": {
                RealConstraints constraint = (RealConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createFloatArbitrary(parameter, constraint);
            }
            case "double":
            case "java.lang.Double": {
                RealConstraints constraint = (RealConstraints) constraints.getOrDefault(parameter.getName(), null);
                return createDoubleArbitrary(parameter, constraint);
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
                return "return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)";
        }
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
        result.append(String.format("return net.jqwik.api.Arbitraries.%s().between(%s, %s)", arbitraryType, n.min(), n.max()));
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
        result.append(String.format("return net.jqwik.api.Arbitraries.%s().ofScale(%d).between(%s, %s, %s, %s)", arbitraryType, scale, n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
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
