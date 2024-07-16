package teralizer.processing.task;

import java.util.concurrent.Callable;

public class TaskCallable<V> implements Callable<V> {

    private final Task task;
    private final Callable<V> callable;

    public TaskCallable(Task task, Callable<V> callable) {
        this.task = task;
        this.callable = callable;
    }

    public Task getTask() {
        return this.task;
    }

    public V call() throws Exception {
        return this.callable.call();
    }
}
