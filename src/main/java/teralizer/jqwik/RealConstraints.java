package teralizer.jqwik;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RealConstraints implements VariableConstraints {

    private String variableType;
    private String variableName;
    private String constantEquality = null;
    private String variableEquality = null;
    private final List<RealBound> lowerBounds = new ArrayList<>();
    private final List<RealBound> upperBounds = new ArrayList<>();

    private String valueOf(double value) {
        String valueString;
        if (Double.isInfinite(value)) {
            if (value > 0) {
                valueString = "Double.POSITIVE_INFINITY";
            } else {
                valueString = "Double.NEGATIVE_INFINITY";
            }
        } else if (Double.isNaN(value)) {
            valueString = "Double.NaN";
        } else {
            valueString = String.valueOf(value);
        }
        return String.format("(%s) (%s)", this.variableType, valueString);
    }

    @Override
    public void setVariableType(String variableType) {
        this.variableType = variableType;
    }

    public String getVariableType() {
        return this.variableType;
    }

    @Override
    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    @Override
    public String getVariableName() {
        return this.variableName;
    }

    public void addConstantEquality(double value) {
        this.constantEquality = this.valueOf(value);
    }

    public void addVariableEquality(String name) {
        this.variableEquality = name;
    }

    public void addEqualityExpression(String expression) {
        this.variableEquality = expression;
    }

    public String getEquality() {
        if (this.constantEquality != null) {
            return this.constantEquality;
        } else if (this.variableEquality != null) {
            return this.variableEquality;
        }
        return null;
    }

    public void addConstantLowerBound(double value, boolean isIncluded) {
        this.lowerBounds.add(new RealBound(this.valueOf(value), isIncluded));
    }

    public void addVariableLowerBound(String name, boolean isIncluded) {
        this.lowerBounds.add(new RealBound(name, isIncluded));
    }

    public void addLowerBoundExpression(String expression, boolean isIncluded) {
        this.lowerBounds.add(new RealBound(expression, isIncluded));
    }

    public List<RealBound> getLowerBounds() {
        return this.lowerBounds;
    }

    public void addConstantUpperBound(double value, boolean isIncluded) {
        this.upperBounds.add(new RealBound(this.valueOf(value), isIncluded));
    }

    public void addVariableUpperBound(String name, boolean isIncluded) {
        this.upperBounds.add(new RealBound(name, isIncluded));
    }

    public void addUpperBoundExpression(String expression, boolean isIncluded) {
        this.upperBounds.add(new RealBound(expression, isIncluded));
    }

    public List<RealBound> getUpperBounds() {
        return this.upperBounds;
    }

    @Override
    public String toString() {
        List<String> lowerBoundsIncluded = this.lowerBounds.stream().filter(b -> b.getIsIncluded()).map(RealBound::getValue).collect(Collectors.toList());
        List<String> lowerBoundsExcluded = this.lowerBounds.stream().filter(b -> !b.getIsIncluded()).map(RealBound::getValue).collect(Collectors.toList());
        List<String> upperBoundsIncluded = this.upperBounds.stream().filter(b -> b.getIsIncluded()).map(RealBound::getValue).collect(Collectors.toList());
        List<String> upperBoundsExcluded = this.upperBounds.stream().filter(b -> !b.getIsIncluded()).map(RealBound::getValue).collect(Collectors.toList());

        return String.format("%s = { %s }, ", this.variableName, (this.getEquality() == null ? "" : this.getEquality())) +
            String.format("%s > { %s }, ", this.variableName, String.join(", ", lowerBoundsExcluded)) +
            String.format("%s >= { %s }, ", this.variableName, String.join(", ", lowerBoundsIncluded)) +
            String.format("%s <= { %s }, ", this.variableName, String.join(", ", upperBoundsIncluded)) +
            String.format("%s < { %s }", this.variableName, String.join(", ", upperBoundsExcluded));
    }
}
