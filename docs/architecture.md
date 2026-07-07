# Architecture

## Processing Pipeline

The pipeline is defined in `src/main/java/teralizer/processing/ProcessingStage.java` and
driven by `PipelinePlanner`, which runs three **independently-toggled phases** in canonical
order — generation, generalization, reduction (`project.use_test_generation` /
`use_test_generalization` / `use_test_reduction`). Each phase clears its own prior state,
checks artifact-based preconditions (fail loud on a missing input), schedules its stages, and
drains the queue before the next phase begins. Draining between phases is load-bearing: a
reduction (Stage 5) failure can never drop generalization results, and reduction can run in a
separate later invocation over the persisted on-disk workspace (attach by `root_path`, guarded
by a config hash).

### Stages by phase (current `ProcessingStage` order)
1. **Bootstrap** (0–4): cleanup, download, setup, add dependencies, build original project. Runs once per invocation; `CLEANUP_PROJECT` fires only on a fresh start, never on attach.
2. **Generation** (5–6, optional): EvoSuite generation + postprocessing.
3. **Generalization** (7–28): Spoon model; original execute / JUnit / JaCoCo / filter; test and assertion analysis and filtering; SPF instrumentation → build → execute → analyze → cleanup; initial build / execute / JUnit; then per variant cleanup → generalize → build → execute → JUnit → filter. `EXECUTE_TESTS_GENERALIZED` archives each variant's generated sources under the data dir.
4. **Reduction** (29–34, Stage 5 measurement): PIT-original, INITIAL JaCoCo + PIT, then per variant `RESTORE_GENERALIZED_BUILD` → generalized JaCoCo + PIT. Deferred behind the whole generalization loop; the restore step rebuilds each variant from its archived sources so its mutation run is isolated from siblings.

### Package responsibilities

- `processing` / `processing.task`: stage orchestration — DB records, scheduling, file I/O.
  Tasks orchestrate. They should not own transformation logic (see §Code generation) or
  telemetry persistence (see `processing.diagnostics`).
- `processing.filter`: typed per-test/per-assertion gates (`FilterResult`, ACCEPT/REJECT/DEFER;
  every REJECT/DEFER carries a stable `reason_code` from `FilterReasonCodes`).
- `processing.diagnostics`: telemetry writers and classifiers — task diagnostics, assertion
  semantics, build-environment observations, generalization lifecycle, generation coverage,
  jqwik outcome import. Written where the fact is known, with stable-code constants beside
  each writer.
- `spoon.analysis`: MUT resolution, recipes, structural screens, test-method resolution —
  reads test ASTs, never writes.
- `spoon.generalization` / `spoon.codegen`: jqwik supplier/parameter codegen and generated
  test assembly (support classes: first-value arbitrary, value recorder, parse predicates).
- `generalization`: pure widening policy (`WideningLicense`) — no Spoon, no DB.
- `jpf`: the SPF listener, capture records, extraction outcomes — everything that runs inside
  JPF, including the concretization and divergence-risk observations.
- `transformer`: total mappings SPF ↔ Model ↔ JSON ↔ Java. Unsupported terms throw typed.
- `jqwik` / `jqwik.planning`: clause interpretation and per-parameter generation plans.
- `domain`: the Model expression tree, value records, and the method-capability vocabulary
  (`MethodCapabilities`) — no dependencies on any other package.
- `repository`: the pipeline's shared jOOQ queries (`PipelineQueries`).

### Key components

- **PipelinePlanner** (`processing`): runs the requested phases in canonical order — per phase, clear prior state, check preconditions, schedule, drain. Resolves attach-or-fresh project identity via **ProjectIdentity** (root-path + config-hash guard).
- **PipelinePhase** (`processing`): the three phases (`GENERATION`, `GENERALIZATION`, `REDUCTION`), each owning its stage set, artifact preconditions, teardown, and success predicate.
- **ProcessingPipeline**: executes queued tasks in priority order; a task failure drops only same-variant queued work (shared/variant-null failures cascade to the project).
- **TaskContext**: Shared state containing database connections, configuration, and utilities
- **MethodUnderTestResolver**: confidence-ranked MUT identification (tiers T1–T5, deciding
  signals, ranked alternatives). It is a total function: every assertion gets a graded
  resolution, persisted as provenance in `mut_resolution_observation`. Only
  generalization-grade picks populate the `tested_*` columns.

## Code generation

Three technologies, each with one job (worked examples of every artifact: `docs/artifacts.md`):

- **Spoon AST construction** — all generated Java that later passes see or that must resolve
  references: instrumented classes, generalized test classes, supplier/parameter classes.
- **Velocity templates** — genuinely textual artifacts: the JPF config (`jpf-config.vm`), the
  driver class (`driver-class.vm`), the value-recorder harness. The harness-as-Java case is
  scheduled to move into a precompiled support jar (harness-support-artifact spec).
- **Code snippets** (`createCodeSnippetExpression`/`...Statement`) — leaf identifier references
  ONLY (`_p_.x`, `site0`, a lifted-local name). A snippet is invisible to the Spoon model: no
  reference resolution, no typing, no later AST pass sees inside it. Building statements or
  structured expressions by string concatenation is PROHIBITED; construct nodes instead.

## Cross-stage contracts

Facts that span multiple pipeline stages and are load-bearing for any change:

- **GeneralizationRecipe** (`teralizer.spoon.analysis`): the oracle-expression path, input
  sites, and oracle type are derived ONCE in `TestAnalysisTask` (post-resolver), persisted as
  JSON in `assertion.generalization_recipe`, and consumed by `JpfInstrumentationTask` and
  `TestGeneralizationTask` through one shared resolver with typed failures. Never re-derive
  recipe facts in a consumer. Extend the recipe instead.
