package teralizer.jqwik.planning;

import teralizer.domain.ConstantInteger;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.transformer.ModelToJavaTransformer;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class BooleanDomainPlanner implements DomainPlanner {

    @Override
    public boolean supports(TypeDomain domain) {
        return domain == TypeDomain.BOOLEAN;
    }

    @Override
    public ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context) {
        String name = parameter.getName();
        Optional<MethodArgument> argument = context.getArguments().containsKey(name)
            ? Optional.of(context.getArguments().get(name))
            : Optional.empty();

        Set<Boolean> derivedValues = new LinkedHashSet<>();
        Set<Integer> derivedIds = new LinkedHashSet<>();

        for (ConstraintClause clause : context.getClauses()) {
            Boolean derived = deriveBooleanValue(clause.getExpression(), name);
            if (derived != null) {
                derivedValues.add(derived);
                derivedIds.add(clause.getId());
            }
        }

        String body;
        Set<Integer> consumedIds;

        if (!derivedIds.isEmpty()) {
            if (derivedValues.size() == 1) {
                boolean v = derivedValues.iterator().next();
                body = "return net.jqwik.api.Arbitraries.just(" + v + ")";
            } else {
                body = "return net.jqwik.api.Arbitraries.of()";
            }
            consumedIds = derivedIds;
        } else if (argument.isPresent()) {
            MethodArgument arg = argument.get();
            String firstValue = new ModelToJavaTransformer().transform(arg);
            body = String.format(
                "return new FirstValueArbitrary<Boolean>((%s) (%s), net.jqwik.api.Arbitraries.of(true, false))",
                arg.getType(), firstValue);
            consumedIds = new LinkedHashSet<>();
        } else {
            body = "return net.jqwik.api.Arbitraries.of(true, false)";
            consumedIds = new LinkedHashSet<>();
        }

        return new ParameterGenerationPlan(parameter, TypeDomain.BOOLEAN, new RawJavaRecipe(body), consumedIds);
    }

    /**
     * Returns the required boolean value if the expression constrains this variable (by name) to
     * exactly true or false, or null if it is not a recognized boolean constraint for this variable.
     *
     * <p>Recognized patterns (both orientations, variable on either side):
     * <ul>
     *   <li>{@code b == 1} or {@code 1 == b} → true</li>
     *   <li>{@code b == 0} or {@code 0 == b} → false</li>
     *   <li>{@code b != 0} or {@code 0 != b} → true</li>
     *   <li>{@code b != 1} or {@code 1 != b} → false</li>
     * </ul>
     */
    private static Boolean deriveBooleanValue(Model expression, String paramName) {
        if (!(expression instanceof Operation)) {
            return null;
        }
        Operation operation = (Operation) expression;
        if (operation.op != Operator.EQ && operation.op != Operator.NE) {
            return null;
        }

        long constValue;
        boolean variableMatches;

        if (operation.left instanceof VariableInteger && operation.right instanceof ConstantInteger) {
            VariableInteger var = (VariableInteger) operation.left;
            ConstantInteger constant = (ConstantInteger) operation.right;
            variableMatches = var.name.equals(paramName);
            constValue = constant.value;
        } else if (operation.left instanceof ConstantInteger && operation.right instanceof VariableInteger) {
            ConstantInteger constant = (ConstantInteger) operation.left;
            VariableInteger var = (VariableInteger) operation.right;
            variableMatches = var.name.equals(paramName);
            constValue = constant.value;
        } else {
            return null;
        }

        if (!variableMatches || (constValue != 0 && constValue != 1)) {
            return null;
        }

        // EQ: b == 1 → true, b == 0 → false; NE: b != 0 → true, b != 1 → false
        if (operation.op == Operator.EQ) {
            return constValue == 1;
        } else {
            return constValue == 0;
        }
    }
}
