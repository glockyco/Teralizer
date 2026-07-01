package teralizer.processing;

import java.util.Comparator;
import java.util.Objects;
import teralizer.processing.task.Task;

public class TaskPriorityComparator implements Comparator<Task> {

    private static final Comparator<Long> LONG_COMPARATOR = new LongComparator();

    @Override
    public int compare(Task task1, Task task2) {
        if (!Objects.equals(task1.getProjectId(), task2.getProjectId())) {
            return LONG_COMPARATOR.compare(task1.getProjectId(), task2.getProjectId());
        }

        if (!Objects.equals(task1.getVariant(), task2.getVariant())) {
            return Comparator.nullsFirst(String::compareTo).compare(task1.getVariant(), task2.getVariant());
        }

        if (task1.getStage() != task2.getStage()) {
            return LONG_COMPARATOR.compare(task1.getStage().getStep().longValue(), task2.getStage().getStep().longValue());
        }

        if (!Objects.equals(task1.getTestId(), task2.getTestId())) {
            return LONG_COMPARATOR.compare(task1.getTestId(), task2.getTestId());
        }

        if (!Objects.equals(task1.getAssertionId(), task2.getAssertionId())) {
            return LONG_COMPARATOR.compare(task1.getAssertionId(), task2.getAssertionId());
        }

        return LONG_COMPARATOR.compare(task1.getGeneralizationId(), task2.getGeneralizationId());
    }

    private static class LongComparator implements Comparator<Long> {
        @Override
        public int compare(Long int1, Long int2) {
            if (int1 == null && int2 == null) {
                return 0;
            } else if (int1 == null) {
                return -1;
            } else if (int2 == null) {
                return 1;
            } else {
                return Long.compare(int1, int2);
            }
        }
    }
}
