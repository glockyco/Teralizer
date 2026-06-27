package teralizer.jqwik.planning;

import teralizer.domain.ConstantInteger;
import teralizer.domain.ConstantReal;
import teralizer.domain.MethodArgument;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.VariableInteger;
import teralizer.domain.VariableReal;
import teralizer.jqwik.IntegerConstraints;
import teralizer.jqwik.RealConstraints;
import teralizer.jqwik.VariableConstraintExtractionResult;
import teralizer.jqwik.VariableConstraintExtractor;
import teralizer.jqwik.VariableConstraints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InputGenerationPlanner {
    private final List<DomainPlanner> domainPlanners = Arrays.asList(new NumericDomainPlanner());

    public InputGenerationPlan plan(List<MethodParameter> parameters, Model inputModel) {
        return this.plan(parameters, Collections.emptyMap(), inputModel);
    }

    public InputGenerationPlan plan(List<MethodParameter> parameters, Map<String, MethodArgument> arguments, Model inputModel) {
        Map<String, String> parameterTypes = new HashMap<>();
        for (MethodParameter parameter : parameters) {
            parameterTypes.put(parameter.getName(), parameter.getType());
        }

        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes);
        VariableConstraintExtractionResult extractionResult = new VariableConstraintExtractor().process(inputModel, parameters);
        Map<String, VariableConstraints> constraints = new HashMap<>(extractionResult.getConstraints());
        Map<String, Integer> parameterIndexes = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            parameterIndexes.put(parameters.get(i).getName(), i);
        }
        addSimpleAffineBounds(inputModel, parameterTypes, parameterIndexes, constraints);
        PlanningContext context = new PlanningContext(parameters, clauses, arguments, constraints);
        List<ParameterGenerationPlan> parameterPlans = new ArrayList<>();
        for (MethodParameter parameter : parameters) {
            TypeDomain domain = TypeDomain.from(parameter.getType());
            parameterPlans.add(this.planParameter(parameter, domain, context));
        }
        return new InputGenerationPlan(parameterPlans, context.getClauses(), Collections.emptySet());
    }

    private ParameterGenerationPlan planParameter(MethodParameter parameter, TypeDomain domain, PlanningContext context) {
        for (DomainPlanner domainPlanner : this.domainPlanners) {
            if (domainPlanner.supports(domain)) {
                return domainPlanner.plan(parameter, context);
            }
        }
        return new ParameterGenerationPlan(parameter, domain, new RawJavaRecipe(defaultRecipe(parameter, domain)), Collections.emptySet());
    }

    private static void addSimpleAffineBounds(
        Model inputModel,
        Map<String, String> parameterTypes,
        Map<String, Integer> parameterIndexes,
        Map<String, VariableConstraints> constraints
    ) {
        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes);
        for (ConstraintClause clause : clauses) {
            if (clause.getExpression() instanceof Operation) {
                addSimpleAffineBound((Operation) clause.getExpression(), parameterTypes, parameterIndexes, constraints);
            }
        }
    }

    private static void addSimpleAffineBound(
        Operation comparison,
        Map<String, String> parameterTypes,
        Map<String, Integer> parameterIndexes,
        Map<String, VariableConstraints> constraints
    ) {
        if (!(comparison.left instanceof Operation)) {
            return;
        }
        Operation sum = (Operation) comparison.left;
        if (sum.op != Operator.PLUS || !(sum.left instanceof Model) || !(sum.right instanceof Model)) {
            return;
        }
        if (!(comparison.right instanceof ConstantInteger) && !(comparison.right instanceof ConstantReal)) {
            return;
        }

        String leftName = variableName(sum.left);
        String rightName = variableName(sum.right);
        if (leftName == null || rightName == null) {
            return;
        }
        Integer leftIndex = parameterIndexes.get(leftName);
        Integer rightIndex = parameterIndexes.get(rightName);
        if (leftIndex == null || rightIndex == null || leftIndex.equals(rightIndex)) {
            return;
        }

        String currentName = leftIndex > rightIndex ? leftName : rightName;
        String previousName = leftIndex > rightIndex ? rightName : leftName;
        String currentType = parameterTypes.get(currentName);
        TypeDomain currentDomain = TypeDomain.from(currentType);
        if (comparison.op != Operator.LT && comparison.op != Operator.LE) {
            return;
        }

        if (currentDomain == TypeDomain.INTEGER && comparison.right instanceof ConstantInteger) {
            IntegerConstraints integerConstraints = ensureIntegerConstraints(currentName, currentType, constraints);
            long value = ((ConstantInteger) comparison.right).value;
            String expression = comparison.op == Operator.LT
                ? value + " - " + previousName + " - 1"
                : value + " - " + previousName;
            integerConstraints.addUpperBoundExpression(expression);
        } else if (currentDomain == TypeDomain.REAL && comparison.right instanceof ConstantReal) {
            RealConstraints realConstraints = ensureRealConstraints(currentName, currentType, constraints);
            double value = ((ConstantReal) comparison.right).value;
            realConstraints.addUpperBoundExpression(value + " - " + previousName, comparison.op == Operator.LE);
        }
    }

    private static String variableName(Model model) {
        if (model instanceof VariableInteger) {
            return ((VariableInteger) model).name;
        }
        if (model instanceof VariableReal) {
            return ((VariableReal) model).name;
        }
        return null;
    }

    private static IntegerConstraints ensureIntegerConstraints(String name, String type, Map<String, VariableConstraints> constraints) {
        VariableConstraints existing = constraints.get(name);
        if (existing instanceof IntegerConstraints) {
            return (IntegerConstraints) existing;
        }
        IntegerConstraints created = new IntegerConstraints();
        created.setVariableName(name);
        created.setVariableType(type);
        constraints.put(name, created);
        return created;
    }

    private static RealConstraints ensureRealConstraints(String name, String type, Map<String, VariableConstraints> constraints) {
        VariableConstraints existing = constraints.get(name);
        if (existing instanceof RealConstraints) {
            return (RealConstraints) existing;
        }
        RealConstraints created = new RealConstraints();
        created.setVariableName(name);
        created.setVariableType(type);
        constraints.put(name, created);
        return created;
    }

    private static String defaultRecipe(MethodParameter parameter, TypeDomain domain) {
        switch (domain) {
            case BOOLEAN:
                return "return net.jqwik.api.Arbitraries.of(true, false)";
            case STRING:
                return "return net.jqwik.api.Arbitraries.strings()";
            case ARRAY:
            case OBJECT:
            default:
                return "return net.jqwik.api.Arbitraries.just((" + parameter.getType() + ") null)";
        }
    }
}
