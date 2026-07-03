---
title: Recipe Seam & Downstream Effects of MUT-id Fusion
type: audit
status: active
created: 2026-07-02
parent: 2026-06-26-teralizer-overview
---

# Recipe Seam Review — how MUT-id output flows downstream

How the pipeline stages after MUT identification (JPF instrumentation → SPF execution/spec
extraction → generalized test creation → jqwik gate) consume the resolver's output; what the
fusion changes (`2026-07-02-mut-id-confidence-fusion`) do and do not touch there; and the
refactorings, spikes, and telemetry that follow. Complements
`2026-06-28-pipeline-architecture-review` (model/solver/generator subsystems — its open items
D-1, C-1/C-6, B-3/B-5, D-5 stand unchanged); evidence here is the *seam between* MUT-id and
those subsystems. All facts verified at source 2026-07-02.

## How the seam works today (verified)

1. **MUT-id's entire output contract is a set of string columns** (`tested_*` paths + names).
   Three downstream tasks independently re-resolve them and **re-derive the recipe four times**:
   `GeneralizableInput.derive(testedMethod, testedMethodCall)` at `TestAnalysisTask.java:119`,
   `JpfInstrumentationTask.java:195` and `:352`, `TestGeneralizationTask.java:480`. Each caller
   first re-materializes the Spoon nodes from persisted CtPaths, rewriting class names by string
   surgery — `getTestedMethodCallRelativePath().replace(testClassQName, generalizedClassQName)`
   (`TestGeneralizationTask.java:467-477`, `JpfInstrumentationTask.java:182-186`). Four
   derivations must agree for the pipeline to be coherent; nothing checks that they do.
2. **Instrumentation is already a synthetic-wrapper recipe.** `createInstrumentedMethod`
   (`JpfInstrumentationTask.java:189-287`) clones the test class, creates a wrapper method whose
   parameters are the generalizable inputs (receiver-ctor args included), rewrites the tested
   call's argument/receiver positions to reference those parameters, and emits
   `return <rewrittenCall>` as the body. `symbolic.method` marks the **wrapper's** parameters
   symbolic (`JpfInstrumentationTask.java:441-442`); a Velocity driver (`driver-class.vm:13-24`)
   constructs the instrumented class, **runs `@Before` methods**, and invokes the test method —
   constraint collection follows the concrete path (`symbolic.collect_constraints=true`,
   `jpf-config.vm:23`).
3. **Output capture is pinned to the tested method's return**, not the wrapper's:
   `TestGeneralizationListener.methodExited` captures the SPF return attr at the tested-method
   frame exit and terminates the search (`TestGeneralizationListener.java:122-133,166-170`).
   A value that flows through an unmodeled library call (no `List`/collection peers exist in the
   vendored jpf-symbc) loses its symbolic attr → `spfOutput = null` → **null output model**,
   serialized as JSON `null` (`SpfToModelTransformer.java:52-55`, `SpecificationExtractor.java:31-38`).
   `output_model_statistics` cannot distinguish null/constant/symbolic (`operationCount=0` for
   all three — `ModelStatisticsExtractor.java:16-48`). **Nothing records output-spec degeneracy.**
4. **The expected side is invisible at SPF time** — the listener terminates before the assertion
   executes; no expected-vs-actual comparison happens. But the **concrete** output value *is*
   captured (`output_value_path`, `CapturedOutput.ofReturnValue`,
   `TestGeneralizationListener.java:158-163`), and the assertion's expected argument is stored
   statically (`assertion_arguments`).
5. **A null/constant output spec is not vacuous downstream**: `TestGeneralizationTask.java:367`
   replaces the expected side only when `outputJava != null`; otherwise the original expected
   literal stays, so the property asserts `actual(_p_) == <original constant>` over the input
   predicate — sound only if the method is genuinely constant there; otherwise it fails on a
   non-seed trial and `NonPassingTestFilter` excludes it (checks `junit_test_report` only,
   `NonPassingTestFilter.java:31-59`).
