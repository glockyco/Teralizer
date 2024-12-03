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
            case "java.lang.Char":
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
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.bytes()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = Byte.MIN_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Byte.MAX_VALUE;");
            result.append("java.util.List<Byte> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Byte> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("return net.jqwik.api.Arbitraries.bytes().between(" + parameter.getName() + "Min, " + parameter.getName() + "Max)");
            return result.toString();
        }
    }

    private static String createShortArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.shorts()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = Short.MIN_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Short.MAX_VALUE;");
            result.append("java.util.List<Short> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Short> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("return net.jqwik.api.Arbitraries.shorts().between(" + parameter.getName() + "Min, " + parameter.getName() + "Max)");
            return result.toString();
        }
    }

    private static String createIntegerArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.integers()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = Integer.MIN_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Integer.MAX_VALUE;");
            result.append("java.util.List<Integer> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Integer> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("return net.jqwik.api.Arbitraries.integers().between(" + parameter.getName() + "Min, " + parameter.getName() + "Max)");
            return result.toString();
        }
    }

    private static String createLongArbitrary(MethodParameter parameter, IntegerConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.longs()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = Long.MIN_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Long.MAX_VALUE;");
            result.append("java.util.List<Long> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Long> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("return net.jqwik.api.Arbitraries.longs().between(" + parameter.getName() + "Min, " + parameter.getName() + "Max)");
            return result.toString();
        }
    }

    private static String createFloatArbitrary(MethodParameter parameter, RealConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.floats()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = -Float.MAX_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Float.MAX_VALUE;");
            result.append("java.util.List<Float> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Boolean> " + parameter.getName() + "LowerBoundsIncluded = java.util.Arrays.asList(true" + constraint.getLowerBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Float> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Boolean> " + parameter.getName() + "UpperBoundsIncluded = java.util.Arrays.asList(true" + constraint.getUpperBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append("boolean " + parameter.getName() + "MinIncluded = java.util.stream.IntStream.range(0, " + parameter.getName() + "LowerBounds.size()).filter(i -> " + parameter.getName() + "LowerBounds.get(i) == " + parameter.getName() + "Min).allMatch(" + parameter.getName() + "LowerBoundsIncluded::get);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("boolean " + parameter.getName() + "MaxIncluded = java.util.stream.IntStream.range(0, " + parameter.getName() + "UpperBounds.size()).filter(i -> " + parameter.getName() + "UpperBounds.get(i) == " + parameter.getName() + "Max).allMatch(" + parameter.getName() + "UpperBoundsIncluded::get);");
            result.append("return net.jqwik.api.Arbitraries.floats().between(" + parameter.getName() + "Min, " + parameter.getName() + "MinIncluded, " + parameter.getName() + "Max, " + parameter.getName() + "MaxIncluded)");
            return result.toString();
        }
    }

    private static String createDoubleArbitrary(MethodParameter parameter, RealConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.doubles()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.of(" + constraint.getEquality() + ")";
        } else {
            StringBuilder result = new StringBuilder();
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMin = -Double.MAX_VALUE;");
            result.append(parameter.getType() + " " + parameter.getName() + "DefaultMax = Double.MAX_VALUE;");
            result.append("java.util.List<Double> " + parameter.getName() + "LowerBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMin" + constraint.getLowerBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Boolean> " + parameter.getName() + "LowerBoundsIncluded = java.util.Arrays.asList(true" + constraint.getLowerBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Double> " + parameter.getName() + "UpperBounds = java.util.Arrays.asList(" + parameter.getName() + "DefaultMax" + constraint.getUpperBounds().stream().map(b -> ", " + b.getValue()).collect(Collectors.joining()) + ");");
            result.append("java.util.List<Boolean> " + parameter.getName() + "UpperBoundsIncluded = java.util.Arrays.asList(true" + constraint.getUpperBounds().stream().map(b -> ", " + b.getIsIncluded()).collect(Collectors.joining()) + ");");
            result.append(parameter.getType() + " " + parameter.getName() + "Min = java.util.Collections.max(" + parameter.getName() + "LowerBounds);");
            result.append("boolean " + parameter.getName() + "MinIncluded = java.util.stream.IntStream.range(0, " + parameter.getName() + "LowerBounds.size()).filter(i -> " + parameter.getName() + "LowerBounds.get(i) == " + parameter.getName() + "Min).allMatch(" + parameter.getName() + "LowerBoundsIncluded::get);");
            result.append(parameter.getType() + " " + parameter.getName() + "Max = java.util.Collections.min(" + parameter.getName() + "UpperBounds);");
            result.append("boolean " + parameter.getName() + "MaxIncluded = java.util.stream.IntStream.range(0, " + parameter.getName() + "UpperBounds.size()).filter(i -> " + parameter.getName() + "UpperBounds.get(i) == " + parameter.getName() + "Max).allMatch(" + parameter.getName() + "UpperBoundsIncluded::get);");
            result.append("return net.jqwik.api.Arbitraries.doubles().between(" + parameter.getName() + "Min, " + parameter.getName() + "MinIncluded, " + parameter.getName() + "Max, " + parameter.getName() + "MaxIncluded)");
            return result.toString();
        }
    }
}
