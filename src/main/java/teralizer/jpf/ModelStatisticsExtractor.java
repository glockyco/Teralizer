package teralizer.jpf;

import teralizer.domain.Model;
import teralizer.domain.ModelVisitor;
import teralizer.domain.Operation;

import java.util.HashMap;
import java.util.Map;

public class ModelStatisticsExtractor extends ModelVisitor {

    private int operationCount;
    private final Map<String, Integer> operationCounts = new HashMap<>();
    private final Map<String, Integer> operatorCounts = new HashMap<>();
    private final Map<String, Integer> operandCounts = new HashMap<>();

    public ModelStatistics process(Model model, String modelJava) {
        this.operationCount = 0;
        this.operationCounts.clear();
        this.operatorCounts.clear();
        this.operandCounts.clear();

        if (model != null) {
            model.accept(this);
        }

        return new ModelStatistics(
            modelJava != null ? modelJava.length() : 0,
            this.operationCount,
            new HashMap<>(this.operationCounts),
            new HashMap<>(this.operatorCounts),
            new HashMap<>(this.operandCounts)
        );
    }

    @Override
    public void preVisit(Operation operation) {
        String leftName = operation.left == null ? "null" : operation.left.getClass().getSimpleName();
        String rightName = operation.right == null ? "null" : operation.right.getClass().getSimpleName();
        String opName = operation.op.toString();

        String operationKey = String.format("%s %s %s", leftName, opName, rightName);

        this.operationCount++;
        this.incrementCount(this.operationCounts, operationKey);
        this.incrementCount(this.operatorCounts, opName);
        this.incrementCount(this.operandCounts, leftName);
        this.incrementCount(this.operandCounts, rightName);
    }

    private void incrementCount(Map<String, Integer> map, String key) {
        map.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
    }
}
