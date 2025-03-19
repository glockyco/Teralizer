package teralizer.jpf;

import java.util.Map;

public class ModelStatistics {

    private final int javaSize;
    private final int operationCount;
    private final Map<String, Integer> operationCounts;
    private final Map<String, Integer> operatorCounts;
    private final Map<String, Integer> operandCounts;

    public ModelStatistics(
        int javaSize,
        int operationCount,
        Map<String, Integer> operationCounts,
        Map<String, Integer> operatorCounts,
        Map<String, Integer> operandCounts
    ) {
        this.javaSize = javaSize;
        this.operationCount = operationCount;
        this.operationCounts = operationCounts;
        this.operatorCounts = operatorCounts;
        this.operandCounts = operandCounts;
    }

    public int getJavaSize() {
        return this.javaSize;
    }

    public int getOperationCount() {
        return this.operationCount;
    }

    public Map<String, Integer> getOperationCounts() {
        return this.operationCounts;
    }

    public Map<String, Integer> getOperatorCounts() {
        return this.operatorCounts;
    }

    public Map<String, Integer> getOperandCounts() {
        return this.operandCounts;
    }
}
