package teralizer.jqwik.planning;

import teralizer.domain.TypeDomain;

import teralizer.domain.Value;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.IntegerConstraints;
import teralizer.jqwik.RealBound;
import teralizer.jqwik.RealConstraints;
import teralizer.jqwik.VariableConstraints;
import teralizer.transformer.ModelToJavaTransformer;

import java.util.Optional;
import java.util.stream.Collectors;

public class NumericDomainPlanner implements DomainPlanner {
    @Override
    public boolean supports(TypeDomain domain) {
        return domain == TypeDomain.INTEGER || domain == TypeDomain.REAL || domain == TypeDomain.CHAR;
    }

    @Override
    public boolean supportsReturn(TypeDomain domain) {
        return supports(domain);
    }

    @Override
    public ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context) {
        TypeDomain domain = TypeDomain.from(parameter.getType());
        Optional<Value> argument = context.getArguments().containsKey(parameter.getName())
            ? Optional.of(context.getArguments().get(parameter.getName()))
            : Optional.empty();
        NumericClauseInterpretation interpretation = context.getInterpretation(parameter.getName());
        String body = createArbitrary(parameter, argument, interpretation.getConstraints());
        String originalValue = argument
            .map(arg -> "(" + arg.getJavaType() + ") (" + new ModelToJavaTransformer().transform(arg) + ")")
            .orElse(null);
        return new ParameterGenerationPlan(parameter, domain, new RawJavaRecipe(body), originalValue, interpretation.getConsumedClauseIds());
    }

    private static String createArbitrary(MethodParameter parameter, Optional<Value> argument, VariableConstraints constraint) {
        switch (parameter.getType()) {
            case "byte":
            case "java.lang.Byte":
                return createNumberArbitrary(parameter, argument, (IntegerConstraints) constraint, "byte", "Byte", "bytes", "Byte.MIN_VALUE", "Byte.MAX_VALUE");
            case "short":
            case "java.lang.Short":
                return createNumberArbitrary(parameter, argument, (IntegerConstraints) constraint, "short", "Short", "shorts", "Short.MIN_VALUE", "Short.MAX_VALUE");
            case "int":
            case "java.lang.Integer":
                return createNumberArbitrary(parameter, argument, (IntegerConstraints) constraint, "int", "Integer", "integers", "Integer.MIN_VALUE", "Integer.MAX_VALUE");
            case "long":
            case "java.lang.Long":
                return createNumberArbitrary(parameter, argument, (IntegerConstraints) constraint, "long", "Long", "longs", "Long.MIN_VALUE", "Long.MAX_VALUE");
            case "float":
            case "java.lang.Float":
                return createRealArbitrary(parameter, argument, (RealConstraints) constraint, "float", "Float", "floats", "-Float.MAX_VALUE", "Float.MAX_VALUE");
            case "double":
            case "java.lang.Double":
                return createRealArbitrary(parameter, argument, (RealConstraints) constraint, "double", "Double", "doubles", "-Double.MAX_VALUE", "Double.MAX_VALUE");
            case "char":
            case "java.lang.Character":
                return createCharArbitrary(parameter, argument, (IntegerConstraints) constraint);
            default:
                throw new IllegalArgumentException("Unsupported numeric parameter type " + parameter.getType());
        }
    }

    private static String createCharArbitrary(MethodParameter parameter, Optional<Value> argument, IntegerConstraints constraint) {
        if (constraint == null) {
            return "return net.jqwik.api.Arbitraries.chars()";
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.just((char) (" + constraint.getEquality() + "))";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("char %s = Character.MIN_VALUE;%n", n.defaultMin()));
        result.append(String.format("char %s = Character.MAX_VALUE;%n", n.defaultMax()));
        result.append(String.format("java.util.List<Character> %s = java.util.Arrays.asList(%s%s);%n", n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> String.format(", %s", b)).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Character> %s = java.util.Arrays.asList(%s%s);%n", n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> String.format(", %s", b)).collect(Collectors.joining())));
        result.append(String.format("char %s = java.util.Collections.max(%s);%n", n.min(), n.lowerBounds()));
        result.append(String.format("char %s = java.util.Collections.min(%s);%n", n.max(), n.upperBounds()));
        if (argument.isPresent()) {
            String firstValue = new ModelToJavaTransformer().transform(argument.get());
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.just((char) (%s)); }%n", n.min(), n.max(), firstValue));
            result.append(String.format("return net.jqwik.api.Arbitraries.chars().range(%s, %s)", n.min(), n.max()));
        } else {
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.of(); }%n", n.min(), n.max()));
            result.append(String.format("return net.jqwik.api.Arbitraries.chars().range(%s, %s)", n.min(), n.max()));
        }
        return result.toString();
    }

    private static String createNumberArbitrary(
        MethodParameter parameter,
        Optional<Value> argument,
        IntegerConstraints constraint,
        String unboxedType,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue
    ) {
        if (constraint == null) {
            return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.just(" + String.format("(%s) (%s)", unboxedType, constraint.getEquality()) + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;%n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;%n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);%n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b)).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);%n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b)).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);%n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(String.format("%s %s = java.util.Collections.min(%s);%n", parameter.getType(), n.max(), n.upperBounds()));

        if (argument.isPresent()) {
            String firstValue = new ModelToJavaTransformer().transform(argument.get());
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), argument.get().getJavaType(), firstValue));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().between(%s, %s)", arbitraryType, n.min(), n.max()));
        } else {
            result.append(String.format("if (%s > %s) { return net.jqwik.api.Arbitraries.of(); }%n", n.min(), n.max()));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().between(%s, %s)", arbitraryType, n.min(), n.max()));
        }
        return result.toString();
    }

    private static String createRealArbitrary(
        MethodParameter parameter,
        Optional<Value> argument,
        RealConstraints constraint,
        String unboxedType,
        String boxedType,
        String arbitraryType,
        String minValue,
        String maxValue
    ) {
        if (constraint == null) {
            return String.format("return net.jqwik.api.Arbitraries.%s()", arbitraryType);
        } else if (constraint.getEquality() != null) {
            return "return net.jqwik.api.Arbitraries.just(" + String.format("(%s) (%s)", unboxedType, constraint.getEquality()) + ")";
        }

        Names n = new Names(parameter.getName());
        StringBuilder result = new StringBuilder();
        result.append(String.format("%s %s = %s;%n", parameter.getType(), n.defaultMin(), minValue));
        result.append(String.format("%s %s = %s;%n", parameter.getType(), n.defaultMax(), maxValue));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);%n", boxedType, n.lowerBounds(), n.defaultMin(), constraint.getLowerBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b.getValue())).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);%n", n.lowerBoundIncluded(), constraint.getLowerBounds().stream().map(RealBound::getIsIncluded).map(String::valueOf).map(b -> ", " + b).collect(Collectors.joining())));
        result.append(String.format("java.util.List<%s> %s = java.util.Arrays.asList(%s%s);%n", boxedType, n.upperBounds(), n.defaultMax(), constraint.getUpperBounds().stream().map(b -> String.format(", (%s) (%s)", unboxedType, b.getValue())).collect(Collectors.joining())));
        result.append(String.format("java.util.List<Boolean> %s = java.util.Arrays.asList(true%s);%n", n.upperBoundIncluded(), constraint.getUpperBounds().stream().map(RealBound::getIsIncluded).map(String::valueOf).map(b -> ", " + b).collect(Collectors.joining())));
        result.append(String.format("%s %s = java.util.Collections.max(%s);%n", parameter.getType(), n.min(), n.lowerBounds()));
        result.append(generateInclusionCheck(n, true));
        result.append(String.format("%s %s = java.util.Collections.min(%s);%n", parameter.getType(), n.max(), n.upperBounds()));
        result.append(generateInclusionCheck(n, false));
        result.append(String.format("int %s = 0;%n", n.scale()));
        result.append(String.format("if (!java.lang.Double.isNaN(%s) && !java.lang.Double.isInfinite(%s)) { %s = java.lang.Math.max(%s, java.lang.Math.max(0, java.math.BigDecimal.valueOf(%s).scale())); }%n", n.min(), n.min(), n.scale(), n.scale(), n.min()));
        result.append(String.format("if (!java.lang.Double.isNaN(%s) && !java.lang.Double.isInfinite(%s)) { %s = java.lang.Math.max(%s, java.lang.Math.max(0, java.math.BigDecimal.valueOf(%s).scale())); }%n", n.max(), n.max(), n.scale(), n.scale(), n.max()));

        if (argument.isPresent()) {
            String firstValue = new ModelToJavaTransformer().transform(argument.get());
            result.append(String.format("if ((%s > %s) || (%s == %s && (!%s || !%s))) { return net.jqwik.api.Arbitraries.just((%s) (%s)); }%n", n.min(), n.max(), n.min(), n.max(), n.minIncluded(), n.maxIncluded(), argument.get().getJavaType(), firstValue));
            result.append(String.format("return net.jqwik.api.Arbitraries.%s().ofScale(%s).between(%s, %s, %s, %s)", arbitraryType, n.scale(), n.min(), n.minIncluded(), n.max(), n.maxIncluded()));
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
        return String.format("boolean %s = true;%n", included)
            + String.format("for (int i = 0; i < %s.size(); i++) {%n", bounds)
            + String.format("    if (%s.get(i).equals(%s) && !%s.get(i)) {%n", bounds, value, boundIncluded)
            + String.format("        %s = false;%n", included)
            + "        break;\n"
            + "    }\n"
            + "}\n";
    }

    private static final class Names {
        private final String name;

        private Names(String name) {
            this.name = name;
        }

        private String defaultMin() { return this.name + "DefaultMin"; }
        private String defaultMax() { return this.name + "DefaultMax"; }
        private String lowerBounds() { return this.name + "LowerBounds"; }
        private String upperBounds() { return this.name + "UpperBounds"; }
        private String lowerBoundIncluded() { return this.name + "LowerBoundIncluded"; }
        private String upperBoundIncluded() { return this.name + "UpperBoundIncluded"; }
        private String min() { return this.name + "Min"; }
        private String max() { return this.name + "Max"; }
        private String minIncluded() { return this.name + "MinIncluded"; }
        private String maxIncluded() { return this.name + "MaxIncluded"; }
        private String scale() { return this.name + "Scale"; }
    }
}
