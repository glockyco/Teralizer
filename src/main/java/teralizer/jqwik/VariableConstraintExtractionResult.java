package teralizer.jqwik;

import java.util.Map;

public class VariableConstraintExtractionResult {

    private final int totalConstraintCount;
    private final int usedConstraintCount;
    private final Map<String, VariableConstraints> constraints;

    public VariableConstraintExtractionResult(int totalConstraintCount, int usedConstraintCount, Map<String, VariableConstraints> constraints) {
        this.totalConstraintCount = totalConstraintCount;
        this.usedConstraintCount = usedConstraintCount;
        this.constraints = constraints;
    }

    public int getTotalConstraintCount() {
        return this.totalConstraintCount;
    }

    public int getUsedConstraintCount() {
        return this.usedConstraintCount;
    }

    public Map<String, VariableConstraints> getConstraints() {
        return this.constraints;
    }
}
