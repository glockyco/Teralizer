package teralizer.processing;

import java.util.Set;
import java.util.function.Consumer;
import org.jooq.DSLContext;
import org.jooq.generated.tables.records.ProjectRecord;
import teralizer.processing.task.Task;

interface Phase {
    Set<ProcessingStage> stages();

    boolean isRequested(ProjectRecord project);

    void checkPreconditions(ProjectRecord project);

    void schedule(ProjectRecord project, Consumer<Task> schedule);

    void clear(DSLContext create, ProjectRecord project);
}
