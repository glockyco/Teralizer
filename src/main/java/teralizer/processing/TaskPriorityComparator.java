package teralizer.processing;

import teralizer.processing.task.Task;

import java.util.Comparator;
import java.util.Objects;

public class TaskPriorityComparator implements Comparator<Task> {

    private static final Comparator<Integer> INTEGER_COMPARATOR = new IntegerComparator();

    @Override
    public int compare(Task task1, Task task2) {
        if (!Objects.equals(task1.getProjectId(), task2.getProjectId())) {
            return INTEGER_COMPARATOR.compare(task1.getProjectId(), task2.getProjectId());
        }

        if (task1.getVariant() != task2.getVariant() && task1.getVariant() != null && task2.getVariant() != null) {
            return INTEGER_COMPARATOR.compare(task1.getVariant().getId(), task2.getVariant().getId());
        }

        if (task1.getStage() != task2.getStage()) {
            return INTEGER_COMPARATOR.compare(task1.getStage().getStep(), task2.getStage().getStep());
        }

        if (!Objects.equals(task1.getTestId(), task2.getTestId())) {
            return INTEGER_COMPARATOR.compare(task1.getTestId(), task2.getTestId());
        }

        return INTEGER_COMPARATOR.compare(task1.getGeneralizationId(), task2.getGeneralizationId());
    }

    private static class IntegerComparator implements Comparator<Integer> {
        @Override
        public int compare(Integer int1, Integer int2) {
            if (int1 == null && int2 == null) {
                return 0;
            } else if (int1 == null) {
                return -1;
            } else if (int2 == null) {
                return 1;
            } else {
                return Integer.compare(int1, int2);
            }
        }
    }
}
