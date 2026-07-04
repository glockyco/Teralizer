# Architecture

## Processing Pipeline

The system follows a multi-stage pipeline architecture defined in `src/main/java/teralizer/processing/ProcessingStage.java`:

### Core Processing Stages (ordered sequence):
1. **Project Setup** (stages 0-2): Download, cleanup, and setup target project
2. **Build & Dependencies** (stages 3-4): Add required dependencies and build original project
3. **Test Generation** (stages 5-6): Optional EvoSuite test generation and postprocessing
4. **Analysis Preparation** (stages 7-12): Build Spoon model, execute original tests, collect reports (JUnit, JaCoCo, PIT), filter tests
5. **Test Analysis** (stages 13-15): Analyze and filter tests and assertions for generalization suitability
6. **Constraint Collection** (stages 16-20): SPF instrumentation, build instrumented project, execute SPF constraint collection, analyze results, cleanup
7. **Initial Testing** (stages 21-25): Build and test project before generalization, collect baseline metrics
8. **Test Generalization** (stages 26-33): Generate property-based tests, build generalized project, execute and collect final metrics

### Package responsibilities

- `processing` / `processing.task`: stage orchestration — DB records, scheduling, file I/O.
  Tasks orchestrate; they should not own transformation logic (see §Code generation).
- `processing.filter`: typed per-test/per-assertion gates (`FilterResult`, ACCEPT/REJECT/DEFER).
- `spoon.analysis`: MUT resolution, recipes, structural screens — reads test ASTs, never writes.
- `spoon.generalization`: jqwik supplier/parameter codegen + the widening-license policy.
- `jpf`: the SPF listener, capture records, extraction outcomes — everything that runs inside JPF.
- `transformer`: total mappings SPF ↔ Model ↔ JSON ↔ Java; unsupported terms throw typed.
- `jqwik` / `jqwik.planning`: clause interpretation and per-parameter generation plans.
- `domain`: the Model expression tree and value records; no dependencies on any other package.

### Key components

- **ProcessingPipeline**: Orchestrates task execution in dependency order
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
- **Widening license** (`WideningLicense`, consulted in `TestGeneralizationTask`): an input is
  widened beyond its concrete seed only as far as extraction evidence licenses. SYMBOLIC and
  CONSTANT output models widen. EXCEPTION and boolean-in-PC cases widen only with zero
  concretization events and a path condition that is empty (EXCEPTION) or names every widened
  parameter. Everything else becomes the typed exclusion `ORACLE_NOT_WIDENABLE`. Design
  authority: `docs/plans/archive/2026-07-03-widening-license.md`.
- **Extraction telemetry** (written at SPF analysis time, consumed by the license and by
  analysis): `assertion.output_spec_class` (SYMBOLIC | CONSTANT | NULL_CONCRETE | EXCEPTION)
  and `assertion.concretization_events` (symbolic values entering unmodeled native methods).
  Boxed-primitive returns are captured from the box's `value` field attr. The vendored fork
  preserves it for `Integer.valueOf` and explicit constructors but loses it on
  `Long/Boolean.valueOf`, where extraction degrades to NULL_CONCRETE, never to unsoundness.
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