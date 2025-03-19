package teralizer.jqwik;

import teralizer.domain.*;

import java.util.HashMap;
import java.util.List;

public class VariableConstraintExtractor extends ModelVisitor {

    private HashMap<String, Integer> parameterIds;
    private HashMap<String, String> parameterTypes;

    private int totalConstraintCount;
    private int usedConstraintCount;
    private HashMap<String, VariableConstraints> constraints;

    public VariableConstraintExtractionResult process(Model model, List<MethodParameter> allParameters) {
        this.parameterIds = new HashMap<>();
        this.parameterTypes = new HashMap<>();
        for (int i = 0; i < allParameters.size(); i++) {
            this.parameterIds.put(allParameters.get(i).getName(), i);
            this.parameterTypes.put(allParameters.get(i).getName(), allParameters.get(i).getType());
        }

        this.totalConstraintCount = 0;
        this.usedConstraintCount = 0;
        this.constraints = new HashMap<>();

        if (model != null) {
            this.totalConstraintCount++;
            model.accept(this);
        }

        return new VariableConstraintExtractionResult(this.totalConstraintCount, this.usedConstraintCount, this.constraints);
    }

    @Override
    public void preVisit(Operation op) {
        if (op.op == Operator.AND) {
            this.totalConstraintCount++;
        }

        if (op.left instanceof VariableInteger && op.right instanceof VariableInteger) {
            this.updateConstraints((VariableInteger) op.left, op.op, (VariableInteger) op.right);
        } else if (op.left instanceof VariableInteger && op.right instanceof ConstantInteger) {
            this.updateConstraints((VariableInteger) op.left, op.op, (ConstantInteger) op.right);
        } else if (op.left instanceof ConstantInteger && op.right instanceof VariableInteger) {
            this.updateConstraints((ConstantInteger) op.left, op.op, (VariableInteger) op.right);
        } else if (op.left instanceof VariableReal && op.right instanceof VariableReal) {
            this.updateConstraints((VariableReal) op.left, op.op, (VariableReal) op.right);
        } else if (op.left instanceof VariableReal && op.right instanceof ConstantReal) {
            this.updateConstraints((VariableReal) op.left, op.op, (ConstantReal) op.right);
        } else if (op.left instanceof ConstantReal && op.right instanceof VariableReal) {
            this.updateConstraints((ConstantReal) op.left, op.op, (VariableReal) op.right);
        } else if (op.left instanceof VariableInteger && op.right instanceof VariableReal) {
            this.updateConstraints((VariableInteger) op.left, op.op, (VariableReal) op.right);
        } else if (op.left instanceof VariableReal && op.right instanceof VariableInteger) {
            this.updateConstraints((VariableReal) op.left, op.op, (VariableInteger) op.right);
        }
    }

