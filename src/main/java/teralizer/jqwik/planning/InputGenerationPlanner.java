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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InputGenerationPlanner {
    private final List<DomainPlanner> domainPlanners = Arrays.asList(new NumericDomainPlanner());

    public InputGenerationPlan plan(List<MethodParameter> parameters, Model inputModel) {
        return this.plan(parameters, Collections.emptyMap(), inputModel);
    }

    public InputGenerationPlan plan(List<MethodParameter> parameters, Map<String, MethodArgument> arguments, Model inputModel) {
        Map<String, String> parameterTypes = new HashMap<>();
        Map<String, Integer> parameterIndexes = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            parameterTypes.put(parameter.getName(), parameter.getType());
            parameterIndexes.put(parameter.getName(), i);
        }

        List<ConstraintClause> clauses = ConstraintClauses.from(inputModel, parameterTypes);
        VariableConstraintExtractionResult extractionResult = new VariableConstraintExtractor().process(inputModel, parameters);
        Map<String, VariableConstraints> constraints = new HashMap<>(extractionResult.getConstraints());
        addAffineBounds(clauses, parameterTypes, parameterIndexes, constraints);
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

    private static void addAffineBounds(
        List<ConstraintClause> clauses,
        Map<String, String> parameterTypes,
        Map<String, Integer> parameterIndexes,
        Map<String, VariableConstraints> constraints
    ) {
        for (ConstraintClause clause : clauses) {
            if (clause.getExpression() instanceof Operation) {
                addAffineBound((Operation) clause.getExpression(), parameterTypes, parameterIndexes, constraints);
            }
        }
    }

    private static void addAffineBound(
        Operation comparison,
        Map<String, String> parameterTypes,
        Map<String, Integer> parameterIndexes,
        Map<String, VariableConstraints> constraints
    ) {
        if (!isComparison(comparison.op) || (!(comparison.left instanceof Operation) && !(comparison.right instanceof Operation))) {
            return;
        }
        AffineTerm left = AffineTerm.from(comparison.left);
        AffineTerm right = AffineTerm.from(comparison.right);
        if (left == null || right == null) {
            return;
        }
        AffineTerm normalized = left.minus(right);
        String currentName = highestIndexedVariable(normalized, parameterIndexes);
        if (currentName == null) {
            return;
        }
        int currentIndex = parameterIndexes.get(currentName);
        for (String variable : normalized.coefficients.keySet()) {
            if (!variable.equals(currentName) && parameterIndexes.getOrDefault(variable, currentIndex) >= currentIndex) {
                return;
            }
        }

        int currentCoefficient = normalized.coefficients.get(currentName);
        if (currentCoefficient != 1 && currentCoefficient != -1) {
            return;
        }
        String currentType = parameterTypes.get(currentName);
        TypeDomain currentDomain = TypeDomain.from(currentType);
        AffineTerm rest = normalized.without(currentName);
        Bound bound = currentCoefficient == 1
            ? new Bound(comparison.op, rest.negate())
            : new Bound(flip(comparison.op), rest);

        if (currentDomain == TypeDomain.INTEGER) {
            addIntegerBound(currentName, currentType, bound, constraints);
        } else if (currentDomain == TypeDomain.REAL) {
            addRealBound(currentName, currentType, bound, constraints);
        }
    }

    private static boolean isComparison(Operator operator) {
        return operator == Operator.EQ
            || operator == Operator.LT
            || operator == Operator.LE
            || operator == Operator.GT
            || operator == Operator.GE;
    }

    private static Operator flip(Operator operator) {
        switch (operator) {
            case LT: return Operator.GT;
            case LE: return Operator.GE;
            case GT: return Operator.LT;
            case GE: return Operator.LE;
            default: return operator;
        }
    }

    private static String highestIndexedVariable(AffineTerm term, Map<String, Integer> parameterIndexes) {
        String selected = null;
        int selectedIndex = -1;
        for (String variable : term.coefficients.keySet()) {
            Integer index = parameterIndexes.get(variable);
            if (index != null && index > selectedIndex) {
                selected = variable;
                selectedIndex = index;
            }
        }
        return selected;
    }

    private static void addIntegerBound(
        String name,
        String type,
        Bound bound,
        Map<String, VariableConstraints> constraints
    ) {
        IntegerConstraints integerConstraints = ensureIntegerConstraints(name, type, constraints);
        switch (bound.operator) {
            case EQ:
                integerConstraints.addEqualityExpression(bound.term.renderInteger());
                break;
            case LT:
                if (bound.term.constant == Long.MIN_VALUE) {
                    return;
                }
                integerConstraints.addUpperBoundExpression(bound.term.shiftIntegerConstant(-1).renderInteger());
                break;
            case LE:
                integerConstraints.addUpperBoundExpression(bound.term.renderInteger());
                break;
            case GT:
                if (bound.term.constant == Long.MAX_VALUE) {
                    return;
                }
                integerConstraints.addLowerBoundExpression(bound.term.shiftIntegerConstant(1).renderInteger());
                break;
            case GE:
                integerConstraints.addLowerBoundExpression(bound.term.renderInteger());
                break;
            default:
                break;
        }
    }

    private static void addRealBound(
        String name,
        String type,
        Bound bound,
        Map<String, VariableConstraints> constraints
    ) {
        RealConstraints realConstraints = ensureRealConstraints(name, type, constraints);
        switch (bound.operator) {
            case EQ:
                realConstraints.addEqualityExpression(bound.term.renderReal());
                break;
            case LT:
                realConstraints.addUpperBoundExpression(bound.term.renderReal(), false);
                break;
            case LE:
                realConstraints.addUpperBoundExpression(bound.term.renderReal(), true);
                break;
            case GT:
                realConstraints.addLowerBoundExpression(bound.term.renderReal(), false);
                break;
            case GE:
                realConstraints.addLowerBoundExpression(bound.term.renderReal(), true);
                break;
            default:
                break;
        }
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

    private static final class Bound {
        private final Operator operator;
        private final AffineTerm term;

        private Bound(Operator operator, AffineTerm term) {
            this.operator = operator;
            this.term = term;
        }
    }

    private static final class AffineTerm {
        private final LinkedHashMap<String, Integer> coefficients;
        private final long constant;
        private final boolean real;
        private final double realConstant;

        private AffineTerm(LinkedHashMap<String, Integer> coefficients, long constant, boolean real, double realConstant) {
            this.coefficients = coefficients;
            this.constant = constant;
            this.real = real;
            this.realConstant = realConstant;
        }

        private static AffineTerm from(Model model) {
            if (model instanceof VariableInteger) {
                return variable(((VariableInteger) model).name, false);
            }
            if (model instanceof VariableReal) {
                return variable(((VariableReal) model).name, true);
            }
            if (model instanceof ConstantInteger) {
                return integerConstant(((ConstantInteger) model).value);
            }
            if (model instanceof ConstantReal) {
                return realConstant(((ConstantReal) model).value);
            }
            if (model instanceof Operation) {
                Operation operation = (Operation) model;
                AffineTerm left = from(operation.left);
                AffineTerm right = from(operation.right);
                if (left == null || right == null) {
                    return null;
                }
                if (operation.op == Operator.PLUS) {
                    return left.plus(right);
                }
                if (operation.op == Operator.MINUS) {
                    return left.minus(right);
                }
            }
            return null;
        }

        private static AffineTerm variable(String name, boolean real) {
            LinkedHashMap<String, Integer> coefficients = new LinkedHashMap<>();
            coefficients.put(name, 1);
            return new AffineTerm(coefficients, 0, real, 0.0);
        }

        private static AffineTerm integerConstant(long value) {
            return new AffineTerm(new LinkedHashMap<>(), value, false, value);
        }

        private static AffineTerm realConstant(double value) {
            return new AffineTerm(new LinkedHashMap<>(), (long) value, true, value);
        }

        private AffineTerm plus(AffineTerm other) {
            LinkedHashMap<String, Integer> result = new LinkedHashMap<>(this.coefficients);
            other.coefficients.forEach((name, coefficient) -> result.merge(name, coefficient, Integer::sum));
            result.entrySet().removeIf(entry -> entry.getValue() == 0);
            return new AffineTerm(result, this.constant + other.constant, this.real || other.real, this.realConstant + other.realConstant);
        }

        private AffineTerm minus(AffineTerm other) {
            return this.plus(other.negate());
        }

        private AffineTerm negate() {
            LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
            this.coefficients.forEach((name, coefficient) -> result.put(name, -coefficient));
            return new AffineTerm(result, -this.constant, this.real, -this.realConstant);
        }

        private AffineTerm without(String variable) {
            LinkedHashMap<String, Integer> result = new LinkedHashMap<>(this.coefficients);
            result.remove(variable);
            return new AffineTerm(result, this.constant, this.real, this.realConstant);
        }

        private AffineTerm shiftIntegerConstant(long delta) {
            return new AffineTerm(new LinkedHashMap<>(this.coefficients), this.constant + delta, this.real, this.realConstant + delta);
        }

        private String renderInteger() {
            return render(false);
        }

        private String renderReal() {
            return render(true);
        }

        private String render(boolean forceReal) {
            List<String> variableParts = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : this.coefficients.entrySet()) {
                int coefficient = entry.getValue();
                if (coefficient == 1) {
                    variableParts.add(entry.getKey());
                } else if (coefficient == -1) {
                    variableParts.add("-" + entry.getKey());
                } else {
                    variableParts.add(coefficient + " * " + entry.getKey());
                }
            }
            String constantText = forceReal ? Double.toString(this.realConstant) : Long.toString(this.constant);
            boolean renderConstant = forceReal ? this.realConstant != 0.0 : this.constant != 0;
            if (variableParts.isEmpty()) {
                return constantText;
            }

            List<String> parts = new ArrayList<>();
            boolean positiveConstantWithNegativeVariable = renderConstant
                && (forceReal ? this.realConstant > 0.0 : this.constant > 0)
                && variableParts.get(0).startsWith("-");
            if (positiveConstantWithNegativeVariable) {
                parts.add(constantText);
            }
            parts.addAll(variableParts);
            if (renderConstant && !positiveConstantWithNegativeVariable) {
                parts.add(constantText);
            }
            return join(parts);
        }

        private static String join(List<String> parts) {
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (builder.length() == 0) {
                    builder.append(part.startsWith("-") ? "-" + part.substring(1) : part);
                } else if (part.startsWith("-")) {
                    builder.append(" - ").append(part.substring(1));
                } else {
                    builder.append(" + ").append(part);
                }
            }
            return builder.toString();
        }
    }
}
