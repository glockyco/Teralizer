package teralizer.jqwik;

import java.util.ArrayList;
import java.util.List;

public class IntegerConstraints implements VariableConstraints {

    private String variableType;
    private String variableName;
    private String constantEquality = null;
    private String variableEquality = null;
    private final List<String> lowerBounds = new ArrayList<>();
    private final List<String> upperBounds = new ArrayList<>();

    private String valueOf(double value) {
        return String.format("(%s) (%s)", this.variableType, value);
    }

    @Override
    public void setVariableType(String variableType) {
        this.variableType = variableType;
    }

    @Override
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

    public void addConstantEquality(long value) {
        this.constantEquality = this.valueOf(value);
    }

    public void addVariableEquality(String name) {
        this.variableEquality = name;
    }

    public String getEquality() {
        if (this.constantEquality != null) {
            return this.constantEquality;
        } else if (this.variableEquality != null) {
            return this.variableEquality;
        }
        return null;
    }

    public void addConstantLowerBound(long value, boolean isIncluded) {
        this.lowerBounds.add(this.valueOf(value + (isIncluded ? 0 : 1)));
    }

    public void addVariableLowerBound(String name, boolean isIncluded) {
        this.lowerBounds.add(name + (isIncluded ? "" : "+1"));
    }

    public List<String> getLowerBounds() {
        return this.lowerBounds;
    }

    public void addConstantUpperBound(long value, boolean isIncluded) {
        this.upperBounds.add(this.valueOf(value + (isIncluded ? 0 : -1)));
    }

    public void addVariableUpperBound(String name, boolean isIncluded) {
        this.upperBounds.add(name + (isIncluded ? "" : "-1"));
    }

    public List<String> getUpperBounds() {
        return this.upperBounds;
    }

    @Override
    public String toString() {
        return String.format("%s = { %s }, ", this.variableName, (this.getEquality() == null ? "" : this.getEquality())) +
            String.format("%s >= { %s }, ", this.variableName, String.join(", ", this.getLowerBounds())) +
            String.format("%s <= { %s }", this.variableName, String.join(", ", this.getUpperBounds()));
    }
}