    private <T extends VariableConstraints> T getConstraint(Class<T> type, String variableName) {
        try {
            T constraint;
            if (this.constraints.containsKey(variableName)) {
                constraint = (T) this.constraints.get(variableName);
            } else {
                constraint = type.newInstance();
                constraint.setVariableType(this.parameterTypes.get(variableName));
                constraint.setVariableName(variableName);
                this.constraints.put(variableName, constraint);
            }
            return constraint;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateConstraints(VariableInteger left, Operator operator, VariableInteger right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    // left == right -> add equality
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableEquality(right.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // left < right -> add upper bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // left <= right -> add upper bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // left > right -> add lower bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // left >= right -> add lower bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    // right == left -> add equality
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableEquality(left.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // right > left -> add lower bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // right >= left -> add lower bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // right < left -> add upper bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // right <= left -> add upper bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
    }

    private void updateConstraints(VariableInteger left, Operator operator, ConstantInteger right) {
        switch (operator) {
            case EQ:
                // left == right -> add equality
                this.getConstraint(IntegerConstraints.class, left.name).addConstantEquality(right.value);
                this.usedConstraintCount++;
                break;
            case LT:
                // left < right -> add upper bound
                this.getConstraint(IntegerConstraints.class, left.name).addConstantUpperBound(right.value, false);
                this.usedConstraintCount++;
                break;
            case LE:
                // left <= right -> add upper bound
                this.getConstraint(IntegerConstraints.class, left.name).addConstantUpperBound(right.value, true);
                this.usedConstraintCount++;
                break;
            case GT:
                // left > right -> add lower bound
                this.getConstraint(IntegerConstraints.class, left.name).addConstantLowerBound(right.value, false);
                this.usedConstraintCount++;
                break;
            case GE:
                // left >= right -> add lower bound
                this.getConstraint(IntegerConstraints.class, left.name).addConstantLowerBound(right.value, true);
                this.usedConstraintCount++;
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantInteger left, Operator operator, VariableInteger right) {
        switch (operator) {
            case EQ:
                // right == left -> add equality
                this.getConstraint(IntegerConstraints.class, right.name).addConstantEquality(left.value);
                this.usedConstraintCount++;
                break;
            case LT:
                // right > left -> add lower bound
                this.getConstraint(IntegerConstraints.class, right.name).addConstantLowerBound(left.value, false);
                this.usedConstraintCount++;
                break;
            case LE:
                // right >= left -> add lower bound
                this.getConstraint(IntegerConstraints.class, right.name).addConstantLowerBound(left.value, true);
                this.usedConstraintCount++;
                break;
            case GT:
                // right < left -> add upper bound
                this.getConstraint(IntegerConstraints.class, right.name).addConstantUpperBound(left.value, false);
                this.usedConstraintCount++;
                break;
            case GE:
                // right <= left -> add upper bound
                this.getConstraint(IntegerConstraints.class, right.name).addConstantUpperBound(left.value, true);
                this.usedConstraintCount++;
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, VariableReal right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    // left == right -> add equality
                    this.getConstraint(RealConstraints.class, left.name).addVariableEquality(right.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // left < right -> add upper bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // left <= right -> add upper bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // left > right -> add lower bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // left >= right -> add lower bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    // right == left -> add equality
                    this.getConstraint(RealConstraints.class, right.name).addVariableEquality(left.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // right > left -> add lower bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // right >= left -> add lower bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // right < left -> add upper bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // right <= left -> add upper bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, ConstantReal right) {
        switch (operator) {
            case EQ:
                // left == right -> add equality
                this.getConstraint(RealConstraints.class, left.name).addConstantEquality(right.value);
                this.usedConstraintCount++;
                break;
            case LT:
                // left < right -> add upper bound
                this.getConstraint(RealConstraints.class, left.name).addConstantUpperBound(right.value, false);
                this.usedConstraintCount++;
                break;
            case LE:
                // left <= right -> add upper bound
                this.getConstraint(RealConstraints.class, left.name).addConstantUpperBound(right.value, true);
                this.usedConstraintCount++;
                break;
            case GT:
                // left > right -> add lower bound
                this.getConstraint(RealConstraints.class, left.name).addConstantLowerBound(right.value, false);
                this.usedConstraintCount++;
                break;
            case GE:
                // left >= right -> add lower bound
                this.getConstraint(RealConstraints.class, left.name).addConstantLowerBound(right.value, true);
                this.usedConstraintCount++;
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantReal left, Operator operator, VariableReal right) {
        switch (operator) {
            case EQ:
                // right == left -> add equality
                this.getConstraint(RealConstraints.class, right.name).addConstantEquality(left.value);
                this.usedConstraintCount++;
                break;
            case LT:
                // right > left -> add lower bound
                this.getConstraint(RealConstraints.class, right.name).addConstantLowerBound(left.value, false);
                this.usedConstraintCount++;
                break;
            case LE:
                // right >= left -> add lower bound
                this.getConstraint(RealConstraints.class, right.name).addConstantLowerBound(left.value, true);
                this.usedConstraintCount++;
                break;
            case GT:
                // right < left -> add upper bound
                this.getConstraint(RealConstraints.class, right.name).addConstantUpperBound(left.value, false);
                this.usedConstraintCount++;
                break;
            case GE:
                // right <= left -> add upper bound
                this.getConstraint(RealConstraints.class, right.name).addConstantUpperBound(left.value, true);
                this.usedConstraintCount++;
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(VariableInteger left, Operator operator, VariableReal right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    // left == right -> add equality
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableEquality(right.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // left < right -> add upper bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // left <= right -> add upper bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // left > right -> add lower bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // left >= right -> add lower bound
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    // right == left -> add equality
                    this.getConstraint(RealConstraints.class, right.name).addVariableEquality(left.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // right > left -> add lower bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // right >= left -> add lower bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // right < left -> add upper bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // right <= left -> add upper bound
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, VariableInteger right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    // left == right -> add equality
                    this.getConstraint(RealConstraints.class, left.name).addVariableEquality(right.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // left < right -> add upper bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // left <= right -> add upper bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // left > right -> add lower bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // left >= right -> add lower bound
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    // right == left -> add equality
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableEquality(left.name);
                    this.usedConstraintCount++;
                    break;
                case LT:
                    // right > left -> add lower bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case LE:
                    // right >= left -> add lower bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                case GT:
                    // right < left -> add upper bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    this.usedConstraintCount++;
                    break;
                case GE:
                    // right <= left -> add upper bound
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    this.usedConstraintCount++;
                    break;
                default:
                    // do nothing
            }
        }
    }
}
