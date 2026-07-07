package teralizer.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.jqwik.api.Example;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.generated.tables.records.ProjectRecord;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
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

    @Example
    void structuralFailureAfterPhaseAbortsBeforeNextPhase() {
        List<String> events = new ArrayList<>();
        TaskFailureStore store = new TaskFailureStore(ProcessingStage.FILTER_GENERALIZATIONS);
        DSLContext create = store.dsl();
        RecordingPipeline pipeline = new RecordingPipeline(create, events);
        PipelinePlanner planner = new PipelinePlanner(create, pipeline);

        IllegalStateException thrown = Assert.assertThrows(
            IllegalStateException.class,
            () -> planner.runPhases(
                project(),
                Arrays.asList(
                    new FakePhase(
                        "generalization",
                        true,
                        events,
                        EnumSet.of(ProcessingStage.FILTER_GENERALIZATIONS)
                    ),
                    new FakePhase("reduction", true, events)
                )
            )
        );

        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("structural"));
        Assert.assertTrue(thrown.getMessage(), thrown.getMessage().contains("FILTER_GENERALIZATIONS"));
        Assert.assertEquals(
            Arrays.asList(
                "generalization:clear",
                "generalization:check",
                "generalization:schedule",
                "drain"
            ),
            events
        );
    }

    @Example
    void structuralFailureCheckIgnoresAttritionFailures() {
        List<String> events = new ArrayList<>();
        TaskFailureStore store = new TaskFailureStore();
        DSLContext create = store.dsl();
        RecordingPipeline pipeline = new RecordingPipeline(create, events);
        PipelinePlanner planner = new PipelinePlanner(create, pipeline);

        planner.runPhases(
            project(),
            Arrays.asList(
                new FakePhase("generalization", true, events, EnumSet.of(ProcessingStage.FILTER_GENERALIZATIONS)),
                new FakePhase("reduction", true, events)
            )
        );

        Assert.assertTrue(store.queriedStructuralFailures);
        Assert.assertEquals(
            Arrays.asList(
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

    private static ProjectRecord project() {
        ProjectRecord project = new ProjectRecord();
        project.setId(7L);
        project.setUseTestGeneration(true);
        project.setUseTestGeneralization(true);
        project.setUseTestReduction(true);
        return project;
    }

    private static DSLContext dsl() {
        return new TaskFailureStore().dsl();
    }

    private static final class FakePhase implements Phase {
        private final String name;
        private final boolean requested;
        private final List<String> events;
        private final PhasePreconditionException preconditionFailure;
        private final Set<ProcessingStage> stages;

        private FakePhase(String name, boolean requested, List<String> events) {
            this(name, requested, events, Collections.emptySet(), null);
        }

        private FakePhase(String name, boolean requested, List<String> events, Set<ProcessingStage> stages) {
            this(name, requested, events, stages, null);
        }

        private FakePhase(
            String name,
            boolean requested,
            List<String> events,
            PhasePreconditionException preconditionFailure
        ) {
            this(name, requested, events, Collections.emptySet(), preconditionFailure);
        }

        private FakePhase(
            String name,
            boolean requested,
            List<String> events,
            Set<ProcessingStage> stages,
            PhasePreconditionException preconditionFailure
        ) {
            this.name = name;
            this.requested = requested;
            this.events = events;
            this.stages = stages;
            this.preconditionFailure = preconditionFailure;
        }

        @Override
        public Set<ProcessingStage> stages() {
            return this.stages;
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

    private static final class TaskFailureStore implements MockDataProvider {
        private final DSLContext records = DSL.using(SQLDialect.POSTGRES);
        private final Set<ProcessingStage> structuralFailedStages;
        private boolean queriedStructuralFailures;

        private TaskFailureStore(ProcessingStage... structuralFailedStages) {
            this.structuralFailedStages = structuralFailedStages.length == 0
                ? Collections.emptySet()
                : EnumSet.copyOf(Arrays.asList(structuralFailedStages));
        }

        private DSLContext dsl() {
            return DSL.using(new MockConnection(this), SQLDialect.POSTGRES);
        }

        @Override
        public MockResult[] execute(MockExecuteContext context) {
            String sql = context.sql().trim().toLowerCase();
            if (sql.startsWith("select") && sql.contains("test_id") && sql.contains("generalization_id")) {
                this.queriedStructuralFailures = true;
                Assert.assertTrue(sql, sql.contains("status"));
                Assert.assertTrue(sql, sql.contains("test_id"));
                Assert.assertTrue(sql, sql.contains("assertion_id"));
                Assert.assertTrue(sql, sql.contains("generalization_id"));
                Assert.assertTrue(sql, sql.contains("is null"));
                Result<Record1<ProcessingStage>> result = this.records.newResult(org.jooq.generated.Tables.TASK.STAGE);
                for (ProcessingStage stage : this.structuralFailedStages) {
                    result.add(this.records.newRecord(org.jooq.generated.Tables.TASK.STAGE).values(stage));
                }
                return new MockResult[] {new MockResult(result.size(), result)};
            }
            Field<Integer> count = DSL.field("count", Integer.class);
            Result<Record1<Integer>> result = this.records.newResult(count);
            return new MockResult[] {new MockResult(0, result)};
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
