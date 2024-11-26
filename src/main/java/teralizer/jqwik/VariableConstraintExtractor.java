package teralizer.jqwik;

import teralizer.domain.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class VariableConstraintExtractor extends ModelVisitor {

    private static final double EPS = 0.01;

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
                case LE:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableUpperBound(right.name, true);
                case GT:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, false);
                case GE:
                    this.getConstraint(IntegerConstraints.class, left.name).addVariableLowerBound(right.name, true);
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
        String getEquality();
        String getLowerBound();
        String getUpperBound();
    }

    public static class IntegerConstraints implements VariableConstraints {

        private String variableName;
        private Long constantEquality = null;
        private String variableEquality = null;
        private final List<Long> constantLowerBounds = new ArrayList<>();
        private final List<Long> constantUpperBounds = new ArrayList<>();
        private final List<String> variableLowerBounds = new ArrayList<>();
        private final List<String> variableUpperBounds = new ArrayList<>();

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
            this.constantLowerBounds.add(value + (isIncluded ? 0 : 1));
        }

        public void addVariableLowerBound(String name, boolean isIncluded) {
            this.variableLowerBounds.add(name + (isIncluded ? "" : "+1"));
        }

        public String getLowerBound() {
            if (!this.constantLowerBounds.isEmpty() && !this.variableLowerBounds.isEmpty()) {
                return "java.util.Collections.max(java.util.Arrays.asList("
                    + Collections.max(this.constantLowerBounds) + ", "
                    + String.join(", ", this.variableLowerBounds) + "))";
            } else if (!this.constantLowerBounds.isEmpty()) {
                return String.valueOf(Collections.max(this.constantLowerBounds));
            } else if (!this.variableLowerBounds.isEmpty()) {
                return "java.util.Collections.max(java.util.Arrays.asList("
                    + String.join(", ", this.variableLowerBounds) + "))";
            }
            return null;
        }

        public void addConstantUpperBound(long value, boolean isIncluded) {
            this.constantUpperBounds.add(value + (isIncluded ? 0 : -1));
        }

        public void addVariableUpperBound(String name, boolean isIncluded) {
            this.variableUpperBounds.add(name + (isIncluded ? "" : "-1"));
        }

        public String getUpperBound() {
            if (!this.constantUpperBounds.isEmpty() && !this.variableUpperBounds.isEmpty()) {
                return "java.util.Collections.min(java.util.Arrays.asList("
                    + Collections.min(this.constantUpperBounds) + ", "
                    + String.join(", ", this.variableUpperBounds) + "))";
            } else if (!this.constantUpperBounds.isEmpty()) {
                return String.valueOf(Collections.min(this.constantUpperBounds));
            } else if (!this.variableUpperBounds.isEmpty()) {
                return "java.util.Collections.min(java.util.Arrays.asList("
                    + String.join(", ", this.variableUpperBounds) + "))";
            }
            return null;
        }
    }

    public static class RealConstraints implements VariableConstraints {

        private String variableName;
        private Double constantEquality = null;
        private String variableEquality = null;
        private final List<Double> constantLowerBounds = new ArrayList<>();
        private final List<Double> constantUpperBounds = new ArrayList<>();
        private final List<String> variableLowerBounds = new ArrayList<>();
        private final List<String> variableUpperBounds = new ArrayList<>();

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
            this.constantLowerBounds.add(
                new BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
                    .add(BigDecimal.valueOf(isIncluded ? 0 : EPS)).doubleValue());
        }

        public void addVariableLowerBound(String name, boolean isIncluded) {
            this.variableLowerBounds.add(
                "new java.math.BigDecimal(" + name + ").setScale(2, java.math.RoundingMode.HALF_UP)" +
                    ".add(java.math.BigDecimal.valueOf(" + (isIncluded ? 0 : EPS) + ")).doubleValue()");
        }

        public String getLowerBound() {
            if (!this.constantLowerBounds.isEmpty() && !this.variableLowerBounds.isEmpty()) {
                return "java.util.Collections.max(java.util.Arrays.asList("
                    + Collections.max(this.constantLowerBounds) + ", "
                    + String.join(", ", this.variableLowerBounds) + "))";
            } else if (!this.constantLowerBounds.isEmpty()) {
                return String.valueOf(Collections.max(this.constantLowerBounds));
            } else if (!this.variableLowerBounds.isEmpty()) {
                return "java.util.Collections.max(java.util.Arrays.asList("
                    + String.join(", ", this.variableLowerBounds) + "))";
            }
            return null;
        }

        public void addConstantUpperBound(double value, boolean isIncluded) {
            this.constantUpperBounds.add(
                new BigDecimal(value).setScale(2, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.valueOf(isIncluded ? 0 : EPS)).doubleValue());
        }

        public void addVariableUpperBound(String name, boolean isIncluded) {
            this.variableUpperBounds.add(
                "new java.math.BigDecimal(" + name + ").setScale(2, java.math.RoundingMode.HALF_UP)" +
                    ".subtract(java.math.BigDecimal.valueOf(" + (isIncluded ? 0 : EPS) + ")).doubleValue()");
        }

        public String getUpperBound() {
            if (!this.constantUpperBounds.isEmpty() && !this.variableUpperBounds.isEmpty()) {
                return "java.util.Collections.min(java.util.Arrays.asList("
                    + Collections.min(this.constantUpperBounds) + ", "
                    + String.join(", ", this.variableUpperBounds) + "))";
            } else if (!this.constantUpperBounds.isEmpty()) {
                return String.valueOf(Collections.min(this.constantUpperBounds));
            } else if (!this.variableUpperBounds.isEmpty()) {
                return "java.util.Collections.min(java.util.Arrays.asList("
                    + String.join(", ", this.variableUpperBounds) + "))";
            }
            return null;
        }
    }
}
