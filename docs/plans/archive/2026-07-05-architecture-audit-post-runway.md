---
title: Architecture Audit — Post-Runway, Pre-Rerun
type: audit
status: implemented
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
superseded_by:
archived: 2026-07-05
---

# Architecture Audit — Post-Runway, Pre-Rerun

**One concern:** a coordinator-run whole-structure audit after the census-lever, telemetry,
parse-predicate, and inherited-tests waves, before the full evaluation rerun. Scale at audit
time: 18,967 production lines in 162 files. Supersedes
`2026-07-04-architecture-implementation-review` — that review's Tier A (task split, recipe
unification, sentinel-int removal, resolver extraction) and most of Tier B (CtThisAccess,
TypeFilter sweep, license package move, `CapturedInvocation` rename) shipped; this audit
records what the subsequent waves added and what they bent.

## Sound — keep

- **Package DAG is real and near-clean.** `domain` has zero teralizer imports; `transformer`,
  `jpf`, `jqwik`, `generalization` point downward; `processing` sits on top. Two defects
  named below; everything else holds the documented layering.
- **The diagnostics package** (`processing.diagnostics`) came out uniform: final classes,
  static writers invoked where the fact is known (`ProcessingPipeline` for failures, each
  stage for its success flag), stable-code constants beside each writer, classifier logic
  unit-tested. The lifecycle writer's success/failure monotonicity guards
  (`successCanAffect`/`failureCanAffect`) are the right amount of defensiveness.
- **Filter layer**: one canonical `FilterResult` constructor with telescoping conveniences,
  every REJECT/DEFER carrying a stable code, behavioral tests per filter. No builder needed.
- **`WideningLicense`** stayed a pure six-argument function with one production callsite and
  a javadoc that carries the full soundness argument. Growth by parameter, not by branching
  sprawl.
- **jpf-symbc additions are minimal and documented**: `CharPredicateHandler` is a final,
  package-private, single-purpose class with the interval-soundness argument in a comment;
  the `INVOKESTATIC` hook is one guarded delegation line. The fork's diff surface stays small.
- **`InheritedTestMethodScreens`** is a pure predicate evaluated at both collection and
  clone-flatten time — the double evaluation is cheap defense in depth, not duplication.
- **Snippet rule status**: the remaining `createCodeSnippetStatement` sites are the
  planner-boundary seam (planners emit textual Java by design; the factory wraps at the
  boundary) and two leaf recorder calls that die with the queued harness-support-artifact
  spec. No structured-code-by-concatenation remains outside that boundary.

## Findings (ranked; F1–F6 fixed in this audit's batch)

- **F1 · `SQLiteRepository` is a lie.** The class holds the pipeline's shared jOOQ queries
  against PostgreSQL; nothing SQLite exists anywhere. Eight tasks import it. Renamed to
  `PipelineQueries` (package `repository` retained).
- **F2 · Filter layer imports the task layer.** `AssertionInMethodFilter` →
  `TestAnalysisTask.resolveTestMethod`. The resolver is pure Spoon+record logic (declaring
  class walk + CtPath evaluation) that three call sites share. Moved to
  `teralizer.spoon.analysis.TestMethodResolver`; the filter now imports analysis only, and
  the tasks call the same helper.
- **F3 · `transformer` ↔ `jqwik.planning` import cycle.** The transformers consult
  `MethodCapabilities`/`MethodCapability` (admission + rendering vocabulary); the planners
  consult `ModelToJavaTransformer`. The capability types are pure vocabulary over
  `TypeDomain` — they belong beside the Model. Moved to `teralizer.domain`; the cycle is
  gone (`transformer` → `domain`; `jqwik` → `domain`, `transformer`).
- **F4 · Codegen detects parse predicates by magic string.** `GeneralizedTestBuilder`
  matched `"ParsePredicates."` while `MethodCapabilities.PARSE_PREDICATES_QUALIFIER` already
  names it. Now references the constant, so ingestion, rendering, and emission cannot drift
  apart silently.
