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
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.ParameterGenerationPlan;
import teralizer.spoon.SpoonUtils;
import teralizer.transformer.ModelToJavaTransformer;

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
        return createSupplierClass(factory, parameters, arguments, constraints, inputJava, null);
    }

    public static CtClass<?> createSupplierClass(
        Factory factory,
        List<MethodParameter> parameters,
        Map<String, MethodArgument> arguments,
        Map<String, VariableConstraints> constraints,
        String inputJava,
        InputGenerationPlan plan
    ) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        String residualPredicate = plan == null ? inputJava : plan.getResidualPredicate();
        boolean applyInputFilter = plan == null ? inputJava != null : plan.hasResidualClauses();
        createGetMethod(supplierClass, parameters, residualPredicate, applyInputFilter);

        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            Optional<MethodArgument> argument = arguments.containsKey(parameter.getName())
                ? Optional.of(arguments.get(parameter.getName()))
                : Optional.empty();

            ParameterGenerationPlan parameterPlan = plan == null ? null : plan.getParameterPlans().get(i);
            createGetParameterMethod(supplierClass, parameters.get(i), argument, parameters.subList(0, i), constraints, parameterPlan);
        }

        return supplierClass;
    }

    private static void createGetMethod(
        CtClass<?> supplierClass,
        List<MethodParameter> parameters,
        String inputJava,
        boolean applyInputFilter
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
        //     return get_x().flatMap(new java.util.function.Function<Integer, net.jqwik.api.Arbitrary<TestParameters>>() {
        //         public net.jqwik.api.Arbitrary<TestParameters> apply(final Integer x) {
        //             return get_y(x).flatMap(new java.util.function.Function<Integer, net.jqwik.api.Arbitrary<TestParameters>>() {
        //                 public net.jqwik.api.Arbitrary<TestParameters> apply(final Integer y) {
        //                     return get_z(x, y).map(new java.util.function.Function<Integer, TestParameters>() {
        //                         public TestParameters apply(final Integer z) {
        //                             return new TestParameters(x, y, z);
        //                         }
        //                     });
        //                 }
        //             });
        //         }
        //     }).filter(new java.util.function.Predicate<TestParameters>() {
        //         public boolean test(final TestParameters _p_) {
        //             return {inputJava};
        //         }
        //     });

        Function<List<MethodParameter>, String> paramNames = (List<MethodParameter> params) -> params.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));

        StringBuilder builder = new StringBuilder();
        builder.append("return ");
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter currentParameter = parameters.get(i);
            List<MethodParameter> previousParameters = parameters.subList(0, i);
            String previousParamNames = paramNames.apply(previousParameters);

            builder.append("get_").append(currentParameter.getName()).append("(").append(previousParamNames).append(")");

            if (i < parameters.size() - 1) {
                builder.append(".flatMap(new java.util.function.Function<");
                builder.append(getBoxedType(currentParameter.getType()));
                builder.append(", net.jqwik.api.Arbitrary<TestParameters>>() {\n");
                builder.append("    public net.jqwik.api.Arbitrary<TestParameters> apply(final ");
                builder.append(getBoxedType(currentParameter.getType())).append(" ");
                builder.append(currentParameter.getName()).append(") {\n");
                builder.append("        return ");
            } else {
                builder.append(".map(new java.util.function.Function<");
                builder.append(getBoxedType(currentParameter.getType()));
                builder.append(", TestParameters>() {\n");
                builder.append("    public TestParameters apply(final ");
                builder.append(getBoxedType(currentParameter.getType())).append(" ");
                builder.append(currentParameter.getName()).append(") {\n");
                builder.append("        return new ").append(TEST_PARAMETERS_CLASS_NAME);
                builder.append("(").append(paramNames.apply(parameters)).append(");\n");
                builder.append("    }\n");
                builder.append("})");

                // Close all the nested return statements and methods
                for (int j = 0; j < i; j++) {
                    builder.append(";\n    }\n})");
                }

                if (applyInputFilter) {
                    builder.append("\n.filter(new java.util.function.Predicate<TestParameters>() {\n");
                    builder.append("    public boolean test(final TestParameters _p_) {\n");
                    builder.append("        return ").append(inputJava).append(";\n");
                    builder.append("    }\n");
                    builder.append("})");
                }
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
        createGetParameterMethod(supplierClass, parameter, argument, previousParameters, constraints, null);
    }

    private static void createGetParameterMethod(
        CtClass<?> supplierClass,
        MethodParameter parameter,
        Optional<MethodArgument> argument,
        List<MethodParameter> previousParameters,
        Map<String, VariableConstraints> constraints,
        ParameterGenerationPlan parameterPlan
    ) {
        Factory factory = supplierClass.getFactory();

        String body;
        String arbitraryType = getBoxedType(parameter.getType());
        if (parameterPlan != null && parameterPlan.getRecipe() != null && !parameterPlan.getRecipe().emit().isEmpty()) {
            body = parameterPlan.getRecipe().emit();
        } else {
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
        }

        Set<ModifierKind> modifiers = new HashSet<>(Collections.singletonList(ModifierKind.PRIVATE));
        CtTypeReference<?> returnType = factory.Type().createReference("net.jqwik.api.Arbitrary<" + arbitraryType + ">");

        List<CtParameter<?>> params = previousParameters.stream().map(p -> {
            CtParameter<?> param = factory.createParameter(null, SpoonUtils.getTypeReference(factory, p.getType()), p.getName());
            param.addModifier(ModifierKind.FINAL);
            return param;
        }).collect(Collectors.toList());

        CtMethod<?> supplierMethod = factory.Method().create(supplierClass, modifiers, returnType, "get_" + parameter.getName(), params, Collections.emptySet(), factory.Core().createBlock());
        supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement(body));
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
        return createRealArbitrary(parameter, argument, constraint, "float", "Float", "floats", "-Float.MAX_VALUE", "Float.MAX_VALUE");
    }

    private static String createDoubleArbitrary(MethodParameter parameter, Optional<MethodArgument> argument, RealConstraints constraint) {
        return createRealArbitrary(parameter, argument, constraint, "double", "Double", "doubles", "-Double.MAX_VALUE", "Double.MAX_VALUE");
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
                String firstValue = new ModelToJavaTransformer().transform(argument.get());
                return String.format("return new FirstValueArbitrary<" + boxedType + ">((%s) (%s), net.jqwik.api.Arbitraries.%s())", argument.get().getType(), firstValue, arbitraryType);
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
            String firstValue = new ModelToJavaTransformer().transform(argument.get());
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), argument.get().getType(), firstValue));
            result.append(String.format("return new FirstValueArbitrary<" + boxedType + ">((%s) (%s), net.jqwik.api.Arbitraries.%s().between(%s, %s))", argument.get().getType(), firstValue, arbitraryType, n.min(), n.max()));
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
        String maxValue
    ) {
        if (constraint == null) {
            if (argument.isPresent()) {
                String firstValue = new ModelToJavaTransformer().transform(argument.get());
                return String.format("return new FirstValueArbitrary<" + boxedType + ">((%s) (%s), net.jqwik.api.Arbitraries.%s())", argument.get().getType(), firstValue, arbitraryType);
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
        result.append(generateInclusionCheck(n, true));

        result.append(String.format("%s %s = java.util.Collections.min(%s);\n", parameter.getType(), n.max(), n.upperBounds()));
        result.append(generateInclusionCheck(n, false));
        result.append(String.format("int %s = 0;\n", n.scale()));
        result.append(String.format("if (!java.lang.Double.isNaN(%s) && !java.lang.Double.isInfinite(%s)) { %s = java.lang.Math.max(%s, java.lang.Math.max(0, java.math.BigDecimal.valueOf(%s).scale())); }\n", n.min(), n.min(), n.scale(), n.scale(), n.min()));
        result.append(String.format("if (!java.lang.Double.isNaN(%s) && !java.lang.Double.isInfinite(%s)) { %s = java.lang.Math.max(%s, java.lang.Math.max(0, java.math.BigDecimal.valueOf(%s).scale())); }\n", n.max(), n.max(), n.scale(), n.scale(), n.max()));

        if (argument.isPresent()) {
            String firstValue = new ModelToJavaTransformer().transform(argument.get());
            result.append(String.format("if ((%s > %s) || (%s == %s && (!%s || !%s))) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded(), argument.get().getType(), firstValue));
            result.append(String.format("return new FirstValueArbitrary<" + boxedType + ">((%s) (%s), net.jqwik.api.Arbitraries.%s().ofScale(%s).between(%s, %s, %s, %s))", argument.get().getType(), firstValue, arbitraryType, n.scale(), n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
        } else {
            result.append(String.format("if ((%s > %s) || (%s == %s && (!%s || !%s))) { return net.jqwik.api.Arbitraries.of(); }%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded()));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().ofScale(%s).between(%s, %s, %s, %s)", arbitraryType, n.scale(), n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
        }

        return result.toString();
    }

    private static String generateInclusionCheck(Names n, boolean isMin) {
        String bounds = isMin ? n.lowerBounds() : n.upperBounds();
        String boundIncluded = isMin ? n.lowerBoundIncluded() : n.upperBoundIncluded();
        String value = isMin ? n.min() : n.max();
        String included = isMin ? n.minIncluded() : n.maxIncluded();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("boolean %s = true;\n", included));
        sb.append(String.format("for (int i = 0; i < %s.size(); i++) {\n", bounds));
        sb.append(String.format("    if (%s.get(i).equals(%s) && !%s.get(i)) {\n", bounds, value, boundIncluded));
        sb.append(String.format("        %s = false;\n", included));
        sb.append(String.format("        break;\n"));
        sb.append(String.format("    }\n"));
        sb.append(String.format("}\n"));
        return sb.toString();
    }

    private static class Names {
        private final String baseName;

        public Names(String baseName) {
            this.baseName = baseName;
        }

        public String scale() {
            return this.baseName + "Scale";
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