6. **Seed-kill attribution is already measurable**: generalized runs execute in `PERSISTED`
   diagnostics mode (`TestExecutionTask.java:72-89`); `jqwik_property_execution.tries = 1` with
   `diagnostic_kind = 'ASSERTION_FAILED'` is the aggregate signature of a first-trial (seed)
   failure, because `FirstValueArbitrary` emits the captured original tuple first
   (`first-value-arbitrary.vm:14-28`, import at `JunitDataCollectionTask.java:283-295`).
   PIT/`IN_MEMORY_ONLY` runs write no rows — the signal exists only for the jqwik gate.
7. **Per-assertion JPF wall-clock is already persisted** as `task.runtime` on the assertion-level
   `EXECUTE_JPF` task row (`ProcessingPipeline.java:82-118`); the SPF listener enforces
   `teralizer.jpf.max-execution-time` (default 10 s, `reference.conf:14`).

## Effect of the fusion changes on downstream stages

**Mechanically: none.** Fusion v1 keeps the exact `CtInvocation`/`tested_*` contract; grade
separation guarantees no null-declaration pick reaches `JpfInstrumentationTask` (the CtPath NPE
documented in `2026-06-27-ensemble-mut-identification`). Instrumentation, SPF, spec extraction,
and generation are untouched.

**Economically: more load, with two known wrong-pick failure shapes**, both bounded, both
currently paid at the most expensive point:

- *Incoherent pick, spec extracted fine* — the wrapper returns a value unrelated to the asserted
  one; the property fails at the seed trial → excluded after paying SPF + codegen +
  `BUILD_PROJECT_GENERALIZED` + a jqwik run.
- *Pick's output not symbolic in its inputs* — null/constant spec (finding 3/5); property is the
  original constant over varied inputs → fails on an early random trial unless genuinely
  constant → same full-cycle cost.

T4 ranked guesses raise the frequency of both. The fusion plan's Task 11 measures the cost;
finding 6 gives the incoherence *rate* for free (`tries = 1` share of newly-attempted
generalizations).

**For the recipe increments (R1/R2 in `2026-07-02-input-topology-spike`): the mechanism is
confirmed, the risk is sharpened.** R1 (expression-slice) is structurally: wrapper body returns
the *asserted expression* with sites lifted (the exact machinery of finding 2), plus capture at
the **wrapper** exit instead of the tested-method exit (one listener/config change), plus
`ReturnTypeFilter` gating on the expression type. But finding 3 bounds its value: a chain ending
in an unmodeled library inspector (`*.size()`) keeps a symbolic output only when the value is
**arithmetic dataflow** from the inputs; control-flow-mediated values (collection sizes, counts)
concretize → null spec → constant-expected property. The R1 opportunity numbers from the topology
spike are therefore upper bounds on *attempts*, not on *symbolic specs* — hence the R1 viability
spike below. For R2 (statement-slice): SPF-side extraction is plausible today (the driver already
runs fixtures; symbolic attrs flow mutator-args → fields → inspector for pure dataflow); the wall
is recipe *re-execution* on the jqwik side, as the topology spike concluded.

## Recommendations

### R-A · First-class `GeneralizationRecipe` (the structural fix) — do before R1
One value object — oracle-expression path, input sites `[(path, name, type,
kind=METHOD_ARG|CTOR_ARG|RECEIVER_CTOR_ARG)]`, oracle type — derived **once** in
`TestAnalysisTask` (post-resolver), persisted as JSON, consumed by `JpfInstrumentationTask` and
`TestGeneralizationTask`. `GeneralizableInput.derive` becomes the recipe builder with exactly one
production callsite; CtPath resolution + class-name rewriting centralize in one validated place
(resolve-on-write: fail at analysis time, not three stages later). Kills the 4× drift surface
(finding 1) and turns R1 into a recipe-payload change instead of a three-task rewrite. Effort M.
Behavior-preserving refactor → verifiable by the census no-regression check. Sequence: after
fusion v1 lands (its Task 9 keeps the current contract), before any R1 work.

