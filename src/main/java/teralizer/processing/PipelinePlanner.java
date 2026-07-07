package teralizer.processing;

import java.util.Arrays;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.generated.tables.records.ProjectRecord;

public class PipelinePlanner {
    private final DSLContext create;
    private final ProcessingPipeline pipeline;

    public PipelinePlanner(DSLContext create) {
        this(create, new ProcessingPipeline(create));
    }

    public PipelinePlanner(DSLContext create, ProcessingPipeline pipeline) {
        this.create = create;
        this.pipeline = pipeline;
    }

    public void run(ProjectRecord project) {
        runPhases(project, Arrays.asList(PipelinePhase.values()));
    }

    void runPhases(ProjectRecord project, List<? extends Phase> phases) {
        for (Phase phase : phases) {
            if (!phase.isRequested(project)) {
                continue;
            }
            phase.clear(this.create, project);
            phase.checkPreconditions(project);
            phase.schedule(project, this.pipeline::addTask);
            this.pipeline.executeAll();
        }
    }
}