- **F5 · Generation-coverage telemetry braided into `TestGeneralizationTask`** (~90 lines of
  writer + shape/representation helpers inside the orchestrator — the "tasks are three
  programs" smell the previous review named). Extracted to
  `processing.diagnostics.GenerationCoverageWriter`, matching the package's writer pattern.
- **F6 · jqwik-diagnostics import braided into `JunitDataCollectionTask`** (~90 lines of
  sidecar parsing + row building inside the surefire collector, plus the NUL-strip and
  sidecar-path helpers). Extracted to `processing.diagnostics.JqwikDiagnosticsImporter`.
  The task returns to report discovery + record building + scheduling.

## Recorded, not acted on

- **Listener split candidate.** `TestGeneralizationListener` (531) now carries capture,
  extraction guards, and concretization/divergence telemetry. The telemetry block
  (~90 lines) is cohesive and could become a second JPF listener
  (`ConcretizationObserver`), making it independently testable — but it shares the
  extraction-active window (targetDepth pinning) with capture, and the listener's behavior
  is pinned by a dozen harness tests. Medium effort, medium risk, zero behavior gain: do it
  only if the telemetry grows again.
- **`JunitDataCollectionTask` residual size** (~480 after F6): report discovery, surefire
  parsing, record building with inherited-method resolution, scheduling. Cohesive enough;
  the next split (report-path resolution + parsing into a `SurefireReports` helper) becomes
  worthwhile only if report-format work resumes.
- **`util` → `processing` upward imports** (`Configuration` → `GeneralizationAlgorithm`,
  `ConsoleCommand` → `ProcessingStage` for file naming). Mild; breaking them costs churn
  with no comprehension gain at this scale.
- **`ParsePredicatesFactory` uses an inline source string** where its two siblings use
  Velocity templates. The class is constant (no substitutions), so inline is defensible;
  the whole support-class emission area is queued to change with
  `2026-07-03-harness-support-artifact`. Leave it.
- **Open items inherited from the superseded review**: `sameField` simple-name comparison
  (latent, guarded), `rewriteCtPathForClone` literal string replace (improbable corruption),
  vararg expected-type approximation, resolver static caches (`TYPE_INDEXES`, `FOCAL_CACHE`)
  belonging in `TaskContext`. All small, none load-bearing for the rerun; fold into the next
  hygiene batch that touches the resolver.

## Verification for the F1–F6 batch

Behavior-neutral (renames, moves, one constant reference, two extractions with identical
logic): targeted unit tests per touched seam plus one full `./gradlew build`. No fixture gate
— no golden-bearing behavior changes; the rerun is the next full-corpus event regardless.

## Outcome

All six findings landed as atomic commits, each compiling standalone, followed by the
architecture.md correction:

- F1 `b2b03b08` — rename `SQLiteRepository` → `PipelineQueries` (8 callers).
- F2 `951b6bd9` — `TestMethodResolver` in `spoon.analysis`; filter → task import removed.
- F3 `d2a28633` — `MethodCapability`/`MethodCapabilities` to `domain`; the
  `transformer` ↔ `jqwik.planning` cycle is gone.
- F4 `bdd67d2f` — `GeneralizedTestBuilder` detects parse predicates via
  `MethodCapabilities.PARSE_PREDICATES_QUALIFIER` instead of a magic string.
- F5 `6051dee7` — `GenerationCoverageWriter` extracted to `processing.diagnostics`.
- F6 `e55d87b8` — `JqwikDiagnosticsImporter` extracted to `processing.diagnostics`.
- Docs `7b6239e5` — architecture.md: real widening-license rule (divergence-risk gate, not
  zero-events), package responsibilities incl. `processing.diagnostics`/`repository`/`domain`,
  new cross-stage contracts (extraction telemetry, sound SPF models, inherited test methods).

Gate: full `./gradlew build` green after the batch. Package DAG has no remaining cycles among
the audited seams. The "Recorded, not acted on" list above is the seed for the next hygiene
batch.