- **Widening license** (`teralizer.generalization.WideningLicense`, consulted in
  `TestGeneralizationTask`): an input is widened beyond its concrete seed only as far as
  extraction evidence licenses. SYMBOLIC and CONSTANT output models widen. EXCEPTION widens
  when no concretization event occurred OR the per-assertion
  `post_concretization_divergence_risk` flag is false (no concrete application-code branch
  after the first event and an application-origin throw), in both cases combined with the
  path-name coverage condition (empty path condition, or every widened parameter named).
  Boolean-in-PC (NULL_CONCRETE boolean return) widens only with zero events and full
  path-name coverage. Everything else becomes the typed exclusion `ORACLE_NOT_WIDENABLE`.
  Design authorities: `docs/plans/archive/2026-07-03-widening-license.md`,
  `docs/plans/archive/2026-07-05-exception-message-widening.md`.
- **Extraction telemetry** (written at SPF analysis time, consumed by the license and by
  analysis): `assertion.output_spec_class` (SYMBOLIC | CONSTANT | NULL_CONCRETE | EXCEPTION),
  `assertion.concretization_events` (symbolic values entering unmodeled native methods — a
  boundary marker, not a loss marker: box→unbox round trips preserve attrs),
  `assertion.concretized_methods` (per-method counts), and
  `assertion.post_concretization_divergence_risk`. Boxed-primitive returns are captured from
  the box's `value` field attr. Degradation remains a typed NULL_CONCRETE refusal, never
  unsoundness.
- **Sound SPF models in the fork** fire no concretization events because interception happens
  before native-peer dispatch: `String.isEmpty` (string equality), ASCII
  `Character.isWhitespace` (interval pinning in constraint collection,
  `CharPredicateHandler`), and the parseability comparators
  (ISINTEGER/ISLONG/ISFLOAT/ISDOUBLE, ingested as static `ParsePredicates` invocations and
  rendered against a helper class emitted into the generated test).
- **Inherited test methods**: collection resolves the declaring class via the superclass
  chain and stores it in the method columns (the class column keeps the JUnit-reported
  child). Two screens (type variables, private-member accessibility) gate flattening into
  generated clones (`SpoonUtils.cloneClass`), the generated-class writers merge the declaring
  unit's imports, and unflattenable methods become the typed test-level exclusion
  `INHERITED_METHOD_NOT_FLATTENABLE`.
- **Ingestion totality** (`SpfToModelTransformer`): every SPF term entering the model maps
  faithfully or is refused with a typed `UnsupportedSpfTermException` (surfacing as an
  `UNSUPPORTED_TERM` exclusion at the JPF task boundary). String-derived integer symbols
  (`SymbolicLengthInteger`, the `SymbolicIndexOf*`/`SymbolicCharAt*` family) carry a parent
  string expression; dropping that tie yields a free integer variable that corrupts temporary
  recovery and the license's path-condition evidence. `length` maps to a `length()` invocation
  on its parent; the rest of the family is refused.
- **Build-file copies**: the pipeline never mutates a target project's own build file. It works
  on `pom.teralizer.xml` / `build.teralizer.gradle` (copied at setup, mutated by the dependency
  managers: dependency injection, jacoco/pitest plugins, test-source floor) and derives
  `pom.teralizer.generalized.xml` (surefire floored to a JUnit-platform-capable version) which
  ONLY `EXECUTE_TESTS_GENERALIZED` uses — original suites run each project's native surefire.
- **Generalization variants** are defined exclusively in profile configs
  (`project-configs/*.conf`, block `teralizer.generalizations`). `reference.conf` defines none.
  Algorithms: BASELINE (seed replay), NAIVE (unconstrained widening + residual filter),
  IMPROVED (clause-encoding planners + residual filter). Generated properties run with a fixed
  jqwik seed, shrinking off, edge-cases-first, and a first-value wrapper that executes the
  captured seed tuple first and dedups random draws (`first-value-arbitrary.vm`).
- **Input generation** (`teralizer.jqwik.planning`): a type is generatable iff a registered
  `DomainPlanner` supports it. Planners encode path-condition clauses by construction and the
  unconditional residual filter enforces the rest. Design authority:
  `docs/plans/2026-06-28-clause-driven-input-generation.md`.

## Verification

Synthetic full-pipeline fixtures under `verification/fixtures/` with observed goldens pin every
behavior family above. See AGENTS.md §Verification tiers and the repo skill
`verifying-pipeline-changes` for the workflow.

## Key Technical Distinction

While the tool uses Symbolic Pathfinder (SPF), it does NOT perform traditional symbolic execution. Instead, it uses SPF in constraint collection mode to extract constraints from concrete test executions, avoiding the computational expense of full symbolic execution while still capturing input-output relationships.

## Dependencies

### Key Dependencies
- **Symbolic Pathfinder (SPF)**: Custom build in `jpf-symbc/` for constraint collection
- **Spoon**: Java source code analysis and AST manipulation
- **JQwik**: Property-based testing framework for generated tests
- **JOOQ**: Type-safe database queries with PostgreSQL
- **Gradle Tooling API**: Build system integration for target projects
- **EvoSuite**: Automated test generation for projects lacking existing tests
- **PIT**: Mutation testing for effectiveness evaluation
- **JaCoCo**: Code coverage analysis
- **Maven/Gradle**: Target project build system support