package teralizer.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockResult;
import org.junit.Assert;
import teralizer.processing.task.Task;

public class PipelinePlannerTest {

    @Example
    void runsRequestedPhasesInCanonicalOrderAndDrainsBetweenThem() {
        List<String> events = new ArrayList<>();
        RecordingPipeline pipeline = new RecordingPipeline(dsl(), events);
        PipelinePlanner planner = new PipelinePlanner(dsl(), pipeline);

        planner.runPhases(
            project(),
            Arrays.asList(
                new FakePhase("generation", true, events),
                new FakePhase("generalization", true, events),
                new FakePhase("reduction", true, events)
            )
        );

        Assert.assertEquals(
            Arrays.asList(
                "generation:clear",
                "generation:check",
                "generation:schedule",
                "drain",
                "generalization:clear",
                "generalization:check",
                "generalization:schedule",
                "drain",
                "reduction:clear",
                "reduction:check",
                "reduction:schedule",
                "drain"
            ),
            events
        );
    }

    @Example
    void skipsUnrequestedPhasesEntirely() {
        List<String> events = new ArrayList<>();
        RecordingPipeline pipeline = new RecordingPipeline(dsl(), events);
        PipelinePlanner planner = new PipelinePlanner(dsl(), pipeline);

        planner.runPhases(
            project(),
            Arrays.asList(
                new FakePhase("generation", true, events),
                new FakePhase("generalization", false, events),
                new FakePhase("reduction", true, events)
            )
        );

        Assert.assertEquals(
            Arrays.asList(
                "generation:clear",
                "generation:check",
                "generation:schedule",
                "drain",
                "reduction:clear",
                "reduction:check",
                "reduction:schedule",
                "drain"
            ),
            events
        );
    }

    @Example
    void preconditionFailureAbortsBeforeSchedulingThatPhase() {
        List<String> events = new ArrayList<>();
        RecordingPipeline pipeline = new RecordingPipeline(dsl(), events);
        PipelinePlanner planner = new PipelinePlanner(dsl(), pipeline);
        PhasePreconditionException failure = new PhasePreconditionException("missing generalized tests");

        PhasePreconditionException thrown = Assert.assertThrows(
            PhasePreconditionException.class,
            () -> planner.runPhases(
                project(),
                Arrays.asList(
                    new FakePhase("generation", true, events),
                    new FakePhase("generalization", true, events, failure),
                    new FakePhase("reduction", true, events)
                )
            )
        );

        Assert.assertSame(failure, thrown);
        Assert.assertEquals(
            Arrays.asList(
                "generation:clear",
                "generation:check",
                "generation:schedule",
                "drain",
                "generalization:clear",
                "generalization:check"
            ),
            events
        );
    }

    private static ProjectRecord project() {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setUseTestGeneration(true);
        project.setUseTestGeneralization(true);
        project.setUseTestReduction(true);
        return project;
    }

    private static DSLContext dsl() {
        return DSL.using(new MockConnection(context -> new MockResult[] {
            new MockResult(0, DSL.using(SQLDialect.POSTGRES).newResult())
        }), SQLDialect.POSTGRES);
    }

    private static final class FakePhase implements Phase {
        private final String name;
        private final boolean requested;
        private final List<String> events;
        private final PhasePreconditionException preconditionFailure;

        private FakePhase(String name, boolean requested, List<String> events) {
            this(name, requested, events, null);
        }

        private FakePhase(
            String name,
            boolean requested,
            List<String> events,
            PhasePreconditionException preconditionFailure
        ) {
            this.name = name;
            this.requested = requested;
            this.events = events;
            this.preconditionFailure = preconditionFailure;
        }

        @Override
        public Set<ProcessingStage> stages() {
            return Collections.emptySet();
        }

        @Override
        public boolean isRequested(ProjectRecord project) {
            return this.requested;
        }

        @Override
        public void checkPreconditions(DSLContext create, ProjectRecord project) {
            this.events.add(this.name + ":check");
            if (this.preconditionFailure != null) {
                throw this.preconditionFailure;
            }
        }

        @Override
        public void schedule(DSLContext create, ProjectRecord project, Consumer<Task> schedule) {
            this.events.add(this.name + ":schedule");
        }

        @Override
        public void clear(DSLContext create, ProjectRecord project) {
            this.events.add(this.name + ":clear");
        }
    }

    private static final class RecordingPipeline extends ProcessingPipeline {
        private final List<String> events;

        private RecordingPipeline(DSLContext create, List<String> events) {
            super(create);
            this.events = events;
        }

        @Override
        public void executeAll() {
            this.events.add("drain");
            super.executeAll();
        }
    }
}
