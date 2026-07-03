---
title: Static MUT-id Fusion (v1)
type: plan
status: active
created: 2026-07-02
parent: 2026-07-02-mut-id-confidence-fusion
---

# Static MUT-id Fusion (v1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the abstain-on-ambiguity MUT identification in `TestAnalysis.findTestedMethodCall` with a confidence-ranked fusion resolver that returns a graded pick + full provenance for every assertion, and persist that provenance in a new `mut_resolution_observation` table.

**Architecture:** A new `MethodUnderTestResolver` produces a `MutResolution` value object (pick, confidence tier, deciding signal, corroborators, ranked alternatives) for every assertion — never `Optional.empty()`. `TestAnalysisTask` consumes it: generalization-grade picks populate `tested_*` as today; everything else becomes a typed observation row only. Design authority: `2026-07-02-mut-id-confidence-fusion` (spec). Read the spec's "fusion model" section before starting.

**Tech stack:** Java 8, Spoon AST (`spoon.reflect.*`), jOOQ 3.x via `nu.studer.jooq` Gradle plugin 5.2.2, PostgreSQL (docker container `postgres-teralizer`), Gson for JSON-in-TEXT columns, jqwik `@Example` + `org.junit.Assert` for unit tests.

**Ground rules for every task:**
- Run from the repo root. Do NOT run project-wide formatters or the full test suite unless a step says so; the verification command is always given explicitly.
- JUnit 4 `@Test` methods are NOT discovered (no vintage engine). New tests use `net.jqwik.api.Example` + `org.junit.Assert`, like `src/test/java/teralizer/spoon/analysis/GeneralizableInputTest.java`.
- Never edit anything under `projects/` or `build/generated-src/` by hand (generated jOOQ code is regenerated in Task 1, then committed — that is the only way it changes).
- Commit after every task with the given message (Conventional Commits; title-only is fine for these).

---

## Behavior contract (from the spec — binding for every task)

1. **Compatibility:** every pick the pre-fusion resolver returned, the fusion resolver returns identically. Two intentional divergences, both graded honestly and verified in Task 11: (a) *killed definitions* — `int x = foo(); x = 5; assertEquals(5, x);` previously picked `foo()` (a dataflow bug: the write of `5` kills that definition); fusion returns no producer from that variable; (b) *inspector unwrap* — `assertTrue(sut.compute(x).isEmpty())` previously picked `isEmpty`; fusion picks `compute` (T1, `INSPECTOR_UNWRAP`).
2. **Totality:** `MethodUnderTestResolver.resolve(...)` never returns null and never throws for any Spoon-modelable input; the fallback is `status=NONE, tier=T5_NONE`.
3. **Grade separation:** only a pick whose `getExecutable().getDeclaration()` is a source-model `CtMethod` is generalization-grade. Everything else must leave the declaration-dependent `tested_*` columns null (so `MissingValueFilter` still rejects and nothing NPEs in `JpfInstrumentationTask`).
4. **Determinism:** identical input source ⇒ identical resolution, including `candidate_details` order.

## File map

- Create: `src/main/java/teralizer/spoon/analysis/MutResolution.java` — value object + enums.
- Create: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java` — the fusion resolver.
- Create: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java` — unit tests for every branch.
- Modify: `src/main/java/teralizer/spoon/analysis/TestAnalysis.java` — delete `findTestedMethodCall` + `getExecutedBody` (Task 9); `getActualParameterIndex`, `isJUnit4Assertion`, `isJUnit5Assertion`, `findAllAsserts` stay (other callers: `TestGeneralizationTask:378,525`).
- Modify: `src/main/java/teralizer/processing/task/TestAnalysisTask.java` — consume `MutResolution`, write observation rows.
- Modify: `src/main/resources/db/create-tables.sql` — new table DDL.
- Generated (via Task 1 codegen, committed): `build/generated-src/jooq/main/org/jooq/generated/**` — `MutResolutionObservation` table + record classes.
- Create: `analysis/src/teralizer/mut_resolution_funnel.py` — tier-funnel queries.
- Modify (Task 12): `docs/plans/2026-06-28-mut-id-targeting-and-coverage.md` — record spike results.

---

### Task 1: `mut_resolution_observation` DDL + jOOQ codegen

The table must exist in a PostgreSQL schema for the jOOQ generator to produce the record classes everything later compiles against. Generated sources are committed in this repo (precedent: commit `4757534f`, which added `jqwik_execution_run`).

**Files:**
- Modify: `src/main/resources/db/create-tables.sql`
- Generated: `build/generated-src/jooq/main/org/jooq/generated/**`

- [x] **Step 1: Add the DROP line.** In `src/main/resources/db/create-tables.sql`, the file starts with a block of `DROP TABLE IF EXISTS` lines in reverse-dependency order. Add a new first drop (before `DROP TABLE IF EXISTS jqwik_property_execution;`):

```sql
DROP TABLE IF EXISTS mut_resolution_observation;
```

- [x] **Step 2: Add the CREATE TABLE at the end of the file** (after the `jqwik_property_execution` indexes):

```sql
CREATE TABLE mut_resolution_observation
(
    id                         BIGSERIAL PRIMARY KEY,

    assertion_id               BIGINT  NOT NULL,
    project_id                 BIGINT  NOT NULL,
    test_id                    BIGINT  NOT NULL,

    status                     TEXT    NOT NULL, -- RESOLVED | CHARACTERIZATION_ONLY | NONE
    confidence_tier            TEXT    NOT NULL, -- T1_PROVEN | T2_CORROBORATED | T3_SINGLE_WEAK | T4_GUESS | T5_NONE
    deciding_signal            TEXT    NOT NULL,
    corroborating_signals      TEXT,             -- JSON array of NAME_MATCH | FOCAL_CLASS_MEMBER
    no_pick_reason             TEXT,             -- null when RESOLVED

    candidate_count            INTEGER NOT NULL,
    resolved_call_source       TEXT,
    resolved_method_name       TEXT,
    resolved_declaring_type    TEXT,
    resolved_parameter_types   TEXT,             -- JSON array of qualified type names
    resolved_return_type       TEXT,

    inspector_unwrapped        BOOLEAN NOT NULL DEFAULT FALSE,
    shallow_inspector_pick     BOOLEAN NOT NULL DEFAULT FALSE,

    focal_type                 TEXT,
    focal_type_source          TEXT,             -- PATH_AND_NAME | NAME_ONLY | PATH_ONLY | NONE
    focal_agreement            BOOLEAN,          -- null when focal_type_source = NONE or no pick

    candidate_param_count      INTEGER,
    candidate_param_supported  BOOLEAN,
    candidate_return_supported BOOLEAN,

    oracle_agreement           TEXT,             -- reserved: AGREED | REFUTED | ABSENT (PIT_ORIGINAL)
    candidate_details          TEXT,             -- JSON array of ranked alternatives

    actual_shape               TEXT,             -- AST shape of the asserted actual expression (spec enum)
    receiver_provenance        TEXT,             -- INLINE_CTOR | LOCAL_CTOR | LOCAL_CTOR_MUTATED | LOCAL_OTHER | FIELD | PARAM_OR_STATIC | NONE

    FOREIGN KEY (assertion_id) REFERENCES assertion (id) ON DELETE CASCADE,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_mut_resolution_observation_project_id ON mut_resolution_observation (project_id);
CREATE INDEX idx_mut_resolution_observation_assertion_id ON mut_resolution_observation (assertion_id);
CREATE INDEX idx_mut_resolution_observation_status_tier ON mut_resolution_observation (status, confidence_tier);
```

- [x] **Step 3: Load the schema into a scratch codegen DB.** The jOOQ generator (`build.gradle:113-124`) connects to `jdbc:postgresql://${dbHost}:${dbPort}/${dbName}` where `dbName` comes from `.env` `DB_NAME`, else env var, else `postgres`. Never point codegen at `postgres_dev`/`postgres_test` — the DDL starts with DROPs. Use a scratch DB:

```bash
./gradlew startPostgres
docker exec -i postgres-teralizer psql -U postgres -c "DROP DATABASE IF EXISTS jooq_codegen_scratch;"
docker exec -i postgres-teralizer psql -U postgres -c "CREATE DATABASE jooq_codegen_scratch;"
docker exec -i postgres-teralizer psql -U postgres -d jooq_codegen_scratch < src/main/resources/db/create-tables.sql
```

Expected: a stream of `DROP TABLE` / `CREATE TABLE` / `CREATE INDEX` lines, no `ERROR`.

- [x] **Step 4: Run codegen against the scratch DB.** Check first whether `.env` exists and sets `DB_NAME` (`.env` beats the environment in `getEnv`, `build.gradle:22-24`). If it does, temporarily comment that line out for this step and restore it afterwards. Then:

```bash
DB_NAME=jooq_codegen_scratch ./gradlew generateJooq
```

Expected: `BUILD SUCCESSFUL`; `git status` shows new files `build/generated-src/jooq/main/org/jooq/generated/tables/MutResolutionObservation.java` and `.../tables/records/MutResolutionObservationRecord.java`, plus modified `Tables.java`, `Public.java`, `Keys.java`, `Indexes.java`, `Sequences.java`. If any *other* generated table class changes semantically, the scratch DB did not match the committed DDL — stop and re-check Step 3.

- [x] **Step 5: Compile.** Run: `./gradlew compileJava`. Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Commit.**

```bash
git add src/main/resources/db/create-tables.sql build/generated-src/jooq
git commit -m "feat(db): add mut_resolution_observation table"
```

---

### Task 2: `MutResolution` value object

