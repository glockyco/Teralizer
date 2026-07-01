package teralizer.spoon.generalization;

import static teralizer.util.Configuration.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.util.Configuration.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import teralizer.domain.MethodParameter;
import teralizer.jqwik.planning.InputGenerationPlan;
import teralizer.jqwik.planning.ParameterGenerationPlan;
import teralizer.spoon.SpoonUtils;

public class ImprovedTestParametersSupplierFactory {

    public static CtClass<?> createSupplierClass(
        Factory factory,
        List<MethodParameter> parameters,
        String inputJava,
        InputGenerationPlan plan
    ) {
        CtClass<?> supplierClass = factory.Class().create(TEST_PARAMETERS_SUPPLIER_CLASS_NAME);
        supplierClass.setSuperInterfaces(new HashSet<>(Collections.singletonList(factory.Type().createReference("net.jqwik.api.ArbitrarySupplier<" + TEST_PARAMETERS_CLASS_NAME + ">"))));
        supplierClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        String fullPredicate = plan.getFullPredicate();
        boolean applyInputFilter = plan.hasClauses();
        String originalTuple = buildOriginalTuple(plan.getParameterPlans());
        createGetMethod(supplierClass, parameters, fullPredicate, applyInputFilter, originalTuple);

        for (int i = 0; i < parameters.size(); i++) {
            ParameterGenerationPlan parameterPlan = plan.getParameterPlans().get(i);
            createGetParameterMethod(supplierClass, parameters.get(i), parameters.subList(0, i), parameterPlan);
        }

        return supplierClass;
    }

    private static void createGetMethod(
        CtClass<?> supplierClass,
        List<MethodParameter> parameters,
        String inputJava,
        boolean applyInputFilter,
        String originalTuple
    ) {
        Factory factory = supplierClass.getFactory();

        Set<ModifierKind> modifiers = new HashSet<>(Collections.singletonList(ModifierKind.PUBLIC));
        CtTypeReference<?> returnType = factory.Type().createReference("net.jqwik.api.Arbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">");

        CtMethod<?> supplierMethod = factory.Method().create(supplierClass, modifiers, returnType, "get", Collections.emptyList(), Collections.emptySet(), factory.Core().createBlock());

        if (parameters.isEmpty()) {
            supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement("return net.jqwik.api.Arbitraries.just((" + TEST_PARAMETERS_CLASS_NAME + ") null)"));
            return;
        }

        Function<List<MethodParameter>, String> paramNames = (List<MethodParameter> params) -> params.stream().map(MethodParameter::getName).collect(Collectors.joining(", "));

        // Combined tuple arbitrary, e.g.:
        //     get_x().flatMap(x -> get_y(x).flatMap(y -> get_z(x, y).map(z -> new TestParameters(x, y, z))))
        // The original input combination is injected once around this whole tuple (a tuple-level
        // FirstValueArbitrary), never per parameter: a per-parameter seed inside flatMap re-emits each
        // inner parameter's value on every outer draw and collapses inner-parameter diversity.
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter currentParameter = parameters.get(i);
            List<MethodParameter> previousParameters = parameters.subList(0, i);
            String previousParamNames = paramNames.apply(previousParameters);

            builder.append("get_").append(currentParameter.getName()).append("(").append(previousParamNames).append(")");

            if (i < parameters.size() - 1) {
                builder.append(".flatMap(new java.util.function.Function<");
                builder.append(SpoonUtils.getBoxedType(currentParameter.getType()));
                builder.append(", net.jqwik.api.Arbitrary<TestParameters>>() {\n");
                builder.append("    public net.jqwik.api.Arbitrary<TestParameters> apply(final ");
                builder.append(SpoonUtils.getBoxedType(currentParameter.getType())).append(" ");
                builder.append(currentParameter.getName()).append(") {\n");
                builder.append("        return ");
            } else {
                builder.append(".map(new java.util.function.Function<");
                builder.append(SpoonUtils.getBoxedType(currentParameter.getType()));
                builder.append(", TestParameters>() {\n");
                builder.append("    public TestParameters apply(final ");
                builder.append(SpoonUtils.getBoxedType(currentParameter.getType())).append(" ");
                builder.append(currentParameter.getName()).append(") {\n");
                builder.append("        return new ").append(TEST_PARAMETERS_CLASS_NAME);
                builder.append("(").append(paramNames.apply(parameters)).append(");\n");
                builder.append("    }\n");
                builder.append("})");

                for (int j = 0; j < i; j++) {
                    builder.append(";\n    }\n})");
                }
            }
        }

        String body = builder.toString();
        if (originalTuple != null) {
            body = "new FirstValueArbitrary<" + TEST_PARAMETERS_CLASS_NAME + ">(" + originalTuple + ", " + body + ")";
        }
        if (applyInputFilter) {
            body += "\n.filter(new java.util.function.Predicate<TestParameters>() {\n"
                + "    public boolean test(final TestParameters _p_) {\n"
                + "        return " + inputJava + ";\n"
                + "    }\n"
                + "})";
        }

        supplierMethod.getBody().addStatement(factory.createCodeSnippetStatement("return " + body));
    }

    private static String buildOriginalTuple(List<ParameterGenerationPlan> parameterPlans) {
        if (parameterPlans.isEmpty()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (ParameterGenerationPlan parameterPlan : parameterPlans) {
            if (parameterPlan.getOriginalValue() == null) {
                return null;
            }
            values.add(parameterPlan.getOriginalValue());
        }
        return "new " + TEST_PARAMETERS_CLASS_NAME + "(" + String.join(", ", values) + ")";
    }

    private static void createGetParameterMethod(
        CtClass<?> supplierClass,
        MethodParameter parameter,
        List<MethodParameter> previousParameters,
        ParameterGenerationPlan parameterPlan
    ) {
        Factory factory = supplierClass.getFactory();

        String body = parameterPlan.getRecipe().emit();
        String arbitraryType = SpoonUtils.getBoxedType(parameter.getType());

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
}
