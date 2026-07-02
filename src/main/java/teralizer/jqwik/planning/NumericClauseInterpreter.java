package teralizer.jqwik.planning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import teralizer.domain.Constant;
import teralizer.domain.Expression;
import teralizer.domain.MethodParameter;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.domain.TypeDomain;
import teralizer.domain.Variable;
import teralizer.jqwik.IntegerConstraints;
import teralizer.jqwik.RealConstraints;
import teralizer.jqwik.VariableConstraints;

/**
 * Single source of truth for turning numeric input clauses into per-parameter {@link VariableConstraints}
 * (the bounds the recipe renderer consumes) plus the ids of the clauses that produced them.
 *
 * <p>Atomic comparisons (operands that are directly typed integer/real {@code Variable}/
 * {@code Constant} leaves) reproduce the operator-&gt;bound mapping and the
 * "bound the higher-indexed variable only" rule for var/var comparisons. Compound affine
 * comparisons (one side is an {@code Operation} that normalizes to an affine term) reproduce the
 * highest-indexed-variable, coefficient-&plusmn;1, and overflow-guard logic that used to live in
 * {@code InputGenerationPlanner}. The two sweeps run in the original order (all atomic bounds, then
 * all affine bounds) so the accumulated bound lists -- and therefore the rendered recipes -- are
 * byte-identical to the previous extractor-then-affine pipeline.
 *
 * <p>This interpreter is the single source of truth for numeric clause→bound semantics.
 * It replaces the former VariableConstraintExtractor, which only recognized atomic
 * comparisons and powered the DB constraint-count metrics. The interpreter handles both
 * atomic and affine bounds, and the plan-level consumed-clause ids feed the DB metrics
 * via InputGenerationPlan in TestGeneralizationTask.
 */
public final class NumericClauseInterpreter {
    private NumericClauseInterpreter() {
    }

    public static Map<String, NumericClauseInterpretation> interpret(List<ConstraintClause> clauses, List<MethodParameter> parameters) {
        Map<String, Integer> parameterIndexes = new HashMap<>();
        Map<String, String> parameterTypes = new HashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            MethodParameter parameter = parameters.get(i);
            parameterIndexes.put(parameter.getName(), i);
            parameterTypes.put(parameter.getName(), parameter.getType());
        }

        Map<String, VariableConstraints> constraints = new HashMap<>();
        Map<String, Set<Integer>> consumed = new HashMap<>();

        // Pass 1: atomic comparisons over the flattened clauses.
        for (ConstraintClause clause : clauses) {
            if (clause.getExpression() instanceof Operation) {
                interpretAtomic((Operation) clause.getExpression(), clause.getId(), parameterIndexes, parameterTypes, constraints, consumed);
            }
        }

        // Pass 2: compound affine comparisons (reproduces the former InputGenerationPlanner affine pre-pass).
        for (ConstraintClause clause : clauses) {
            if (clause.getExpression() instanceof Operation) {
                addAffineBound((Operation) clause.getExpression(), clause.getId(), parameterTypes, parameterIndexes, constraints, consumed);
            }
        }

