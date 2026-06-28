package teralizer.spoon.generalization;

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

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static teralizer.util.Configuration.TEST_PARAMETERS_CLASS_NAME;
import static teralizer.util.Configuration.TEST_PARAMETERS_SUPPLIER_CLASS_NAME;

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

        String residualPredicate = plan.getResidualPredicate();
        boolean applyInputFilter = plan.hasResidualClauses();
        createGetMethod(supplierClass, parameters, residualPredicate, applyInputFilter);

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
