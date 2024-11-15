package teralizer.jqwik;

import teralizer.domain.*;

import java.util.*;

public class VariableConstraintExtractor extends ModelVisitor {

    private HashMap<String, VariableConstraints> constraints;

    public Map<String, VariableConstraints> process(teralizer.domain.Model model) {
        this.constraints = new HashMap<>();
        if (model != null) {
            model.accept(this);
        }
        return this.constraints;
    }

    @Override
    public void preVisit(Operation op) {
        if (op.left instanceof VariableInteger && op.right instanceof ConstantInteger) {
            this.updateConstraints((VariableInteger) op.left, op.op, (ConstantInteger) op.right);
        } else if (op.left instanceof ConstantInteger && op.right instanceof VariableInteger) {
            this.updateConstraints((ConstantInteger) op.left, op.op, (VariableInteger) op.right);
        } else if (op.left instanceof VariableReal && op.right instanceof ConstantReal) {
            this.updateConstraints((VariableReal) op.left, op.op, (ConstantReal) op.right);
        } else if (op.left instanceof ConstantReal && op.right instanceof VariableReal) {
            this.updateConstraints((ConstantReal) op.left, op.op, (VariableReal) op.right);
        }
    }

    private <T extends VariableConstraints> T getConstraint(Class<T> type, String variableName) {
        try {
            T constraint;
            if (this.constraints.containsKey(variableName)) {
                constraint = (T) this.constraints.get(variableName);
            } else {
                constraint = type.newInstance();
                constraint.setVariableName(variableName);
                this.constraints.put(variableName, constraint);
            }
            return constraint;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateConstraints(VariableInteger left, Operator operator, ConstantInteger right) {
        switch (operator) {
            case EQ:
                this.getConstraint(IntegerConstraints.class, left.name).addLowerBound(right.value, true);
                this.getConstraint(IntegerConstraints.class, left.name).addUpperBound(right.value, true);
                break;
            case LT:
                this.getConstraint(IntegerConstraints.class, left.name).addUpperBound(right.value, false);
                break;
            case LE:
                this.getConstraint(IntegerConstraints.class, left.name).addUpperBound(right.value, true);
                break;
            case GT:
                this.getConstraint(IntegerConstraints.class, left.name).addLowerBound(right.value, false);
                break;
            case GE:
                this.getConstraint(IntegerConstraints.class, left.name).addLowerBound(right.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantInteger left, Operator operator, VariableInteger right) {
        switch (operator) {
            case EQ:
                this.getConstraint(IntegerConstraints.class, right.name).addLowerBound(left.value, true);
                this.getConstraint(IntegerConstraints.class, right.name).addUpperBound(left.value, true);
                break;
            case LT:
                this.getConstraint(IntegerConstraints.class, right.name).addLowerBound(left.value, false);
                break;
            case LE:
                this.getConstraint(IntegerConstraints.class, right.name).addLowerBound(left.value, true);
                break;
            case GT:
                this.getConstraint(IntegerConstraints.class, right.name).addUpperBound(left.value, false);
                break;
            case GE:
                this.getConstraint(IntegerConstraints.class, right.name).addUpperBound(left.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, ConstantReal right) {
        switch (operator) {
            case EQ:
                this.getConstraint(RealConstraints.class, left.name).addLowerBound(right.value, true);
                this.getConstraint(RealConstraints.class, left.name).addUpperBound(right.value, true);
                break;
            case LT:
                this.getConstraint(RealConstraints.class, left.name).addUpperBound(right.value, false);
                break;
            case LE:
                this.getConstraint(RealConstraints.class, left.name).addUpperBound(right.value, true);
                break;
            case GT:
                this.getConstraint(RealConstraints.class, left.name).addLowerBound(right.value, false);
                break;
            case GE:
                this.getConstraint(RealConstraints.class, left.name).addLowerBound(right.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantReal left, Operator operator, VariableReal right) {
        switch (operator) {
            case EQ:
                this.getConstraint(RealConstraints.class, right.name).addLowerBound(left.value, true);
                this.getConstraint(RealConstraints.class, right.name).addUpperBound(left.value, true);
                break;
            case LT:
                this.getConstraint(RealConstraints.class, right.name).addLowerBound(left.value, false);
                break;
            case LE:
                this.getConstraint(RealConstraints.class, right.name).addLowerBound(left.value, true);
                break;
            case GT:
                this.getConstraint(RealConstraints.class, right.name).addUpperBound(left.value, false);
                break;
            case GE:
                this.getConstraint(RealConstraints.class, right.name).addUpperBound(left.value, true);
                break;
            default:
                // do nothing
        }
    }

    public interface VariableConstraints {
        void setVariableName(String variableName);
        String getVariableName();
        String getLowerBound();
        String getUpperBound();
    }

    public static class IntegerConstraints implements VariableConstraints {

        private String variableName;
        private final List<Long> lowerBounds = new ArrayList<>();
        private final List<Long> upperBounds = new ArrayList<>();

        @Override
        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public String getVariableName() {
            return this.variableName;
        }

        public void addLowerBound(long value, boolean isIncluded) {
            this.lowerBounds.add(value + (isIncluded ? 0 : 1));
        }

        public String getLowerBound() {
            return this.lowerBounds.isEmpty() ? null : String.valueOf(Collections.max(this.lowerBounds));
        }

        public void addUpperBound(long value, boolean isIncluded) {
            this.upperBounds.add(value + (isIncluded ? 0 : -1));
        }

        public String getUpperBound() {
            return this.upperBounds.isEmpty() ? null : String.valueOf(Collections.min(this.upperBounds));
        }
    }

    public static class RealConstraints implements VariableConstraints {

        private String variableName;
        private final List<Double> lowerBounds = new ArrayList<>();
        private final List<Double> upperBounds = new ArrayList<>();

        @Override
        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public String getVariableName() {
            return this.variableName;
        }

        public void addLowerBound(double value, boolean isIncluded) {
            this.lowerBounds.add(value + (isIncluded ? 0 : Double.MIN_VALUE));
        }

        public String getLowerBound() {
            return this.lowerBounds.isEmpty() ? null : String.valueOf(Collections.max(this.lowerBounds));
        }

        public void addUpperBound(double value, boolean isIncluded) {
            this.upperBounds.add(value + (isIncluded ? 0 : -Double.MIN_VALUE));
        }

        public String getUpperBound() {
            return this.upperBounds.isEmpty() ? null : String.valueOf(Collections.min(this.upperBounds));
        }
    }
}
