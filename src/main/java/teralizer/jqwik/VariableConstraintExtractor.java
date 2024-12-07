package teralizer.jqwik;

import teralizer.domain.*;

import java.util.*;

public class VariableConstraintExtractor extends ModelVisitor {

    private HashMap<String, VariableConstraints> constraints;
    private HashMap<String, Integer> parameterIds;

    public Map<String, VariableConstraints> process(Model model, List<MethodParameter> allParameters) {
        this.parameterIds = new HashMap<>();
        for (int i = 0; i < allParameters.size(); i++) {
            this.parameterIds.put(allParameters.get(i).getName(), i);
        }

        this.constraints = new HashMap<>();
        if (model != null) {
            model.accept(this);
        }
        return this.constraints;
    }

    @Override
    public void preVisit(Operation op) {
        if (op.left instanceof VariableInteger && op.right instanceof VariableInteger) {
            this.updateConstraints((VariableInteger) op.left, op.op, (VariableInteger) op.right);
        } else if (op.left instanceof VariableInteger && op.right instanceof ConstantInteger) {
            this.updateConstraints((VariableInteger) op.left, op.op, (ConstantInteger) op.right);
        } else if (op.left instanceof ConstantInteger && op.right instanceof VariableInteger) {
            this.updateConstraints((ConstantInteger) op.left, op.op, (VariableInteger) op.right);
        } else if (op.left instanceof VariableReal && op.right instanceof VariableReal){
            this.updateConstraints((VariableReal) op.left, op.op, (VariableReal) op.right);
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

    private void updateConstraints(VariableInteger left, Operator operator, VariableInteger right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableEquality(right.name);
                    break;
                case LT:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    break;
                case LE:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    break;
                case GT:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    break;
                case GE:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableEquality(left.name);
                    break;
                case LT:
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    break;
                case LE:
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    break;
                case GT:
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    break;
                case GE:
                    this.getConstraint(IntegerConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    break;
                default:
                    // do nothing
            }
        }
    }

    private void updateConstraints(VariableInteger left, Operator operator, ConstantInteger right) {
        switch (operator) {
            case EQ:
                this.getConstraint(IntegerConstraints.class, left.name).addConstantEquality(right.value);
                break;
            case LT:
                this.getConstraint(IntegerConstraints.class, left.name).addConstantUpperBound(right.value, false);
                break;
            case LE:
                this.getConstraint(IntegerConstraints.class, left.name).addConstantUpperBound(right.value, true);
                break;
            case GT:
                this.getConstraint(IntegerConstraints.class, left.name).addConstantLowerBound(right.value, false);
                break;
            case GE:
                this.getConstraint(IntegerConstraints.class, left.name).addConstantLowerBound(right.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantInteger left, Operator operator, VariableInteger right) {
        switch (operator) {
            case EQ:
                this.getConstraint(IntegerConstraints.class, right.name).addConstantEquality(left.value);
                break;
            case LT:
                this.getConstraint(IntegerConstraints.class, right.name).addConstantLowerBound(left.value, false);
                break;
            case LE:
                this.getConstraint(IntegerConstraints.class, right.name).addConstantLowerBound(left.value, true);
                break;
            case GT:
                this.getConstraint(IntegerConstraints.class, right.name).addConstantUpperBound(left.value, false);
                break;
            case GE:
                this.getConstraint(IntegerConstraints.class, right.name).addConstantUpperBound(left.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, VariableReal right) {
        if (this.parameterIds.get(left.name) > this.parameterIds.get(right.name)) {
            switch (operator) {
                case EQ:
                    this.getConstraint(RealConstraints.class, left.name).addVariableEquality(right.name);
                    break;
                case LT:
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, false);
                    break;
                case LE:
                    this.getConstraint(RealConstraints.class, left.name).addVariableUpperBound(right.name, true);
                    break;
                case GT:
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, false);
                    break;
                case GE:
                    this.getConstraint(RealConstraints.class, left.name).addVariableLowerBound(right.name, true);
                    break;
                default:
                    // do nothing
            }
        }
        if (this.parameterIds.get(right.name) > this.parameterIds.get(left.name)) {
            switch (operator) {
                case EQ:
                    this.getConstraint(RealConstraints.class, right.name).addVariableEquality(left.name);
                    break;
                case LT:
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, false);
                    break;
                case LE:
                    this.getConstraint(RealConstraints.class, right.name).addVariableLowerBound(left.name, true);
                    break;
                case GT:
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, false);
                    break;
                case GE:
                    this.getConstraint(RealConstraints.class, right.name).addVariableUpperBound(left.name, true);
                    break;
                default:
                    // do nothing
            }
        }
    }

    private void updateConstraints(VariableReal left, Operator operator, ConstantReal right) {
        switch (operator) {
            case EQ:
                this.getConstraint(RealConstraints.class, left.name).addConstantEquality(right.value);
                break;
            case LT:
                this.getConstraint(RealConstraints.class, left.name).addConstantUpperBound(right.value, false);
                break;
            case LE:
                this.getConstraint(RealConstraints.class, left.name).addConstantUpperBound(right.value, true);
                break;
            case GT:
                this.getConstraint(RealConstraints.class, left.name).addConstantLowerBound(right.value, false);
                break;
            case GE:
                this.getConstraint(RealConstraints.class, left.name).addConstantLowerBound(right.value, true);
                break;
            default:
                // do nothing
        }
    }

    private void updateConstraints(ConstantReal left, Operator operator, VariableReal right) {
        switch (operator) {
            case EQ:
                this.getConstraint(RealConstraints.class, right.name).addConstantEquality(left.value);
                break;
            case LT:
                this.getConstraint(RealConstraints.class, right.name).addConstantLowerBound(left.value, false);
                break;
            case LE:
                this.getConstraint(RealConstraints.class, right.name).addConstantLowerBound(left.value, true);
                break;
            case GT:
                this.getConstraint(RealConstraints.class, right.name).addConstantUpperBound(left.value, false);
                break;
            case GE:
                this.getConstraint(RealConstraints.class, right.name).addConstantUpperBound(left.value, true);
                break;
            default:
                // do nothing
        }
    }

    public interface VariableConstraints {
        void setVariableName(String variableName);
        String getVariableName();
    }

    public static class IntegerConstraints implements VariableConstraints {

        private String variableName;
        private Long constantEquality = null;
        private String variableEquality = null;
        private final List<String> lowerBounds = new ArrayList<>();
        private final List<String> upperBounds = new ArrayList<>();

        @Override
        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public String getVariableName() {
            return this.variableName;
        }

        public void addConstantEquality(long value) {
            this.constantEquality = value;
        }

        public void addVariableEquality(String name) {
            this.variableEquality = name;
        }

        public String getEquality() {
            if (this.constantEquality != null) {
                return this.constantEquality.toString();
            } else if (this.variableEquality != null) {
                return this.variableEquality;
            }
            return null;
        }

        public void addConstantLowerBound(long value, boolean isIncluded) {
            this.lowerBounds.add(String.valueOf(value + (isIncluded ? 0 : 1)));
        }

        public void addVariableLowerBound(String name, boolean isIncluded) {
            this.upperBounds.add(name + (isIncluded ? "" : "+1"));
        }

        public List<String> getLowerBounds() {
            return this.lowerBounds;
        }

        public void addConstantUpperBound(long value, boolean isIncluded) {
            this.upperBounds.add(String.valueOf(value + (isIncluded ? 0 : -1)));
        }

        public void addVariableUpperBound(String name, boolean isIncluded) {
            this.upperBounds.add(name + (isIncluded ? "" : "-1"));
        }

        public List<String> getUpperBounds() {
            return this.upperBounds;
        }
    }

    public static class RealConstraints implements VariableConstraints {

        private String variableName;
        private Double constantEquality = null;
        private String variableEquality = null;
        private final List<RealBound> lowerBounds = new ArrayList<>();
        private final List<RealBound> upperBounds = new ArrayList<>();

        @Override
        public void setVariableName(String variableName) {
            this.variableName = variableName;
        }

        @Override
        public String getVariableName() {
            return this.variableName;
        }

        public void addConstantEquality(double value) {
            this.constantEquality = value;
        }

        public void addVariableEquality(String name) {
            this.variableEquality = name;
        }

        public String getEquality() {
            if (this.constantEquality != null) {
                return this.constantEquality.toString();
            } else if (this.variableEquality != null) {
                return this.variableEquality;
            }
            return null;
        }

        public void addConstantLowerBound(double value, boolean isIncluded) {
            this.lowerBounds.add(new RealBound(String.valueOf(value), isIncluded));
        }

        public void addVariableLowerBound(String name, boolean isIncluded) {
            this.lowerBounds.add(new RealBound(name, isIncluded));
        }

        public List<RealBound> getLowerBounds() {
            return this.lowerBounds;
        }

        public void addConstantUpperBound(double value, boolean isIncluded) {
            this.upperBounds.add(new RealBound(String.valueOf(value), isIncluded));
        }

        public void addVariableUpperBound(String name, boolean isIncluded) {
            this.upperBounds.add(new RealBound(name, isIncluded));
        }

        public List<RealBound> getUpperBounds() {
            return this.upperBounds;
        }
    }

    public static class RealBound {

        private final String value;
        private final boolean isIncluded;

        public RealBound(String value, boolean isIncluded) {
            this.value = value;
            this.isIncluded = isIncluded;
        }

        public String getValue() {
            return this.value;
        }

        public boolean getIsIncluded() {
            return this.isIncluded;
        }
    }
}
