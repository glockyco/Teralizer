---
title: Architecture & Implementation Review — Post-R1
type: audit
status: active
created: 2026-07-04
parent: 2026-06-26-teralizer-overview
---

# Architecture & Implementation Review — Post-R1

**One concern:** a whole-codebase read (every file >250 lines in full or structural depth) after the R1 wave, recording what is sound, what makes the Spoon-heavy code hard to understand, and the tiered improvement plan. Complements `2026-06-28-pipeline-architecture-review` (model/solver/generator subsystems) — this review covers structure, comprehension, and codegen hygiene. Scale at review time: 16,771 production lines in 146 files, 9,854 test lines in 103 files.

## Sound — keep, do not re-architect

- **DB-mediated stage pipeline with per-assertion tasks**: resumable, observable, feeds every funnel query. The right amount of architecture for a research tool. No DI framework, no module system.
- **Domain Model** (`Expression` tree with a compile-strict `ModelFolder` and a partial-by-intent `ModelVisitor`): total, small, a missing node kind is a build break.
- **Recipe seam** (single derivation, persisted JSON, resolve-on-write): killed the 4× drift surface and made R1 a payload change.
- **Typed outcomes** end to end (ExtractionOutcome, license verdicts, FilterResult, typed exclusions).
- **Verification pyramid**: caught the license expression-type bug before a golden pinned it.
- **Resolver prose**: heuristics are the domain, and the tier/signal/corroborator vocabulary is the abstraction. Do not force it into a strategy-pattern zoo.
- `NumericClauseInterpreter`'s two-sweep order (census byte-stability constraint, self-documented).

## Root causes of the comprehension problem

1. **Two code representations interleaved.** Statement-level string snippets built by concatenating pretty-printed AST (`JpfInstrumentationTask` invocation-path wrapper body) beside real node construction (the R1 expression path does it right). Snippets bypass the model: no reference resolution, no typing, invisible to later passes.
2. **Tasks are three programs per class.** `JpfInstrumentationTask` (797) = scheduler + record/path bookkeeper + Spoon codegen + file writer + Velocity config generator. `TestGeneralizationTask` (610) = record creation + clone + annotation rewriting + recipe resolution + license gate + codegen assembly + printing + copying. Codegen is only fixture-testable because it is braided into orchestration and I/O.
3. **The R1 dual path.** `expressionRecipe ? … : …` branch pairs in both big tasks: two wrapper builders, two call-site builders, two site-rewrite blocks. The invocation recipe is a degenerate expression recipe (the oracle expression is the call, and the sites are its argument positions).
4. **Sentinel-int encodings.** `GeneralizableInput` encodes a three-way union in `methodArgumentIndex ∈ {-2, -1, ≥0}` while `GeneralizationRecipe.InputKind` already names the same kinds properly.

## Defect/debt inventory

Correctness-adjacent (small, real):
- `MethodUnderTestResolver.isThisOrUnqualified`: `"this".equals(target.toString())` is AST comparison by pretty-print. It should be `instanceof CtThisAccess`.
- `MethodUnderTestResolver.sameField` compares simple names only, so hierarchy shadowing collides (guarded by the this-receiver check, latent).
- `GeneralizationRecipe.rewriteCtPathForClone`: literal `String.replace` on CtPaths — prefix-substring class names would corrupt (improbable, structurally fragile).
- `JpfInstrumentationTask.inferExpectedType` vararg handling approximates with the last declared parameter type.

Design smells:
- Static mutable caches in the resolver (`TYPE_INDEXES`, `FOCAL_CACHE`, synchronized WeakHashMaps) — belongs in per-project `TaskContext`.
- `teralizer.jpf.Invocation` vs `teralizer.domain.Invocation` name collision.
- `WideningLicense` in `teralizer.spoon.generalization` imports zero Spoon — pure policy in the wrong package.
- `getElements(X.class::isInstance)` + manual cast everywhere; Spoon's `TypeFilter` returns typed lists.
- `TestAnalysis` assertion-index if/else pyramids = a lookup table written as control flow; `TestGeneralizationTask` annotation rewriting = four copy-pasted blocks.
- Recorder-as-Java injected via Velocity into Spoon classes — killed by the queued harness-support-artifact spec.

Comprehension gap no refactor fixes: no document showed what the generated artifacts look like → `docs/artifacts.md` (this wave).

## Improvement plan

**Tier A — structural (spec → plan → fixture-gated tasks, in sequence A3 → A1 → A2 → A4):**
- A1 Unify on expression recipes: the invocation recipe becomes an expression recipe with argument-position sites. Deletes both index-keyed rewrite blocks, the second wrapper builder, the `(CtInvocation)` casts, and the dual sentinel semantics. `_target_`/lifted-locals generalize to site kinds. Effort M–L.
- A2 Split mega-tasks into Task (orchestrate) + Builder (pure codegen: recipe in, `CtClass` out, no DB/IO). Halves both files, and codegen becomes unit-testable. Effort M.
- A3 Replace sentinel ints with a shared `InputKind` (+ `EXPRESSION_SITE`). Effort S–M.
- A4 Extract `FocalTypeResolver` + `InputTopologyClassifier` from the resolver, with the caches moving into `TaskContext`. The resolver shrinks to roughly 800 lines of pure resolution. Effort S–M.

A1+A2 subsume most of the old review's C-1 (single emitter). After them C-1 shrinks to a leftover check.

**Tier B — hygiene (one batch):** the snippet rule (snippets only for leaf identifier references, structured code built as nodes — fix the invocation-path wrapper body), rename `jpf.Invocation` → `CapturedInvocation`, the `TypeFilter` sweep, the `isThisOrUnqualified`/`sameField` fixes, moving `WideningLicense` to a policy package, table-ifying `TestAnalysis` indices and the annotation rewrite, and deleting decided TODOs.

**Tier C — comprehension docs:** the `docs/artifacts.md` artifact gallery (observed artifacts, annotated), the architecture.md codegen-technology section with the snippet rule, and complete per-package responsibilities.

## Verification economy (agreed with the operator)

Refactor waves do not re-run the full pyramid per step. Docs: `omp-plans check` only. Tier B: targeted unit tests per change, one full `./gradlew build`, ONE `scripts/verify-pipeline.sh` run for the whole batch (no determinism double-run — no generator/engine seam is touched). Tier A: fixture corpus during development, the determinism double-run and one sentinel comparison ONCE at wave end. First-run numbers stand.

## Explicitly rejected

- Rewriting resolver heuristics into pattern abstractions (hides the ranking order the paper must describe).
- Generalizing the planner registry beyond `DomainPlanner`.
- Any pipeline re-architecture (microservices, DI, module systems).