Pure data holder + enums; the resolver's return type and the observation writer's input. No behavior worth unit-testing on its own (Task 3's resolver tests cover it).

**Files:**
- Create: `src/main/java/teralizer/spoon/analysis/MutResolution.java`

- [x] **Step 1: Write the class** (complete file):

```java
package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import spoon.reflect.code.CtInvocation;

/**
 * Result of method-under-test resolution for one assertion. Always present: the resolver never
 * abstains silently; "no candidate" is an explicit status. Design: docs/plans/2026-07-02-mut-id-confidence-fusion.md
 */
public final class MutResolution {

    public enum Status { RESOLVED, CHARACTERIZATION_ONLY, NONE }

    public enum Tier { T1_PROVEN, T2_CORROBORATED, T3_SINGLE_WEAK, T4_GUESS, T5_NONE }

    public enum Signal {
        DIRECT_ACTUAL_CALL,
        LOCAL_VARIABLE_PRODUCER,
        FIELD_PRODUCER,
        SUBEXPRESSION_PRODUCER,
        INSPECTOR_UNWRAP,
        UNIQUE_PRODUCER_ELIMINATION,
        ASSERT_THROWS_LAMBDA,
        RANKED_GUESS,
        NONE
    }

    public enum Corroborator { NAME_MATCH, FOCAL_CLASS_MEMBER }

    public enum NoPickReason {
        LIBRARY_DECLARATION,
        UNRESOLVED_SOURCE_DECLARATION,
        NO_VISIBLE_CALL,
        UNSUPPORTED_ASSERTION_SHAPE
    }

    public enum FocalSource { PATH_AND_NAME, NAME_ONLY, PATH_ONLY, NONE }

    public enum ActualShape {
        LITERAL, VARIABLE, FIELD_ACCESS, SINGLE_CALL, CHAINED_CALLS_END0ARG,
        CHAINED_CALLS_ENDNARG, CTOR_ONLY, CTOR_RECEIVER_CALL, OPERATOR_COMPOSITE,
        ARRAY_INDEX, LAMBDA_OR_METHODREF, NONE
    }

    public enum ReceiverProvenance {
        INLINE_CTOR, LOCAL_CTOR, LOCAL_CTOR_MUTATED, LOCAL_OTHER, FIELD, PARAM_OR_STATIC, NONE
    }

    /** A losing candidate, recorded for T4 provenance. */
    public static final class Candidate {
        public final String methodName;
        public final String declaringType;
        public final String callSource;

        public Candidate(String methodName, String declaringType, String callSource) {
            this.methodName = methodName;
            this.declaringType = declaringType;
            this.callSource = callSource;
        }
    }

    private final Status status;
    private final Tier tier;
    private final Signal decidingSignal;
    private final Set<Corroborator> corroborators;
    private final NoPickReason noPickReason;
    private final CtInvocation<?> pick;
    private final List<Candidate> alternatives;
    private final int candidateCount;
    private final boolean inspectorUnwrapped;
    private final boolean shallowInspectorPick;
    private final String focalType;
    private final FocalSource focalSource;
    private final Boolean focalAgreement;
    private final ActualShape actualShape;
    private final ReceiverProvenance receiverProvenance;

    MutResolution(Status status, Tier tier, Signal decidingSignal, Set<Corroborator> corroborators,
                  NoPickReason noPickReason, CtInvocation<?> pick, List<Candidate> alternatives,
                  int candidateCount, boolean inspectorUnwrapped, boolean shallowInspectorPick,
                  String focalType, FocalSource focalSource, Boolean focalAgreement,
                  ActualShape actualShape, ReceiverProvenance receiverProvenance) {
        this.status = status;
        this.tier = tier;
        this.decidingSignal = decidingSignal;
        this.corroborators = corroborators == null ? EnumSet.noneOf(Corroborator.class) : corroborators;
        this.noPickReason = noPickReason;
        this.pick = pick;
        this.alternatives = alternatives == null ? new ArrayList<Candidate>() : alternatives;
        this.candidateCount = candidateCount;
        this.inspectorUnwrapped = inspectorUnwrapped;
        this.shallowInspectorPick = shallowInspectorPick;
        this.focalType = focalType;
        this.focalSource = focalSource;
        this.focalAgreement = focalAgreement;
        this.actualShape = actualShape;
        this.receiverProvenance = receiverProvenance;
    }

    public Status getStatus() { return this.status; }
    public Tier getTier() { return this.tier; }
    public Signal getDecidingSignal() { return this.decidingSignal; }
    public Set<Corroborator> getCorroborators() { return Collections.unmodifiableSet(this.corroborators); }
    public NoPickReason getNoPickReason() { return this.noPickReason; }
    /** The picked test-side call; null iff status == NONE. */
    public CtInvocation<?> getPick() { return this.pick; }
    public List<Candidate> getAlternatives() { return Collections.unmodifiableList(this.alternatives); }
    public int getCandidateCount() { return this.candidateCount; }
    public boolean isInspectorUnwrapped() { return this.inspectorUnwrapped; }
    public boolean isShallowInspectorPick() { return this.shallowInspectorPick; }
    public String getFocalType() { return this.focalType; }
    public FocalSource getFocalSource() { return this.focalSource; }
    public Boolean getFocalAgreement() { return this.focalAgreement; }
    public ActualShape getActualShape() { return this.actualShape == null ? ActualShape.NONE : this.actualShape; }
    public ReceiverProvenance getReceiverProvenance() { return this.receiverProvenance == null ? ReceiverProvenance.NONE : this.receiverProvenance; }
}
```

- [x] **Step 2: Compile.** Run: `./gradlew compileJava`. Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit.**

```bash
git add src/main/java/teralizer/spoon/analysis/MutResolution.java
git commit -m "feat(mut-id): add MutResolution value object"
```

---

### Task 3: Resolver skeleton — current behavior, graded (TDD)

Extract today's logic into `MethodUnderTestResolver`, unchanged in *which call it picks*, but returning a graded `MutResolution`. `TestAnalysis.findTestedMethodCall` becomes a thin delegate so every commit stays green.

**Files:**
- Create: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java`
- Create: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java`
- Modify: `src/main/java/teralizer/spoon/analysis/TestAnalysis.java:87-176`

- [x] **Step 1: Write the failing tests.** Complete file (the helpers at the bottom are reused by all later tasks — write them now):

```java
package teralizer.spoon.analysis;

import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class MethodUnderTestResolverTest {

    // --- Task 3: characterization of current behavior, now graded ---

    @Example
    void directInvocationInActualPosition_isT1Proven() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, new Subject().gcd(6, 9)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.RESOLVED, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.DIRECT_ACTUAL_CALL, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void oneHopLocalVariable_isT1Proven() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.LOCAL_VARIABLE_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void assertThrowsSingleInvocation_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> new Subject().gcd(0, 0)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void assertThrowsMultipleInvocations_picksLast_gradedGuess() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
        // last-call position decided; no identity indicator computed yet in Task 3 => T4 base
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertTrue(r.getTier() == MutResolution.Tier.T4_GUESS
            || r.getTier() == MutResolution.Tier.T3_SINGLE_WEAK
            || r.getTier() == MutResolution.Tier.T2_CORROBORATED);
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        Assert.assertEquals("helper", r.getAlternatives().get(0).methodName);
    }

    @Example
    void unsupportedShape_isNoneT5() {
        // assertNotNull has no actual-parameter index => unsupported shape
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertNotNull(new Subject().gcd(6, 9)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T5_NONE, r.getTier());
        Assert.assertNull(r.getPick());
        Assert.assertEquals(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, r.getNoPickReason());
    }

    @Example
    void libraryPick_isCharacterizationOnly() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, Integer.parseInt(\"3\")); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.CHARACTERIZATION_ONLY, r.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.LIBRARY_DECLARATION, r.getNoPickReason());
        Assert.assertEquals("parseInt", r.getPick().getExecutable().getSimpleName());
    }

    // --- shared helpers (used by all tasks) ---

    static final String SUBJECT_SOURCE =
        "public class Subject {\n"
        + "  public int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }\n"
        + "  public int helper(int a) { return a; }\n"
        + "  public boolean isPrime(int n) { return n > 1; }\n"
        + "  public java.util.List<Integer> compute(int x) { return new java.util.ArrayList<>(); }\n"
        + "  public int getTotal() { return 0; }\n"
        + "  public void process(int x) { }\n"
        + "}";

    static MutResolution resolve(String testSource, String... otherSources) {
        return resolveNth(testSource, 0, otherSources);
    }

    static MutResolution resolveNth(String testSource, int assertionIndex, String... otherSources) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        for (int i = 0; i < otherSources.length; i++) {
            launcher.addInputResource(new VirtualFile(otherSources[i], "Other" + i + ".java"));
        }
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        List<CtInvocation<?>> asserts = TestAnalysis.findAllAsserts(testMethod);
        CtInvocation<?> assertion = asserts.isEmpty() ? null : asserts.get(assertionIndex);
        return MethodUnderTestResolver.resolve(testMethod, assertion);
    }
}
```

- [x] **Step 2: Run, expect FAIL** (class absent).

Run: `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'`
Expected: compilation error — `MethodUnderTestResolver` does not exist.

- [x] **Step 3: Write the resolver skeleton.** Complete file. This reproduces `TestAnalysis.findTestedMethodCall` (`TestAnalysis.java:87-176`) pick-for-pick, wrapped in grading. Move `getExecutedBody` (`TestAnalysis.java:178-...`) here verbatim as a private method (it stays in `TestAnalysis` too until Task 9 deletes it — duplication for two tasks is acceptable to keep both compiling).

```java
package teralizer.spoon.analysis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtVariableReference;
import teralizer.util.Configuration;

/**
 * Confidence-ranked MUT resolution. Never abstains: every assertion yields a MutResolution with
 * an explicit status/tier. Design: docs/plans/2026-07-02-mut-id-confidence-fusion.md
 */
public final class MethodUnderTestResolver {

    private MethodUnderTestResolver() {
    }

    public static MutResolution resolve(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        if (assertion == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }

        if (assertion.getExecutable().getSimpleName().equals(Configuration.ASSERT_THROWS)) {
            return resolveAssertThrows(testMethod, assertion);
        }

        Optional<Integer> index = TestAnalysis.getActualParameterIndex(assertion);
        if (!index.isPresent()) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }

        CtExpression<?> actual = assertion.getArguments().get(index.get());
        return resolveValueAssertion(testMethod, assertion, actual);
    }

    // --- assertThrows: the executed body is the slice ---

    private static MutResolution resolveAssertThrows(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        CtElement body = getExecutedBody(assertion.getArguments().get(1)).orElse(null);
        if (body == null) {
            return none(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE);
        }
        List<CtInvocation<?>> invocations = body.getElements(CtInvocation.class::isInstance);
        if (invocations.isEmpty()) {
            return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
        }
        CtInvocation<?> pick = invocations.get(invocations.size() - 1);
        if (invocations.size() == 1) {
            return graded(testMethod, pick, MutResolution.Signal.ASSERT_THROWS_LAMBDA,
                MutResolution.Tier.T1_PROVEN, alternativesExcluding(invocations, pick), false, false);
        }
        // Multiple calls: last-call position decided => guess-grade base, promotable by corroborators.
        return rankedBase(testMethod, pick, MutResolution.Signal.ASSERT_THROWS_LAMBDA,
            alternativesExcluding(invocations, pick));
    }

    // --- value assertions: today's one-hop logic (extended in Tasks 4-8) ---

    private static MutResolution resolveValueAssertion(CtMethod<?> testMethod, CtInvocation<?> assertion,
                                                       CtExpression<?> actual) {
        if (actual instanceof CtInvocation<?>) {
            return graded(testMethod, (CtInvocation<?>) actual, MutResolution.Signal.DIRECT_ACTUAL_CALL,
                MutResolution.Tier.T1_PROVEN, new ArrayList<MutResolution.Candidate>(), false, false);
        }
        if (actual instanceof CtVariableRead<?>) {
            CtVariableReference<?> ref = ((CtVariableRead<?>) actual).getVariable();
            if (!(ref instanceof CtLocalVariableReference)) {
                return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
            }
            CtInvocation<?> producer = nearestWriteProducer(testMethod, assertion, ref);
            if (producer != null) {
                return graded(testMethod, producer, MutResolution.Signal.LOCAL_VARIABLE_PRODUCER,
                    MutResolution.Tier.T1_PROVEN, new ArrayList<MutResolution.Candidate>(), false, false);
            }
        }
        return none(MutResolution.NoPickReason.NO_VISIBLE_CALL);
    }

    /**
     * Reaching definition on the top-level statement list: nearest write to {@code ref} before the
     * assertion whose RHS is an invocation; falls back to the declaration initializer.
     */
    private static CtInvocation<?> nearestWriteProducer(CtMethod<?> testMethod, CtInvocation<?> assertion,
                                                        CtVariableReference<?> ref) {
        List<CtStatement> statements = testMethod.getBody().getStatements();
        int assertionIndex = 0;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i) == assertion) {
                assertionIndex = i;
                break;
            }
        }
        for (int i = assertionIndex - 1; i >= 0; i--) {
            CtStatement statement = statements.get(i);
            if (statement instanceof CtLocalVariable<?>) {
                CtLocalVariable<?> localVar = (CtLocalVariable<?>) statement;
                if (localVar.getReference().equals(ref)) {
                    CtExpression<?> assignment = localVar.getAssignment();
                    return assignment instanceof CtInvocation<?> ? (CtInvocation<?>) assignment : null;
                }
            } else if (statement instanceof CtAssignment<?, ?>) {
                CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) statement;
                CtExpression<?> assigned = assignment.getAssigned();
                if (assigned instanceof CtVariableWrite<?>
                        && ((CtVariableWrite<?>) assigned).getVariable().equals(ref)) {
                    CtExpression<?> value = assignment.getAssignment();
                    return value instanceof CtInvocation<?> ? (CtInvocation<?>) value : null;
                }
            }
        }
        // Declaration not on the top-level path (nested block): fall back to the initializer.
        CtLocalVariable<?> declaration = ((CtLocalVariableReference<?>) ref).getDeclaration();
        if (declaration != null && declaration.getAssignment() instanceof CtInvocation<?>) {
            return (CtInvocation<?>) declaration.getAssignment();
        }
        return null;
    }

    // --- grading ---

    /** Grade a pick whose mechanism-tier is already known (T1 proofs, weak single producers). */
    private static MutResolution graded(CtMethod<?> testMethod, CtInvocation<?> pick,
                                        MutResolution.Signal signal, MutResolution.Tier mechanismTier,
                                        List<MutResolution.Candidate> alternatives,
                                        boolean inspectorUnwrapped, boolean shallow) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick);
        MutResolution.Tier tier = promote(mechanismTier, corroborators.size());
        return build(statusFor(pick), tier, signal, corroborators, noPickReasonFor(pick), pick,
            alternatives, alternatives.size() + 1, inspectorUnwrapped, shallow);
    }

    /** Grade a pick that position/ranking decided: base T4, promotable to T3/T2 by indicators. */
    private static MutResolution rankedBase(CtMethod<?> testMethod, CtInvocation<?> pick,
                                            MutResolution.Signal signal,
                                            List<MutResolution.Candidate> alternatives) {
        EnumSet<MutResolution.Corroborator> corroborators = corroboratorsFor(testMethod, pick);
        MutResolution.Tier tier = promote(MutResolution.Tier.T4_GUESS, corroborators.size());
        return build(statusFor(pick), tier, signal, corroborators, noPickReasonFor(pick), pick,
            alternatives, alternatives.size() + 1, false, false);
    }

    /**
     * Identity-indicator promotion (spec §B): T1 never moves; a weak single (T3) with >=1 indicator
     * becomes T2; a ranked pick (T4) with exactly 1 becomes T3, with >=2 becomes T2.
     */
    private static MutResolution.Tier promote(MutResolution.Tier base, int indicatorCount) {
        if (base == MutResolution.Tier.T1_PROVEN || indicatorCount == 0) {
            return base;
        }
        if (base == MutResolution.Tier.T3_SINGLE_WEAK) {
            return MutResolution.Tier.T2_CORROBORATED;
        }
        if (base == MutResolution.Tier.T4_GUESS) {
            return indicatorCount >= 2 ? MutResolution.Tier.T2_CORROBORATED : MutResolution.Tier.T3_SINGLE_WEAK;
        }
        return base;
    }

    /** Task 3: name-match only. Task 7 adds FOCAL_CLASS_MEMBER. */
    private static EnumSet<MutResolution.Corroborator> corroboratorsFor(CtMethod<?> testMethod,
                                                                        CtInvocation<?> pick) {
        EnumSet<MutResolution.Corroborator> set = EnumSet.noneOf(MutResolution.Corroborator.class);
        if (nameMatches(testMethod.getSimpleName(), pick.getExecutable().getSimpleName())) {
            set.add(MutResolution.Corroborator.NAME_MATCH);
        }
        return set;
    }

    /**
     * Methods2Test-style name strip: testGcd ~ gcd. Conservative: candidate name must be >= 3 chars
     * (rejects get/is/of coincidences) and contained in the normalized test name.
     */
    static boolean nameMatches(String testMethodName, String candidateName) {
        if (candidateName == null || candidateName.length() < 3) {
            return false;
        }
        String normalizedTest = testMethodName.toLowerCase();
        if (normalizedTest.startsWith("test")) {
            normalizedTest = normalizedTest.substring(4);
        }
        return normalizedTest.contains(candidateName.toLowerCase());
    }

    private static MutResolution.Status statusFor(CtInvocation<?> pick) {
        if (pick == null) {
            return MutResolution.Status.NONE;
        }
        return pick.getExecutable().getDeclaration() instanceof CtMethod<?>
            ? MutResolution.Status.RESOLVED
            : MutResolution.Status.CHARACTERIZATION_ONLY;
    }

    private static MutResolution.NoPickReason noPickReasonFor(CtInvocation<?> pick) {
        if (pick == null || pick.getExecutable().getDeclaration() instanceof CtMethod<?>) {
            return null;
        }
        // Declaration unresolved: JDK/classpath types have no source declaration in the model.
        return pick.getExecutable().getDeclaringType() != null
                && pick.getExecutable().getDeclaringType().getTypeDeclaration() == null
            ? MutResolution.NoPickReason.LIBRARY_DECLARATION
            : MutResolution.NoPickReason.UNRESOLVED_SOURCE_DECLARATION;
    }

    private static MutResolution none(MutResolution.NoPickReason reason) {
        return build(MutResolution.Status.NONE, MutResolution.Tier.T5_NONE, MutResolution.Signal.NONE,
            EnumSet.noneOf(MutResolution.Corroborator.class), reason, null,
            new ArrayList<MutResolution.Candidate>(), 0, false, false);
    }

    private static MutResolution build(MutResolution.Status status, MutResolution.Tier tier,
                                       MutResolution.Signal signal,
                                       EnumSet<MutResolution.Corroborator> corroborators,
                                       MutResolution.NoPickReason reason, CtInvocation<?> pick,
                                       List<MutResolution.Candidate> alternatives, int candidateCount,
                                       boolean inspectorUnwrapped, boolean shallow) {
        // Focal fields: Task 7. Shape/provenance classification: Task 8b.
        return new MutResolution(status, tier, signal, corroborators, reason, pick, alternatives,
            candidateCount, inspectorUnwrapped, shallow, null, MutResolution.FocalSource.NONE, null,
            null, null);
    }

    private static List<MutResolution.Candidate> alternativesExcluding(List<CtInvocation<?>> pool,
                                                                       CtInvocation<?> pick) {
        List<MutResolution.Candidate> alternatives = new ArrayList<>();
        for (CtInvocation<?> invocation : pool) {
            if (invocation != pick) {
                alternatives.add(toCandidate(invocation));
            }
        }
        return alternatives;
    }

    static MutResolution.Candidate toCandidate(CtInvocation<?> invocation) {
        String declaringType = invocation.getExecutable().getDeclaringType() == null
            ? null
            : invocation.getExecutable().getDeclaringType().getQualifiedName();
        return new MutResolution.Candidate(invocation.getExecutable().getSimpleName(), declaringType,
            invocation.toString());
    }

    // Moved verbatim from TestAnalysis.getExecutedBody (deleted there in Task 9).
    private static Optional<CtElement> getExecutedBody(CtElement element) {
        // ... copy the full method body from TestAnalysis.java:178 onwards, unchanged ...
        throw new UnsupportedOperationException("copy from TestAnalysis.getExecutedBody");
    }
}
```

**Note on `getExecutedBody`:** copy the complete method (and any private helpers it calls that are not otherwise referenced) from `TestAnalysis.java` verbatim, replacing the placeholder body above. `TestAnalysis.getActualParameterIndex` is `public` (used by `TestGeneralizationTask:378,525`) — call it, do not copy it.

- [x] **Step 4: Run, expect PASS.**

Run: `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'`
Expected: all 6 tests PASS. If `libraryPick_isCharacterizationOnly` fails on the `NoPickReason`, inspect what Spoon returns for `Integer.parseInt`'s declaring type in a jar-less model and adjust `noPickReasonFor`'s library check (the invariant to preserve: source-model declarations ⇒ `RESOLVED`; everything else ⇒ `CHARACTERIZATION_ONLY` with a non-null reason).

- [x] **Step 5: Delegate from `TestAnalysis`.** Replace the body of `findTestedMethodCall` (`TestAnalysis.java:87-176`) with:

```java
    public static Optional<CtInvocation<?>> findTestedMethodCall(CtMethod<?> method, CtInvocation<?> assertion) {
        MutResolution resolution = MethodUnderTestResolver.resolve(method, assertion);
        return Optional.ofNullable(resolution.getPick());
    }
```

Delete the now-unused private members of `TestAnalysis` that only `findTestedMethodCall` used (`getExecutedBody` stays until Task 9 only if something else references it; if nothing does, delete it now and keep the resolver's copy as the single one).

- [x] **Step 6: Full suite green.** Run: `./gradlew test`. Expected: `BUILD SUCCESSFUL` (pre-existing failures, if any, must be identical before/after — check with `git stash && ./gradlew test` if unsure).

- [x] **Step 7: Commit.**

```bash
git add src/main/java/teralizer/spoon/analysis/ src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java
git commit -m "refactor(mut-id): extract graded MethodUnderTestResolver skeleton"
```

---

### Task 4: Producer trace — transitive locals and fields (M1a)

Replace the single-hop `nearestWriteProducer` with a recursive `trace` that follows variable copies and field writes to the producing call, tracking *proven* (straight-line reaching definition) vs *unproven* (write inside nested control flow).

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java`
- Test: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java`

- [x] **Step 1: Add failing tests** to `MethodUnderTestResolverTest`:

```java
    @Example
    void transitiveVariableCopy_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int a = new Subject().gcd(6, 9); int b = a; org.junit.Assert.assertEquals(3, b); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void reassignedVariable_nearestWriteWins_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().helper(1); x = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void killedDefinition_yieldsNoProducerFromVariable() {
        // int x = gcd(...); x = 5; assert(x) -- the literal write kills the call definition.
        // (Pre-fusion code wrongly returned gcd here; contract divergence (a).)
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); x = 5; org.junit.Assert.assertEquals(5, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotEquals("gcd",
            r.getPick() == null ? null : r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void fieldWriteProducer_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  int r;\n"
            + "  public void t() { this.r = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, this.r); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.FIELD_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void writeInsideNestedBlock_isUnproven_T3orBetter() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t(boolean c) { int x = 0; if (c) { x = new Subject().gcd(6, 9); } org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
        Assert.assertNotEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }
```

- [x] **Step 2: Run, expect FAIL.** Run: `./gradlew test --tests 'teralizer.spoon.analysis.MethodUnderTestResolverTest'`. Expected: the 5 new tests fail (transitive copy, field write, nested block resolve nothing today; killed definition wrongly resolves `gcd`).

- [x] **Step 3: Implement the trace.** Inside `MethodUnderTestResolver`, add a result type and replace `nearestWriteProducer` usage:

```java
    /** A traced producer: the call plus whether the trace was a straight-line reaching definition. */
    private static final class Traced {
        final CtInvocation<?> producer;
        final boolean proven;
        final MutResolution.Signal signal;

        Traced(CtInvocation<?> producer, boolean proven, MutResolution.Signal signal) {
            this.producer = producer;
            this.proven = proven;
            this.signal = signal;
        }
    }
```

Implementation rules (encode exactly; each maps to a test above):
- `traceExpression(expr, testMethod, assertion, visited)` returns `List<Traced>` (empty = no producer visible).
- `CtInvocation` → single `Traced(invocation, true, DIRECT_ACTUAL_CALL)` (inspector unwrap refines this in Task 6).
- `CtVariableRead` of a local → find the **nearest** write (declaration or assignment, any nesting depth) that precedes the assertion in the top-level statement order; use a helper `topLevelIndex(CtElement e, List<CtStatement> body)` that walks `e.getParent()` up to the statement that is a direct child of the method body and returns its index. `proven` = the write is itself a direct child of the method body (straight-line). Recurse on the write's RHS with `signal = LOCAL_VARIABLE_PRODUCER` overriding `DIRECT_ACTUAL_CALL` (signal precedence below); a RHS with no producers (literal) returns empty — the definition kills prior ones.
- If no write precedes the assertion at any depth, fall back to the declaration initializer (pre-fusion behavior), `proven = false`.
- `CtFieldRead` on `this`/unqualified → collect `CtAssignment`s whose `getAssigned()` is a `CtFieldWrite` with the same field simple name, preceding the assertion; nearest wins; `proven` = exactly one such write exists and it is a direct child of the method body; recurse on its RHS with `signal = FIELD_PRODUCER`. Zero writes → empty (setup-method fields are out of scope for v1, spec §Scope).
- Track a `Set<CtVariableReference<?>> visited` to guard cycles (`a = b; b = a;`); a revisited reference returns empty.
- **Signal precedence** when mechanisms compose on one trace path (record the strongest wrapper): `INSPECTOR_UNWRAP > FIELD_PRODUCER > LOCAL_VARIABLE_PRODUCER > SUBEXPRESSION_PRODUCER > DIRECT_ACTUAL_CALL`.

Wire into `resolveValueAssertion`: one traced producer with `proven=true` → `graded(..., T1_PROVEN, ...)`; one with `proven=false` → `graded(..., T3_SINGLE_WEAK, ...)`; multiple → hold onto the list (Task 8 ranks it; until Task 8, pick the first and grade via `rankedBase` with the rest as alternatives); empty → keep the current `none(NO_VISIBLE_CALL)` (Task 8 replaces this with slice elimination).

- [x] **Step 4: Run, expect PASS** (same command). All Task 3 tests must still pass.

- [x] **Step 5: Commit.**

```bash
git add src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java
git commit -m "feat(mut-id): trace producers through variable copies and field writes"
```

---

### Task 5: Producer trace — sub-expression descent (M1b)

**Files:** same two files.

- [x] **Step 1: Add failing tests:**

```java
    @Example
    void comparisonOperand_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().gcd(6, 9) > 0); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.SUBEXPRESSION_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void twoProducerComposite_isRankedNotAbstained() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(5, new Subject().gcd(6, 9) + new Subject().helper(2)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotNull(r.getPick());
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        Assert.assertNotEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }
```

- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Extend `traceExpression`:** `CtBinaryOperator` → union of both operands' traces, each re-tagged `signal = SUBEXPRESSION_PRODUCER`, `proven` only if the union has exactly one element; `CtUnaryOperator` → trace the operand, re-tagged `SUBEXPRESSION_PRODUCER`. (Spoon models casts as metadata on the expression, not wrapper nodes — nothing to do for casts.)
- [x] **Step 4: Run, expect PASS.**
- [x] **Step 5: Commit.** `git commit -am "feat(mut-id): descend sub-expressions to producer calls"`

---

### Task 6: Inspector unwrap (M1c) + shallow-pick flag

When the asserted value is a zero-argument inspector on a computed receiver, the MUT is the receiver's producer; the inspector is part of the oracle. When the receiver's producer is unreachable, keep the inspector pick but flag it shallow (spec §shallow-pick).

**Files:** same two files.

- [x] **Step 1: Add failing tests:**

```java
    @Example
    void inspectorOnComputedReceiver_unwrapsToProducer() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().compute(5).isEmpty()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("compute", r.getPick().getExecutable().getSimpleName());
        Assert.assertEquals(MutResolution.Signal.INSPECTOR_UNWRAP, r.getDecidingSignal());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertTrue(r.isInspectorUnwrapped());
        Assert.assertFalse(r.isShallowInspectorPick());
    }

    @Example
    void inspectorOnVariableWithProducer_unwrapsThroughVariable() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { java.util.List<Integer> l = new Subject().compute(5); org.junit.Assert.assertTrue(l.isEmpty()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("compute", r.getPick().getExecutable().getSimpleName());
        Assert.assertTrue(r.isInspectorUnwrapped());
    }

    @Example
    void inspectorWithUnreachableReceiver_keptButFlaggedShallow() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  Subject sut = new Subject();\n"
            + "  public void t() { org.junit.Assert.assertEquals(0, sut.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("getTotal", r.getPick().getExecutable().getSimpleName());
        Assert.assertTrue(r.isShallowInspectorPick());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier()); // dataflow-true, shallow-flagged
    }
```

- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.** Add:

```java
    /**
     * Ghafari-style inspector (ICST'15): zero-argument, non-void, and either a conventional
     * accessor name or declared on a JDK type. Conservative by design.
     */
    static boolean isInspector(CtInvocation<?> invocation) {
        if (!invocation.getArguments().isEmpty()) {
            return false;
        }
        if (invocation.getType() == null || "void".equals(invocation.getType().getSimpleName())) {
            return false;
        }
        String name = invocation.getExecutable().getSimpleName();
        if (name.startsWith("get") || name.startsWith("is") || name.startsWith("has")) {
            return true;
        }
        switch (name) {
            case "size": case "length": case "isEmpty": case "toString": case "hashCode":
            case "name": case "ordinal": case "value": case "count":
                return true;
            default:
                break;
        }
        String declaring = invocation.getExecutable().getDeclaringType() == null
            ? "" : invocation.getExecutable().getDeclaringType().getQualifiedName();
        return declaring.startsWith("java.") || declaring.startsWith("javax.");
    }
```

In `traceExpression`, for a `CtInvocation` that `isInspector`: if the receiver (`getTarget()`) is a `CtInvocation`, recurse on the receiver, re-tag `INSPECTOR_UNWRAP`, set `inspectorUnwrapped`; if the receiver is a `CtVariableRead`/`CtFieldRead`, trace the receiver's producer — found ⇒ that producer (`INSPECTOR_UNWRAP`), not found ⇒ the inspector itself with `shallowInspectorPick = true`. Non-inspector invocations keep current behavior. Thread the two booleans through `Traced` (add fields) into `graded(...)`.

- [x] **Step 4: Run, expect PASS.** All prior tests still green (note: `directInvocationInActualPosition_isT1Proven` uses `gcd(6,9)` which has arguments ⇒ not an inspector ⇒ unaffected).
- [x] **Step 5: Commit.** `git commit -am "feat(mut-id): unwrap inspectors to receiver producers, flag shallow picks"`

---

### Task 7: Focal class — path+name resolution and focal corroboration

**Files:** same two files.

- [x] **Step 1: Add failing tests:**

```java
    @Example
    void focalClass_nameDerived_corroboratesMembership() {
        // Weak position pick inside assertThrows multi-call: Subject membership + no name match => T3.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("Subject", r.getFocalType());
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, r.getFocalSource());
        Assert.assertEquals(Boolean.TRUE, r.getFocalAgreement());
        Assert.assertTrue(r.getCorroborators().contains(MutResolution.Corroborator.FOCAL_CLASS_MEMBER));
        Assert.assertEquals(MutResolution.Tier.T3_SINGLE_WEAK, r.getTier());
    }

    @Example
    void nameAndFocalAgreement_promoteToT2() {
        // Test method named tGcd -> name-match on gcd; + focal membership => 2 indicators => T2.
        String source =
            "public class SubjectTest {\n"
            + "  public void testGcd() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}";
        MutResolution r = resolveNamed(source, "testGcd", SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T2_CORROBORATED, r.getTier());
    }

    @Example
    void pathMirror_helper() {
        Assert.assertEquals("src/main/java/com/x/Foo.java",
            MethodUnderTestResolver.mirrorTestPath("src/test/java/com/x/FooTest.java"));
        Assert.assertEquals("src/main/java/com/x/Foo.java",
            MethodUnderTestResolver.mirrorTestPath("src/test/java/com/x/TestFoo.java"));
        Assert.assertNull(MethodUnderTestResolver.mirrorTestPath("src/test/java/com/x/Helper.java"));
        Assert.assertNull(MethodUnderTestResolver.mirrorTestPath("src/main/java/com/x/Foo.java"));
    }
```

Add this helper next to `resolve`/`resolveNth` in the test class:

```java
    static MutResolution resolveNamed(String testSource, String testMethodName, String... otherSources) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        for (int i = 0; i < otherSources.length; i++) {
            launcher.addInputResource(new VirtualFile(otherSources[i], "Other" + i + ".java"));
        }
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName(testMethodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        return MethodUnderTestResolver.resolve(testMethod, assertion);
    }
```

- [x] **Step 2: Run, expect FAIL.**
- [x] **Step 3: Implement.**

```java
    /**
     * Mirror a test-source path to its production twin (Methods2Test path matching):
     * src/test/java/<pkg>/FooTest.java -> src/main/java/<pkg>/Foo.java. Returns null when the path
     * is not under src/test or the file name carries no Test/Tests/IT/ITCase/TestCase prefix/suffix.
     */
    static String mirrorTestPath(String testPath) {
        if (testPath == null || !testPath.contains("src/test/java/")) {
            return null;
        }
        int slash = testPath.lastIndexOf('/');
        String dir = testPath.substring(0, slash + 1).replace("src/test/java/", "src/main/java/");
        String file = testPath.substring(slash + 1);
        if (!file.endsWith(".java")) {
            return null;
        }
        String base = file.substring(0, file.length() - ".java".length());
        String stripped = stripTestAffix(base);
        return stripped == null ? null : dir + stripped + ".java";
    }

    /** FooTest/FooTests/FooIT/FooITCase/FooTestCase/TestFoo -> Foo; null when no affix present. */
    static String stripTestAffix(String simpleName) {
        String[] suffixes = { "TestCase", "ITCase", "Tests", "Test", "IT" };
        for (String suffix : suffixes) {
            if (simpleName.endsWith(suffix) && simpleName.length() > suffix.length()) {
                return simpleName.substring(0, simpleName.length() - suffix.length());
            }
        }
        if (simpleName.startsWith("Test") && simpleName.length() > 4) {
            return simpleName.substring(4);
        }
        return null;
    }
```

`resolveFocalType(CtMethod<?> testMethod)` (private): derive the candidate simple name via `stripTestAffix(testMethod.getDeclaringType().getSimpleName())`; look it up in the model (`testMethod.getFactory().getModel()`), preferring a type in the same package. Separately compute the path mirror from the test class's `getPosition().getFile()` when a real position exists (virtual files: skip), and check whether the name-derived type's file matches it. Both agree → `PATH_AND_NAME`; name only → `NAME_ONLY`; path only (name-derived lookup failed but a type at the mirrored path exists) → `PATH_ONLY`; neither → `NONE`. Return a small `Focal { String qualifiedName; FocalSource source; }` holder.

Wire in: compute the focal once per `resolve(...)` call; in `corroboratorsFor`, add `FOCAL_CLASS_MEMBER` when the pick's declaring type's qualified name equals the focal's; thread `focalType`/`focalSource`/`focalAgreement` (pick's declaring type equals focal — null when focal is `NONE` or pick is null) through `build(...)`.

- [x] **Step 4: Run, expect PASS.**
- [x] **Step 5: Commit.** `git commit -am "feat(mut-id): resolve focal class by path+name, corroborate membership"`

---

### Task 8: Candidate pool + ranking (unique-producer elimination M1d, ranked guess T4)

The last resolver piece: when dataflow yields no producer, the pre-assertion slice becomes the candidate pool; cardinality 1 is a proof, otherwise the ranking function decides.

**Files:** same two files.

- [x] **Step 1: Add failing tests:**

```java
    @Example
    void uniqueProductionCallInSlice_isT1Elimination() {
        // Mutator-then-inspect: process() is the only production call; getTotal() is the asserted
        // inspector with unreachable receiver-producer -- elimination picks process.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        // getTotal is a shallow inspector; the slice holds exactly one other production call.
        Assert.assertEquals("process", r.getPick().getExecutable().getSimpleName());
        Assert.assertEquals(MutResolution.Signal.UNIQUE_PRODUCER_ELIMINATION, r.getDecidingSignal());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }

    @Example
    void multipleFeasibleCandidates_rankedGuessWithAlternatives() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); s.helper(2); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotNull(r.getPick());
        Assert.assertEquals(MutResolution.Signal.RANKED_GUESS, r.getDecidingSignal());
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        // helper(int->int) is type-eligible, process(int->void) is not => ranking prefers helper.
        Assert.assertEquals("helper", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void killedDefinition_notResurrectedBySliceElimination() {
        // Dataflow refuted gcd (the write of 5 kills it); elimination must NOT bring it back.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); x = 5; org.junit.Assert.assertEquals(5, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.NO_VISIBLE_CALL, r.getNoPickReason());
    }

    @Example
    void noCallsAtAll_isT5None() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = 1 + 2; org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T5_NONE, r.getTier());
        Assert.assertEquals(MutResolution.NoPickReason.NO_VISIBLE_CALL, r.getNoPickReason());
    }
```

- [x] **Step 2: Run, expect FAIL/PASS as follows.** The first two tests fail (both currently resolve `getTotal` shallow); the killed-definition and no-calls tests already pass via Tasks 3-4 — keep them as regression pins that Step 3 must not break.

- [x] **Step 3: Implement.**

Production-call pool (`productionCallsBefore(testMethod, assertion)`):
- All `CtInvocation`s in the test method body whose `topLevelIndex` precedes the assertion's, excluding invocations nested inside the assertion statement itself.
- Exclude assertion-library calls: executable simple name starting with `assert`/`fail`/`verify`, or declaring type qualified name starting with `org.junit`, `org.hamcrest`, `org.assertj`, `org.mockito`, `org.testng`.
- Exclude calls declared on the test class itself or its superclasses (test helpers), and calls whose declaring type's qualified name starts with `java.` or `javax.` (never the MUT).

Type eligibility (`typeEligible(CtInvocation<?>)`): declaration resolves to a source `CtMethod` with ≥1 parameter passing `teralizer.util.TypeCapability.supportsGeneratedInput(paramTypeQualifiedName)` and return passing `TypeCapability.supportsReturnValue(returnTypeQualifiedName)`; a null declaration is ineligible.

Ranking comparator (spec §ranking — lexicographic, all descending-preference):
1. `typeEligible` true first;
2. focal-class member first;
3. `nameMatches(testMethodName, candidateName)` first;
4. larger `topLevelIndex` first (closer to the assertion);
5. source-position order (deterministic tie-break).

Wiring into `resolveValueAssertion` — replace the terminal `none(NO_VISIBLE_CALL)` and the shallow-inspector terminal. **Elimination only fires when dataflow was *silent*, never when it was *refuting*:** track whether the trace saw a killed definition (a reaching write whose RHS had no producers — the asserted value is a known constant). A killed-definition trace goes straight to `none(NO_VISIBLE_CALL)`; resurrecting a refuted producer from the slice would contradict the dataflow proof (and re-break contract divergence (a)).
- Dataflow produced ≥2 candidates → rank them; winner via `rankedBase(..., RANKED_GUESS, losers)` (keep the dataflow signal when all candidates came from one composite expression: `SUBEXPRESSION_PRODUCER` stays the deciding signal, tier from `rankedBase`).
- Dataflow produced a **shallow inspector pick** → compute the pool excluding the inspector: pool size 1 ⇒ that call, `UNIQUE_PRODUCER_ELIMINATION`, T1 (cardinality-forced; the seed-check backstops coherence); pool ≥2 ⇒ rank pool, `RANKED_GUESS`; pool empty ⇒ keep the flagged shallow inspector (Task 6 behavior).
- Dataflow empty (no variable/field/call visible at all, and no killed definition) → pool size 1 ⇒ `UNIQUE_PRODUCER_ELIMINATION` T1; ≥2 ⇒ `RANKED_GUESS`; 0 ⇒ `none(NO_VISIBLE_CALL)`.

- [x] **Step 4: Run, expect PASS.** Verify no prior test regressed — in particular `inspectorWithUnreachableReceiver_keptButFlaggedShallow` (its pool is empty: `new Subject()` is a constructor call, not a `CtInvocation`).
- [x] **Step 5: Commit.** `git commit -am "feat(mut-id): unique-producer elimination and ranked-guess tiers"`

---

### Task 8b: Input-topology classification (`actual_shape`, `receiver_provenance`)

Pure telemetry — classifies *where inputs would enter and where the oracle sits* for every
assertion, so the recipe-increment decisions (expression slices, statement slices) become
`GROUP BY` queries. Design + taxonomy: `2026-07-02-input-topology-spike`. No influence on the
pick, tier, or status.

**Files:**
- Modify: `src/main/java/teralizer/spoon/analysis/MethodUnderTestResolver.java`
- Modify: `src/main/java/teralizer/spoon/analysis/MutResolution.java` (add `withTopology`)
- Test: `src/test/java/teralizer/spoon/analysis/MethodUnderTestResolverTest.java`

- [x] **Step 1: Add failing tests:**

```java
    @Example
    void topology_inlineCtorReceiver() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().isPrime(7)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.ActualShape.CTOR_RECEIVER_CALL, r.getActualShape());
        Assert.assertEquals(MutResolution.ReceiverProvenance.INLINE_CTOR, r.getReceiverProvenance());
    }

    @Example
    void topology_localCtorReceiver_cleanVsMutated() {
        MutResolution clean = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); org.junit.Assert.assertEquals(0, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.ActualShape.SINGLE_CALL, clean.getActualShape());
        Assert.assertEquals(MutResolution.ReceiverProvenance.LOCAL_CTOR, clean.getReceiverProvenance());

        MutResolution mutated = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        // the pick here is process (Task 8 elimination); topology describes the ASSERTED expression,
        // whose receiver s is a mutated local ctor -- the R2 (statement-slice) family marker.
        Assert.assertEquals(MutResolution.ReceiverProvenance.LOCAL_CTOR_MUTATED, mutated.getReceiverProvenance());
    }

    @Example
    void topology_fieldReceiver_andOperatorShape() {
        MutResolution field = resolve(
            "public class SubjectTest {\n"
            + "  Subject sut = new Subject();\n"
            + "  public void t() { org.junit.Assert.assertEquals(0, sut.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.ReceiverProvenance.FIELD, field.getReceiverProvenance());

        MutResolution op = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().gcd(6, 9) > 0); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.ActualShape.OPERATOR_COMPOSITE, op.getActualShape());
    }

    @Example
    void topology_chainedCalls() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().compute(5).isEmpty()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.ActualShape.CHAINED_CALLS_END0ARG, r.getActualShape());
        Assert.assertEquals(MutResolution.ReceiverProvenance.INLINE_CTOR, r.getReceiverProvenance());
    }
```

- [x] **Step 2: Run, expect FAIL.**

- [x] **Step 3: Implement.** In `MutResolution`, add a package-private enrichment copy (keeps every existing constructor call site unchanged):

```java
    MutResolution withTopology(ActualShape shape, ReceiverProvenance provenance) {
        return new MutResolution(this.status, this.tier, this.decidingSignal, this.corroborators,
            this.noPickReason, this.pick, this.alternatives, this.candidateCount,
            this.inspectorUnwrapped, this.shallowInspectorPick, this.focalType, this.focalSource,
            this.focalAgreement, shape, provenance);
    }
```

In `MethodUnderTestResolver.resolve(...)`, wrap the existing body: rename it `resolveInternal`, then:

```java
    public static MutResolution resolve(CtMethod<?> testMethod, CtInvocation<?> assertion) {
        MutResolution resolution = resolveInternal(testMethod, assertion);
        CtExpression<?> actual = actualExpression(testMethod, assertion); // null for assertThrows/unsupported
        return resolution.withTopology(classifyShape(actual), receiverProvenance(actual, testMethod, assertion));
    }
```

`actualExpression`: null assertion or `assertThrows` or missing actual index → null; else the argument at `TestAnalysis.getActualParameterIndex`.

`classifyShape(CtExpression<?> actual)` — AST-exact version of the spike classifier (`analysis/src/teralizer/input_topology.py` documents the taxonomy):
- null → `NONE`; `CtLiteral` → `LITERAL`; `CtVariableRead` → `VARIABLE`; `CtFieldRead` → `FIELD_ACCESS`;
- `CtBinaryOperator`/`CtUnaryOperator`/`CtConditional` → `OPERATOR_COMPOSITE`; `CtArrayRead` → `ARRAY_INDEX`;
- `CtLambda`/`CtExecutableReferenceExpression` → `LAMBDA_OR_METHODREF`; `CtConstructorCall` → `CTOR_ONLY`;
- `CtInvocation`: walk `getTarget()` transitively; count invocations in the chain. 1 invocation whose target is a `CtConstructorCall` → `CTOR_RECEIVER_CALL`; 1 invocation otherwise → `SINGLE_CALL`; ≥2 → outermost call has zero args ? `CHAINED_CALLS_END0ARG` : `CHAINED_CALLS_ENDNARG`;
- anything else (type casts are metadata, not nodes) → recurse-free default `NONE`.

`receiverProvenance(actual, testMethod, assertion)` — only for an invocation-rooted actual (else `NONE`). Take the *root receiver* (walk `getTarget()` past invocations to the first non-invocation):
- `CtConstructorCall` → `INLINE_CTOR`; `CtFieldRead`/`CtThisAccess`-qualified field → `FIELD`;
- `CtTypeAccess` (static) or null target or parameter read → `PARAM_OR_STATIC`;
- `CtVariableRead` of a local: find its reaching definition (reuse Task 4's walk). Definition RHS is a `CtConstructorCall` → any statement strictly between the definition and the assertion that invokes a method on that variable? `LOCAL_CTOR_MUTATED` : `LOCAL_CTOR`. Definition RHS anything else → `LOCAL_OTHER`.

- [x] **Step 4: Run, expect PASS** (all prior tests too — topology must not change any pick/tier).
- [x] **Step 5: Commit.** `git commit -am "feat(mut-id): classify input topology (actual shape, receiver provenance)"`

---

### Task 9: `TestAnalysisTask` integration — observation rows + grade separation

**Files:**
- Modify: `src/main/java/teralizer/processing/task/TestAnalysisTask.java:87-184`
- Modify: `src/main/java/teralizer/spoon/analysis/TestAnalysis.java` (delete `findTestedMethodCall`, `getExecutedBody` if still present)
- Test: `src/test/java/teralizer/processing/task/MutResolutionObservationMapperTest.java` (create)
- Create: `src/main/java/teralizer/processing/task/MutResolutionObservationMapper.java`

- [ ] **Step 1: Write the failing mapper test** (pure mapping, no DB — jOOQ records are instantiable directly, precedent `StringOperationFilterTest`):

```java
package teralizer.processing.task;

import com.google.gson.Gson;
import net.jqwik.api.Example;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import org.junit.Assert;
import teralizer.spoon.analysis.MethodUnderTestResolverTest;
import teralizer.spoon.analysis.MutResolution;

public class MutResolutionObservationMapperTest {

    @Example
    void mapsResolvedPick() {
        MutResolution r = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, new Subject().gcd(6, 9)); }\n"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(r, 11L, 22L, 33L, new Gson(), record);

        Assert.assertEquals(Long.valueOf(33L), record.getAssertionId());
        Assert.assertEquals(Long.valueOf(11L), record.getProjectId());
        Assert.assertEquals(Long.valueOf(22L), record.getTestId());
        Assert.assertEquals("RESOLVED", record.getStatus());
        Assert.assertEquals("T1_PROVEN", record.getConfidenceTier());
        Assert.assertEquals("DIRECT_ACTUAL_CALL", record.getDecidingSignal());
        Assert.assertEquals("gcd", record.getResolvedMethodName());
        Assert.assertEquals(Integer.valueOf(1), record.getCandidateCount());
        Assert.assertNull(record.getNoPickReason());
        Assert.assertEquals("[\"int\",\"int\"]", record.getResolvedParameterTypes());
        Assert.assertEquals("int", record.getResolvedReturnType());
    }

    @Example
    void mapsNone() {
        MutResolution r = MethodUnderTestResolverTest.resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = 1 + 2; org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            MethodUnderTestResolverTest.SUBJECT_SOURCE);

        MutResolutionObservationRecord record = new MutResolutionObservationRecord();
        MutResolutionObservationMapper.map(r, 1L, 2L, 3L, new Gson(), record);

        Assert.assertEquals("NONE", record.getStatus());
        Assert.assertEquals("T5_NONE", record.getConfidenceTier());
        Assert.assertEquals("NO_VISIBLE_CALL", record.getNoPickReason());
        Assert.assertNull(record.getResolvedMethodName());
        Assert.assertEquals("VARIABLE", record.getActualShape());
        Assert.assertEquals("NONE", record.getReceiverProvenance());
    }
}
```

(Make `resolve`/`resolveNth`/`SUBJECT_SOURCE` in `MethodUnderTestResolverTest` `public static` so this test can reuse them.)

- [ ] **Step 2: Run, expect FAIL.** Run: `./gradlew test --tests 'teralizer.processing.task.MutResolutionObservationMapperTest'`.

- [ ] **Step 3: Write the mapper** (complete file):

```java
package teralizer.processing.task;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import org.jooq.generated.tables.records.MutResolutionObservationRecord;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import teralizer.spoon.analysis.MutResolution;
import teralizer.util.TypeCapability;

/** Maps a MutResolution onto a mut_resolution_observation row. Pure; unit-tested without a DB. */
final class MutResolutionObservationMapper {

    private MutResolutionObservationMapper() {
    }

    static void map(MutResolution resolution, long projectId, long testId, long assertionId,
                    Gson gson, MutResolutionObservationRecord record) {
        record.setAssertionId(assertionId);
        record.setProjectId(projectId);
        record.setTestId(testId);

        record.setStatus(resolution.getStatus().name());
        record.setConfidenceTier(resolution.getTier().name());
        record.setDecidingSignal(resolution.getDecidingSignal().name());

        List<String> corroborators = new ArrayList<>();
        for (MutResolution.Corroborator corroborator : resolution.getCorroborators()) {
            corroborators.add(corroborator.name());
        }
        record.setCorroboratingSignals(corroborators.isEmpty() ? null : gson.toJson(corroborators));
        record.setNoPickReason(resolution.getNoPickReason() == null ? null : resolution.getNoPickReason().name());
        record.setCandidateCount(resolution.getCandidateCount());

        CtInvocation<?> pick = resolution.getPick();
        if (pick != null) {
            record.setResolvedCallSource(pick.toString());
            record.setResolvedMethodName(pick.getExecutable().getSimpleName());
            CtTypeReference<?> declaring = pick.getExecutable().getDeclaringType();
            record.setResolvedDeclaringType(declaring == null ? null : declaring.getQualifiedName());

            if (pick.getExecutable().getDeclaration() instanceof CtMethod<?>) {
                CtMethod<?> method = (CtMethod<?>) pick.getExecutable().getDeclaration();
                List<String> parameterTypes = new ArrayList<>();
                boolean anyParamSupported = false;
                for (CtParameter<?> parameter : method.getParameters()) {
                    String qualifiedName = parameter.getType().getQualifiedName();
                    parameterTypes.add(qualifiedName);
                    anyParamSupported |= TypeCapability.supportsGeneratedInput(qualifiedName);
                }
                String returnType = method.getType().getQualifiedName();
                record.setResolvedParameterTypes(gson.toJson(parameterTypes));
                record.setResolvedReturnType(returnType);
                record.setCandidateParamCount(method.getParameters().size());
                record.setCandidateParamSupported(anyParamSupported);
                record.setCandidateReturnSupported(TypeCapability.supportsReturnValue(returnType));
            }
        }

        record.setInspectorUnwrapped(resolution.isInspectorUnwrapped());
        record.setShallowInspectorPick(resolution.isShallowInspectorPick());
        record.setFocalType(resolution.getFocalType());
        record.setFocalTypeSource(resolution.getFocalSource().name());
        record.setFocalAgreement(resolution.getFocalAgreement());

        if (!resolution.getAlternatives().isEmpty()) {
            record.setCandidateDetails(gson.toJson(resolution.getAlternatives()));
        }

        record.setActualShape(resolution.getActualShape().name());
        record.setReceiverProvenance(resolution.getReceiverProvenance().name());
    }
}
```

- [ ] **Step 4: Run, expect PASS.**

- [ ] **Step 5: Integrate into `TestAnalysisTask.createAssertionRecords`.** At `TestAnalysisTask.java:114`, replace

```java
            CtInvocation<?> testedMethodCall = TestAnalysis.findTestedMethodCall(testMethod, assertionCall).orElse(null);
```

with

```java
            MutResolution resolution = MethodUnderTestResolver.resolve(testMethod, assertionCall);
            CtInvocation<?> testedMethodCall = resolution.getPick();
```

(imports: `teralizer.spoon.analysis.MethodUnderTestResolver`, `teralizer.spoon.analysis.MutResolution`). The existing `if (testedMethodCall != null)` block stays byte-identical — it already implements grade separation (declaration-dependent fields only under `testedMethod != null && testedType != null`).

Then replace the batch write (`TestAnalysisTask.java:178-183`). Currently:

```java
            record.setIsIncluded(true);

            records.add(record);
        }

        create.batchStore(records).execute();
```

jOOQ `batchStore` does not populate generated identity keys, and the observation row needs `assertion.id`. Switch to per-record `store()` (identity refresh is jOOQ's documented single-record behavior; precedent: `TestExecutionTask.java:86`, `JunitDataCollectionTask.java:307`). Inside the loop, after `record.setIsIncluded(true);`:

```java
            record.setIsIncluded(true);
            record.store();

            MutResolutionObservationRecord observation = create.newRecord(Tables.MUT_RESOLUTION_OBSERVATION);
            MutResolutionObservationMapper.map(resolution, this.getProjectId(), this.getTestId(),
                record.getId(), gson, observation);
            observation.store();
        }
```

and delete the trailing `create.batchStore(records).execute();` plus the now-unused `records` list.

- [ ] **Step 6: Delete the dead façade.** Remove `TestAnalysis.findTestedMethodCall` and (if still present) `TestAnalysis.getExecutedBody`. `TestAnalysis` retains `findAllAsserts`, `getActualParameterIndex`, `isJUnit4Assertion`, `isJUnit5Assertion` (callers: `TestAnalysisTask`, `TestGeneralizationTask:378,525`). Run `./gradlew compileJava` — any compile error here means a missed caller; fix by migrating it to `MethodUnderTestResolver.resolve`.

- [ ] **Step 7: Full suite.** Run: `./gradlew test`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit.**

```bash
git add src/main/java/teralizer/ src/test/java/teralizer/
git commit -m "feat(mut-id): wire fusion resolver into TestAnalysisTask with observation rows"
```

---

### Task 10: Analysis-side tier funnel

**Files:**
- Create: `analysis/src/teralizer/mut_resolution_funnel.py`

- [ ] **Step 1: Write the module** (complete file; conventions mirror `analysis/src/teralizer/mut_id_targets.py` — pandas + SQLAlchemy `text()`, module-level `main`, `db_config`):

```python
"""MUT-resolution confidence-tier funnel.

Reports how method-under-test identification resolved across the corpus:
per-tier/status/signal counts, the MissingValue cross-tab (which resolution
outcomes still hit ``MissingValueFilter``), and ranked-guess provenance
(how many T4 guesses exist and what their alternatives were).

Tier-slicing is invariant #3 of the fusion spec
(``docs/plans/2026-07-02-mut-id-confidence-fusion.md``): headline claims
cite T1/T2 only; T3/T4 are best-effort and reported separately.

Run:  uv run --directory analysis python -m teralizer.mut_resolution_funnel
"""

from __future__ import annotations

import pandas as pd
from sqlalchemy import Connection, text

from teralizer.config import db_config

_MISSING_VALUE = "teralizer.processing.filter.MissingValueFilter"


def get_tier_funnel(conn: Connection) -> pd.DataFrame:
    """Assertion counts per (status, confidence_tier, deciding_signal)."""
    sql = text(
        """
        SELECT status, confidence_tier, deciding_signal,
               COUNT(*) AS assertions,
               SUM(CASE WHEN shallow_inspector_pick THEN 1 ELSE 0 END) AS shallow_picks,
               SUM(CASE WHEN inspector_unwrapped THEN 1 ELSE 0 END) AS inspector_unwraps
        FROM mut_resolution_observation
        GROUP BY status, confidence_tier, deciding_signal
        ORDER BY confidence_tier, status, assertions DESC
        """
    )
    return pd.read_sql(sql, conn)


def get_missing_value_cross_tab(conn: Connection) -> pd.DataFrame:
    """MissingValue rejects split by resolution status/tier/no_pick_reason."""
    sql = text(
        """
        SELECT o.status, o.confidence_tier, o.no_pick_reason, COUNT(*) AS mv_rejects
        FROM mut_resolution_observation o
        JOIN filter_result fr ON fr.assertion_id = o.assertion_id
        WHERE fr.filter_name = :mv AND fr.decision = 'REJECT'
        GROUP BY o.status, o.confidence_tier, o.no_pick_reason
        ORDER BY mv_rejects DESC
        """
    )
    return pd.read_sql(sql, conn, params={"mv": _MISSING_VALUE})


def get_guess_provenance(conn: Connection) -> pd.DataFrame:
    """T4 ranked guesses with their alternative counts and type eligibility."""
    sql = text(
        """
        SELECT o.project_id, o.assertion_id, o.resolved_method_name, o.candidate_count,
               o.candidate_param_supported, o.candidate_return_supported,
               o.focal_agreement, o.candidate_details
        FROM mut_resolution_observation o
        WHERE o.confidence_tier = 'T4_GUESS'
        ORDER BY o.candidate_count DESC
        """
    )
    return pd.read_sql(sql, conn)


def get_topology_cross_tab(conn: Connection) -> pd.DataFrame:
    """actual_shape x receiver_provenance -- the R1/R2 recipe-increment sizing
    (decision gates in docs/plans/2026-07-02-input-topology-spike.md)."""
    sql = text(
        """
        SELECT actual_shape, receiver_provenance, COUNT(*) AS assertions,
               SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END) AS resolved
        FROM mut_resolution_observation
        GROUP BY actual_shape, receiver_provenance
        ORDER BY assertions DESC
        """
    )
    return pd.read_sql(sql, conn)


def main() -> None:
    engine = db_config.create_engine()
    with engine.connect() as conn:
        funnel = get_tier_funnel(conn)
        print("== Tier funnel ==")
        print(funnel.to_string(index=False))

        total = int(funnel["assertions"].sum())
        if total:
            by_tier = funnel.groupby("confidence_tier")["assertions"].sum()
            print("\n== Tier shares ==")
            for tier, count in by_tier.items():
                print(f"{tier}: {count} ({count / total:.1%})")

        print("\n== MissingValue cross-tab ==")
        print(get_missing_value_cross_tab(conn).to_string(index=False))

        guesses = get_guess_provenance(conn)
        print(f"\n== T4 guesses: {len(guesses)} ==")
        print(guesses.head(20).to_string(index=False))

        print("\n== Input topology (shape x provenance) ==")
        print(get_topology_cross_tab(conn).to_string(index=False))


if __name__ == "__main__":
    main()
```

**Check `db_config` first:** open `analysis/src/teralizer/config.py` and match how `mut_id_targets.py` actually acquires a connection (if it uses a different helper than `db_config.create_engine()`, mirror that exactly).

- [ ] **Step 2: Validate.** Run: `uv run --directory analysis python validate.py --changed`. Expected: ruff/ty/pytest pass. (The module needs a populated DB to produce output; validation only needs it to lint/type-check.)

- [ ] **Step 3: Commit.**

```bash
git add analysis/src/teralizer/mut_resolution_funnel.py
git commit -m "feat(analysis): add MUT-resolution tier funnel"
```

---

### Task 11: Corpus verification on the 20-project spike (operator-assisted)

Prove: recall rose, tiers are honest, the working corpus did not regress, and the JPF compute cost is known. **Needs Docker + hours of pipeline runtime; coordinate with the operator rather than launching unattended.**

**Files:** none (disposable DB — e.g. `postgres_reporeapers_rerun` regenerated, never a core DB). Record results in `docs/plans/2026-06-28-mut-id-targeting-and-coverage.md`.

- [ ] **Step 1: Rerun the 20-project spike** with the current branch into a fresh disposable DB (operator provides the config; `./gradlew run -Dteralizer.config=<spike-config>`).
- [ ] **Step 2: Tier funnel.** Run `uv run --directory analysis python -m teralizer.mut_resolution_funnel` against the spike DB. Record: per-tier shares; `MissingValue`×status cross-tab. Acceptance: `MissingValue` rejects with `status='RESOLVED'` ≈ 0 (a resolved pick must populate `tested_*`); the pre-fusion `MissingValue` count for the same projects strictly exceeds the post-fusion count.
- [ ] **Step 3: No-regression census.** Compare generalization counts per project against the pre-fusion baseline for the same 20 projects (operator has the baseline; the ~250-generalization census is the reference). Acceptance: no project loses generalizations. Any loss ⇒ diff the picks for that project's assertions (`resolved_method_name` vs the old `tested_method_name`) — a changed pick on a previously-working assertion is a contract-1 violation; fix before proceeding. Divergences (a)/(b) from the behavior contract are the only sanctioned pick changes.
- [ ] **Step 4: Mis-targeting spot check, stratified.** Sample manually: 10 T1, 5 T2, 10 T3, 20 T4 newly-resolved assertions (`ORDER BY random()` with a fixed seed via `setseed`). Read each test's source; record whether the pick is the developer-intended method. Acceptance: T1 = 100% intended (any miss is a resolver bug — fix and re-run); T3/T4 rates recorded in the audit doc (no threshold — they are the honesty story and the He-et-al. comparison point).
- [ ] **Step 5: Compute cost + seed-kill share.** From the `task` table, compare total wall-clock of the JPF block (stages `EXECUTE_JPF`/`ANALYZE_JPF`) and generalized-build/test stages before vs after fusion for the same projects; record the delta alongside the newly-attempted assertion count (previously-`MissingValue` assertions now entering JPF, from Step 2). Additionally record the **seed-kill share**: among newly-attempted generalizations, the fraction of `jqwik_property_execution` rows with `tries = 1 AND diagnostic_kind = 'ASSERTION_FAILED'` (first-trial failure = incoherent pick caught by the coherence backstop — the direct empirical test of the spec's bounded-downside claim, and the decision input for the early coherence gate, `2026-07-02-recipe-seam-review` §R-B).
- [ ] **Step 6: Topology distribution (the R1/R2 decision gate).** From the funnel's shape × provenance cross-tab, record: (a) the realized R1 opportunity — `CHAINED_*`/`OPERATOR_COMPOSITE`/`CTOR_ONLY` rows with input sites; (b) the R2 sub-family — `SINGLE_CALL` × `LOCAL_CTOR`/`LOCAL_CTOR_MUTATED` counts. Per `2026-07-02-input-topology-spike` §R2: >5k clean `LOCAL_CTOR`-rooted zero-arg inspectors ⇒ design R2 properly; else T3 stays out of scope.
- [ ] **Step 7: Record everything** in `2026-06-28-mut-id-targeting-and-coverage` (tables: tier shares, MV delta, spot-check rates, cost delta, topology distribution) and check this task's boxes.

---

### Task 12: Docs finalization

**Files:**
- Modify: `docs/plans/2026-06-28-mut-id-targeting-and-coverage.md` (done in Task 11)
- Modify: `docs/plans/INDEX.md` (regenerated)

- [ ] **Step 1:** Verify spec acceptance criteria (`2026-07-02-mut-id-confidence-fusion` §Acceptance) each map to a completed task; tick remaining checkboxes here.
- [ ] **Step 2:** Set this plan's `status: implemented` is **not** done by hand — run `omp-plans complete 2026-07-02-static-mut-id-fusion` (moves to archive, regenerates INDEX, validates).
- [ ] **Step 3:** Commit any doc deltas: `git commit -am "docs(plans): record MUT-id fusion spike results"`.

---

## Self-review

- **Spec coverage:** fusion tiers T1 (Tasks 3-6, 8), T2/T3 promotion (Tasks 3, 7), T4 ranking (Task 8), T5 (Task 3); grades + observation schema (Tasks 1, 9); focal path+name (Task 7); shallow flag (Task 6); input topology (`actual_shape`/`receiver_provenance`, Task 8b, per `2026-07-02-input-topology-spike`); tier funnel + invariant-3 tooling (Task 10); acceptance evidence + R1/R2 decision gate (Task 11). Oracle tiers/`oracle_agreement` are spec-deferred (PIT_ORIGINAL) — reserved column only, by design.
- **Type consistency:** `MutResolution` enum names in Task 2 == mapper strings in Task 9 == DDL comments in Task 1 == funnel SQL literals in Task 10 (`T4_GUESS`, `RANKED_GUESS`, `NO_VISIBLE_CALL` checked).
- **No placeholders** except two deliberate verbatim-copy instructions (`getExecutedBody`, `db_config` acquisition) that point at exact sources — copying stale code into the plan would rot faster than pointing at it.
- **Known judgment calls for the executor:** Spoon's model for `Integer.parseInt` declaring-type resolution without jars (Task 3 Step 4 names the invariant to hold); `db_config` helper name (Task 10 Step 1 says verify first).