        Map<String, NumericClauseInterpretation> interpretations = new HashMap<>();
        for (MethodParameter parameter : parameters) {
            String name = parameter.getName();
            interpretations.put(name, new NumericClauseInterpretation(
                constraints.get(name),
                consumed.getOrDefault(name, Collections.emptySet())
            ));
        }
        return interpretations;
    }

    private static void interpretAtomic(
        Operation operation,
        int clauseId,
        Map<String, Integer> parameterIndexes,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        if (!isComparison(operation.op)) {
            return;
        }
        Expression left = operation.left;
        Expression right = operation.right;
        Operator op = operation.op;
        Variable leftVariable = numericVariable(left);
        Variable rightVariable = numericVariable(right);
        Constant leftConstant = numericConstant(left);
        Constant rightConstant = numericConstant(right);

        if (leftVariable != null && rightVariable != null) {
            applyVariableComparison(leftVariable.name, isReal(leftVariable), op, rightVariable.name, isReal(rightVariable), clauseId, parameterIndexes, parameterTypes, constraints, consumed);
        } else if (leftVariable != null && rightConstant != null) {
            applyConstantComparison(leftVariable, op, rightConstant, clauseId, parameterTypes, constraints, consumed);
        } else if (leftConstant != null && rightVariable != null) {
            applyConstantComparison(rightVariable, flip(op), leftConstant, clauseId, parameterTypes, constraints, consumed);
        }
    }

    private static Variable numericVariable(Expression expression) {
        if (!(expression instanceof Variable)) {
            return null;
        }
        Variable variable = (Variable) expression;
        return variable.domain == TypeDomain.INTEGER || variable.domain == TypeDomain.REAL ? variable : null;
    }

    private static Constant numericConstant(Expression expression) {
        if (!(expression instanceof Constant)) {
            return null;
        }
        Constant constant = (Constant) expression;
        return constant.domain == TypeDomain.INTEGER || constant.domain == TypeDomain.REAL ? constant : null;
    }

    private static boolean isReal(Variable variable) {
        return variable.domain == TypeDomain.REAL;
    }

    private static void applyConstantComparison(
        Variable variable,
        Operator op,
        Constant constant,
        int clauseId,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        if (variable.domain == TypeDomain.INTEGER && constant.domain == TypeDomain.INTEGER) {
            applyIntegerConstant(variable.name, op, ((Number) constant.value).longValue(), clauseId, parameterTypes, constraints, consumed);
        } else if (variable.domain == TypeDomain.REAL && constant.domain == TypeDomain.REAL) {
            applyRealConstant(variable.name, op, ((Number) constant.value).doubleValue(), clauseId, parameterTypes, constraints, consumed);
        }
    }

    /**
     * Bounds the higher-indexed variable only, matching the index comparison in the
     * var/var comparison logic. The flag for each operand records whether its model node
     * is real ({@code TypeDomain.REAL}) so the bound is stored in the right constraint kind
     * regardless of the parameter's declared type.
     */
    private static void applyVariableComparison(
        String leftName,
        boolean leftReal,
        Operator op,
        String rightName,
        boolean rightReal,
        int clauseId,
        Map<String, Integer> parameterIndexes,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        Integer leftIndex = parameterIndexes.get(leftName);
        Integer rightIndex = parameterIndexes.get(rightName);
        if (leftIndex == null || rightIndex == null) {
            return;
        }
        if (leftIndex > rightIndex) {
            applyVariableBound(leftName, leftReal, op, rightName, clauseId, parameterTypes, constraints, consumed);
        } else if (rightIndex > leftIndex) {
            applyVariableBound(rightName, rightReal, flip(op), leftName, clauseId, parameterTypes, constraints, consumed);
        }
    }

    private static void applyVariableBound(
        String targetName,
        boolean targetReal,
        Operator op,
        String comparand,
        int clauseId,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        if (!isNumericDomain(parameterTypes.get(targetName))) {
            return;
        }
        if (targetReal) {
            RealConstraints target = ensureRealConstraints(targetName, parameterTypes.get(targetName), constraints);
            if (applyRealVariableBound(target, op, comparand)) {
                recordConsumed(consumed, targetName, clauseId);
            }
        } else {
            IntegerConstraints target = ensureIntegerConstraints(targetName, parameterTypes.get(targetName), constraints);
            if (applyIntegerVariableBound(target, op, comparand)) {
                recordConsumed(consumed, targetName, clauseId);
            }
        }
    }

    private static void applyIntegerConstant(
        String name,
        Operator op,
        long value,
        int clauseId,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        if (!isNumericDomain(parameterTypes.get(name))) {
            return;
        }
        IntegerConstraints target = ensureIntegerConstraints(name, parameterTypes.get(name), constraints);
        if (applyIntegerConstantBound(target, op, value)) {
            recordConsumed(consumed, name, clauseId);
        }
    }

    private static void applyRealConstant(
        String name,
        Operator op,
        double value,
        int clauseId,
        Map<String, String> parameterTypes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        if (!isNumericDomain(parameterTypes.get(name))) {
            return;
        }
        RealConstraints target = ensureRealConstraints(name, parameterTypes.get(name), constraints);
        if (applyRealConstantBound(target, op, value)) {
            recordConsumed(consumed, name, clauseId);
        }
    }

    private static boolean applyIntegerConstantBound(IntegerConstraints constraints, Operator op, long value) {
        switch (op) {
            case EQ:
                constraints.addConstantEquality(value);
                return true;
            case LT:
                constraints.addConstantUpperBound(value, false);
                return true;
            case LE:
                constraints.addConstantUpperBound(value, true);
                return true;
            case GT:
                constraints.addConstantLowerBound(value, false);
                return true;
            case GE:
                constraints.addConstantLowerBound(value, true);
                return true;
            default:
                return false;
        }
    }

    private static boolean applyRealConstantBound(RealConstraints constraints, Operator op, double value) {
        if (Double.isNaN(value)) {
            return false;
        }
        switch (op) {
            case EQ:
                constraints.addConstantEquality(value);
                return true;
            case LT:
                constraints.addConstantUpperBound(value, false);
                return true;
            case LE:
                constraints.addConstantUpperBound(value, true);
                return true;
            case GT:
                constraints.addConstantLowerBound(value, false);
                return true;
            case GE:
                constraints.addConstantLowerBound(value, true);
                return true;
            default:
                return false;
        }
    }

    private static boolean applyIntegerVariableBound(IntegerConstraints constraints, Operator op, String comparand) {
        switch (op) {
            case EQ:
                constraints.addVariableEquality(comparand);
                return true;
            case LT:
                constraints.addVariableUpperBound(comparand, false);
                return true;
            case LE:
                constraints.addVariableUpperBound(comparand, true);
                return true;
            case GT:
                constraints.addVariableLowerBound(comparand, false);
                return true;
            case GE:
                constraints.addVariableLowerBound(comparand, true);
                return true;
            default:
                return false;
        }
    }

    private static boolean applyRealVariableBound(RealConstraints constraints, Operator op, String comparand) {
        switch (op) {
            case EQ:
                constraints.addVariableEquality(comparand);
                return true;
            case LT:
                constraints.addVariableUpperBound(comparand, false);
                return true;
            case LE:
                constraints.addVariableUpperBound(comparand, true);
                return true;
            case GT:
                constraints.addVariableLowerBound(comparand, false);
                return true;
            case GE:
                constraints.addVariableLowerBound(comparand, true);
                return true;
            default:
                return false;
        }
    }

    private static void addAffineBound(
        Operation comparison,
        int clauseId,
        Map<String, String> parameterTypes,
        Map<String, Integer> parameterIndexes,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
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
            addIntegerBound(currentName, currentType, bound, clauseId, constraints, consumed);
        } else if (currentDomain == TypeDomain.REAL) {
            addRealBound(currentName, currentType, bound, clauseId, constraints, consumed);
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
        int clauseId,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        IntegerConstraints integerConstraints = ensureIntegerConstraints(name, type, constraints);
        switch (bound.operator) {
            case EQ:
                integerConstraints.addEqualityExpression(bound.term.renderInteger());
                recordConsumed(consumed, name, clauseId);
                break;
            case LT:
                if (bound.term.constant == Long.MIN_VALUE) {
                    return;
                }
                integerConstraints.addUpperBoundExpression(bound.term.shiftIntegerConstant(-1).renderInteger());
                recordConsumed(consumed, name, clauseId);
                break;
            case LE:
                integerConstraints.addUpperBoundExpression(bound.term.renderInteger());
                recordConsumed(consumed, name, clauseId);
                break;
            case GT:
                if (bound.term.constant == Long.MAX_VALUE) {
                    return;
                }
                integerConstraints.addLowerBoundExpression(bound.term.shiftIntegerConstant(1).renderInteger());
                recordConsumed(consumed, name, clauseId);
                break;
            case GE:
                integerConstraints.addLowerBoundExpression(bound.term.renderInteger());
                recordConsumed(consumed, name, clauseId);
                break;
            default:
                break;
        }
    }

    private static void addRealBound(
        String name,
        String type,
        Bound bound,
        int clauseId,
        Map<String, VariableConstraints> constraints,
        Map<String, Set<Integer>> consumed
    ) {
        RealConstraints realConstraints = ensureRealConstraints(name, type, constraints);
        switch (bound.operator) {
            case EQ:
                realConstraints.addEqualityExpression(bound.term.renderReal());
                recordConsumed(consumed, name, clauseId);
                break;
            case LT:
                realConstraints.addUpperBoundExpression(bound.term.renderReal(), false);
                recordConsumed(consumed, name, clauseId);
                break;
            case LE:
                realConstraints.addUpperBoundExpression(bound.term.renderReal(), true);
                recordConsumed(consumed, name, clauseId);
                break;
            case GT:
                realConstraints.addLowerBoundExpression(bound.term.renderReal(), false);
                recordConsumed(consumed, name, clauseId);
                break;
            case GE:
                realConstraints.addLowerBoundExpression(bound.term.renderReal(), true);
                recordConsumed(consumed, name, clauseId);
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

    private static void recordConsumed(Map<String, Set<Integer>> consumed, String name, int clauseId) {
        consumed.computeIfAbsent(name, key -> new LinkedHashSet<>()).add(clauseId);
    }

    private static boolean isNumericDomain(String type) {
        TypeDomain domain = TypeDomain.from(type);
        return domain == TypeDomain.INTEGER || domain == TypeDomain.REAL || domain == TypeDomain.CHAR;
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
            if (model instanceof Variable) {
                Variable variable = (Variable) model;
                if (variable.domain == TypeDomain.INTEGER || variable.domain == TypeDomain.REAL) {
                    return variable(variable.name, variable.domain == TypeDomain.REAL);
                }
                return null;
            }
            if (model instanceof Constant) {
                Constant constant = (Constant) model;
                if (constant.domain == TypeDomain.INTEGER) {
                    return integerConstant(((Number) constant.value).longValue());
                }
                if (constant.domain == TypeDomain.REAL) {
                    return realConstant(((Number) constant.value).doubleValue());
                }
                return null;
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