### R-B · Early coherence gate (post-JPF, pre-build) — rejected by its own gate
The measured seed-kill share among validated generalizations is 2.7% (23/850, definitive
single-variant spike) — below the threshold where a pre-build check pays for itself. The
dominant incoherence class is not seed-failure but post-seed widened failure, and that class
is prevented at its source by the widening license (`2026-07-03-widening-license`), which
subsumes R-B's purpose at generation time instead of JPF-analysis time. Revisit only if a
future corpus shows a materially higher seed-kill share among newly-attempted picks.

### R-C · `output_spec_class` telemetry — shipped
`SYMBOLIC | CONSTANT | NULL_CONCRETE | EXCEPTION`, computed where the spec is written
(`SpecificationExtractor`/`JpfAnalysisTask`) — trivially derivable from the output model
(null / lone `Constant` / anything else / `CapturedException`). Closes finding 3's blindness;
directly measures the architecture audit's D-1 (silent concretization); the denominator for R1
viability and the fusion-guess quality signal (incoherent picks skew to CONSTANT/NULL). Added to
`2026-07-01-pipeline-observability-telemetry`. Effort S.

### R-D · Seed-kill share in the fusion verification — done
Recorded in `2026-06-28-mut-id-targeting-and-coverage`: 23/850 (2.7%) raw; the
newly-attempted-subset figure (4/17) is too thinly matched across the baseline join to carry
weight. This is R-B's decision input; verdict above.

### Considered and rejected
- *Expected-side capture inside the SPF driver* — R-B achieves the same check statically for the
  shapes that matter; extending the listener/driver for non-literal expected sides adds JPF
  surface for marginal reach. Revisit only if R-B's literal-only coverage proves too narrow.
- *Merging the recipe into `mut_resolution_observation`* — the observation is telemetry
  (analysis-facing, nullable, additive); the recipe is a load-bearing pipeline contract. Separate
  lifecycles, separate storage.

## Spikes

1. **R1 viability spike (before writing the R1 spec).** Hand-write ~8 wrapper shapes in a toy
   project — chain ending in project-code inspector (pure dataflow), chain ending in `List.size()`
   (control-flow-mediated), operator composite over two calls, `compareTo`-comparison, ctor-only
   equality, cast-wrapped call — run the existing instrumentation+JPF harness, record
   `output_spec_class` per shape. Answers: which topology-spike buckets yield *symbolic* specs vs
   degenerate ones, i.e. R1's realized (not upper-bound) value. ~1 day; no pipeline changes
   (hand-built wrappers reuse the existing artifacts).
2. **Fusion cost/quality spike** — already the fusion plan's Task 11; R-D extends it with the
   seed-kill share at zero cost.

## Telemetry summary (what to add, where it lands)

| Signal | Where | Status |
|---|---|---|
| `output_spec_class` | spec writer → assertion-level column; rolls up into `jpf_extraction_summary` | telemetry spec updated (R-C) |
| `concretization_events` (symbolic value entered unmodeled native method) | listener-only `EXECUTENATIVE` hook → assertion-level column | telemetry spec updated; mechanics in `2026-06-28-pipeline-architecture-review` D-1 |
| Seed-kill share | derived query on `jqwik_property_execution` (`tries`, `diagnostic_kind`) | exists; wired into fusion Task 11 (R-D) |
| Per-assertion JPF wall-clock | `task.runtime` on `EXECUTE_JPF` rows | already exists (finding 7) |
| Tier / shape / provenance | `mut_resolution_observation` | fusion plan Tasks 1/8b |
| Incoherence prevention | `generalization.exclusion_info = 'ORACLE_NOT_WIDENABLE'` (widening license) | design in `2026-07-03-widening-license` |

## Sequencing

1. Fusion v1 + R-C's column + R-D's query — shipped.
2. Widening license (`2026-07-03-widening-license`) + boxed output capture
   (`2026-07-03-boxed-output-capture`) — the incoherence class R-B targeted, fixed at source.
3. R-A recipe extraction (behavior-preserving; census-verified).
4. R1 viability spike (spike 1), then the R1 expression-slice spec — written against the recipe
   seam, scoped by spike-1 results and the `actual_shape` telemetry.
5. R2 decision from `receiver_provenance` counts (topology spike's gate).
