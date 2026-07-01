package teralizer.jqwik.planning;

import teralizer.domain.Constant;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Value;
import teralizer.domain.Variable;
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
    public boolean supportsReturn(TypeDomain domain) {
        return supports(domain);
    }

    @Override
    public ParameterGenerationPlan plan(MethodParameter parameter, PlanningContext context) {
        String name = parameter.getName();
        Optional<Value> argument = context.getArguments().containsKey(name)
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
        } else {
            body = "return net.jqwik.api.Arbitraries.of(true, false)";
            consumedIds = new LinkedHashSet<>();
        }

        String originalValue = argument
            .map(arg -> "(" + arg.getJavaType() + ") (" + new ModelToJavaTransformer().transform(arg) + ")")
            .orElse(null);
        return new ParameterGenerationPlan(parameter, TypeDomain.BOOLEAN, new RawJavaRecipe(body), originalValue, consumedIds);
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

        if (operation.left instanceof Variable && ((Variable) operation.left).domain == TypeDomain.INTEGER
            && operation.right instanceof Constant && ((Constant) operation.right).domain == TypeDomain.INTEGER) {
            Variable var = (Variable) operation.left;
            Constant constant = (Constant) operation.right;
            variableMatches = var.name.equals(paramName);
            constValue = ((Number) constant.value).longValue();
        } else if (operation.left instanceof Constant && ((Constant) operation.left).domain == TypeDomain.INTEGER
            && operation.right instanceof Variable && ((Variable) operation.right).domain == TypeDomain.INTEGER) {
            Constant constant = (Constant) operation.left;
            Variable var = (Variable) operation.right;
            variableMatches = var.name.equals(paramName);
            constValue = ((Number) constant.value).longValue();
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
