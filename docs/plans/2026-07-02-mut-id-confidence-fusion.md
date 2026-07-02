---
title: MUT-id Confidence Fusion
type: spec
status: draft
created: 2026-07-02
parent: 2026-06-26-teralizer-overview
---

# MUT-id Confidence Fusion

Method-under-test (MUT) identification becomes an evidence-fusion ranker instead of a
proof-or-abstain engine: every assertion gets the best available focal method plus a confidence
tier and full provenance, and "abstain" shrinks from the default response to ambiguity down to the
bottom tier ("no candidate exists at all"). This spec defines the fusion model — grades, tiers,
signals, ranking, and the authoritative `mut_resolution_observation` schema. The ordered build
steps for the v1 (AST-only) slice live in `2026-07-02-static-mut-id-fusion` (plan).

## Why fusion replaces proof-or-abstain

The abstain-first policy (previously `2026-06-30-static-mut-identification`, now superseded)
assumed a wrong pick is costlier than no pick. Two facts dissolve that asymmetry:

1. **The coherence seed-check bounds the downside of a wrong pick.** The generated property
   compares an independent SPF-derived output expression (expected) against a re-invocation of the
   *picked* method with generated inputs (actual), under `@Property(seed=0, edgeCases=FIRST)`
   (`TestGeneralizationTask.java:435`), which injects the original concrete inputs first. An
   *incoherent* pick — the picked method's output is not the asserted value — fails the property
   and is excluded by `NonPassingTestFilter` (strong, not total: a coincidental seed match falls
   back to random sampling). A *coherent* pick is a sound regression test for the picked method.
   So a best-effort guess either self-excludes or yields a sound-but-possibly-shallow test — never
   an unsound one.
2. **Recall is the dominant bottleneck.** `MissingValue` is ≈ half the assertion-reject corpus;
   21,616 assertions are blocked *solely* by MUT-id (`2026-06-28-mut-id-targeting-and-coverage`).
   Every abstention that was itself wrong is a spurious exclusion — pure recall loss.

The residual risk is the **coherent-shallow mis-target** (the pick is an inspector/getter whose
value the assertion does check, so the seed-check passes, but the developer-intended method's
regressions go undetected — He et al., FSE'24, DOI 10.1145/3660785, measure LCBA at 43.38%
precision against developer intent). Fusion does not eliminate that risk; it makes it **visible
and quantified** via per-assertion confidence tiers, and reserves the killed-mutant oracle
(`2026-06-27-ensemble-mut-identification`) as the future refuter for exactly this case.

Two costs are accepted knowingly:

- **Compute:** an incoherent guess self-excludes only *after* SPF, codegen, generalized build, and
  a property run. The v1 plan measures this on the 20-project spike before corpus-wide claims.
- **Attribution:** low-tier picks yield sound tests of possibly-unintended methods. Mitigated by
  the tier-slicing invariant below.

## Two grades, one pipeline

| Grade | Condition | Pipeline consequence |
|---|---|---|
| **Generalization-grade** | a pick exists and its declaration resolves in the Spoon source model | `tested_*` columns populated; assertion proceeds to filters/JPF/generalization as today |
| **Characterization-grade** | a pick exists but its declaration does not resolve (library/JDK target, unresolved source), or no pick exists | observation row only; declaration-dependent `tested_*` columns stay null ⇒ `MissingValueFilter` still rejects (typed, not silent) |

Characterization-grade picks never generalize — they exist so applicability analysis can answer
"what *would* the focal method be, and would its types pass the filters?" (the A1/A2 questions in
`2026-07-01-rerun-observability-priorities`).

## The fusion model

Two-stage: (A) resolve the focal class, (B) identify the focal method. **Lexicographic across
tiers, ranked within a tier**: the strongest evidence kind that fires decides; weaker indicators
only disambiguate within a tier or corroborate (promoting confidence). Weighted-sum scoring is
rejected — it lets two weak signals outvote one strong one (a name coincidence plus an
LCBA-position hit must never beat a constructed dataflow link), and there is no labeled corpus to
tune weights against.

### A — Focal class (scopes class-relative indicators; never gates dataflow)

| Indicator | Role |
|---|---|
| Path matching (mirrored `src/main/java` ↔ `src/test/java`, Methods2Test) | primary, structural |
| Name matching (`FooTest`/`TestFoo`/`FooIT` → `Foo`) | primary |
| Both agree → `PATH_AND_NAME` (high); either alone → `NAME_ONLY`/`PATH_ONLY` (medium); neither → `NONE` | |

The focal class **scopes only the class-relative indicators** (focal-membership corroboration,
candidate ranking). A dataflow-proven producer needs no focal class — the proof stands regardless
of declaring type. Inherited test hierarchies (`AbstractFooTest`) resolve to `NONE` in v1 and
degrade gracefully: dataflow tiers are unaffected; only class-relative corroboration is lost.
There is **no CUT veto**: a pick whose declaring type disagrees with a confident focal class is
demoted in ranking and flagged in telemetry, never discarded. Receiver-type dominance is dropped
(it could bless a helper-heavy test's collaborator as the CUT; nothing affirms on it).

### B — Focal method: evidence tiers (descending strength)

| Tier | Evidence | v1 (AST-only) signals | Runtime signals (post-PIT_ORIGINAL) |
|---|---|---|---|
| **T1_PROVEN** | constructed dataflow link or cardinality-forced elimination | single-producer dataflow trace (variable/field/sub-expression resolution, inspector unwrap); unique production call in the pre-assertion slice | — |
| **T2_CORROBORATED** | a weak pick that ≥2 independent identity indicators agree on, or a single weak pick confirmed by one | weak candidate + name-match + focal-membership | static pick ∩ killed-mutant set = 1 |
| **T3_SINGLE_WEAK** | exactly one weak identity indicator decides | name-match alone; focal-membership alone; nearest-write heuristic through nested control flow | coverage-in-focal-class |
| **T4_GUESS** | ranked pick among ≥2 feasible candidates; position/stable-order broke the tie | ranking function below; alternatives recorded | killed-mutant set disambiguates |
| **T5_NONE** | no candidate exists | — | — |

**Identity indicators** (the corroborators that promote tiers): name agreement between test method
and candidate (`testGcd` ↔ `gcd`, Methods2Test name-strip), and focal-class membership. Type
eligibility and statement position are **ranking preferences, never corroborators** — they order
candidates but cannot promote confidence (position says nothing about developer intent; type
eligibility is about *our* generator, not the test's meaning).

**Ranking function** (within the candidate pool, lexicographic):
1. type-eligible before type-ineligible (`TypeCapability.supportsGeneratedInput` on ≥1 parameter
   and `supportsReturnValue` on the return type) — a type-ineligible pick loses at the
   `ParameterType`/`ReturnType` gate anyway, so prefer the candidate that can generalize;
2. focal-class member before non-member;
3. name-match before non-match;
4. later statement position before earlier (LCBA's one honest ounce: proximity to the assertion);
5. stable syntactic order (deterministic tie-break).

Tier assignment for a ranked pick: ≥2 identity indicators on the winner → T2; exactly 1 → T3;
0 (position/stable-order decided) → T4. Ties broken purely by rank never promote.

### C — Validators (confirm/refute; never identify)

- **Coherence seed-check** (runtime, exists today): refutes incoherent picks; the backstop that
  makes best-effort safe.
- **Killed-mutant disagreement** (future, PIT_ORIGINAL): the pick kills no mutants but a reachable
  sibling does → flags a likely coherent-shallow mis-target. The one signal that catches what
  coherence cannot. The oracle answers "whose faults does this test detect?"; static dataflow
  answers "whose output does the assertion check?" (what spec extraction needs). They usually
  coincide; on divergence the static oracle-value method drives generalization and the mutation
  oracle drives shallow-pick refutation and T4 disambiguation. The oracle is therefore the
  strongest **corroborator/refuter**, and the top *identifier* only when static is ambiguous —
  not the unconditional cascade-top (`2026-06-27-ensemble-mut-identification` is recast
  accordingly).

### The shallow-pick flag

A dataflow-proven producer can still be a zero-argument inspector on a receiver whose producer is
out of reach (e.g. `sut.process(x); assertEquals(5, sut.getTotal());` traces to `getTotal`). The
trace is T1 — the asserted value *is* `getTotal`'s output — but the pick is shallow. The resolver
sets `shallow_inspector_pick = true` whenever the accepted pick is a zero-argument inspector whose
receiver producer could not be resolved, so analysis can slice these out of "proven" claims and the
future oracle knows where to look first.

## Data model — `mut_resolution_observation`

One row per assertion, written by `TestAnalysisTask` after resolution (supersedes the
`mut_resolution_observation` sketch in `2026-07-01-pipeline-observability-telemetry`, which is
updated to match). `status` and `confidence_tier` are **orthogonal**: status = pipeline
consequence, tier = evidence strength. A library-target pick can be T1-proven yet
characterization-only.

| Column | Type | Meaning |
|---|---|---|
| `assertion_id` | FK | assertion analyzed |
| `project_id`, `test_id` | FK | denormalized owners |
| `status` | TEXT | `RESOLVED`, `CHARACTERIZATION_ONLY`, `NONE` |
| `confidence_tier` | TEXT | `T1_PROVEN` … `T5_NONE` |
| `deciding_signal` | TEXT | who decided (enum below) |
| `corroborating_signals` | TEXT (JSON array) | who agreed: `NAME_MATCH`, `FOCAL_CLASS_MEMBER` |
| `no_pick_reason` | TEXT nullable | for `CHARACTERIZATION_ONLY`/`NONE`: `LIBRARY_DECLARATION`, `UNRESOLVED_SOURCE_DECLARATION`, `NO_VISIBLE_CALL`, `UNSUPPORTED_ASSERTION_SHAPE` |
| `candidate_count` | INTEGER | producer candidates considered |
| `resolved_call_source` | TEXT nullable | source text of the picked call |
| `resolved_method_name` | TEXT nullable | picked method simple name |
| `resolved_declaring_type` | TEXT nullable | declaring type, when resolvable |
| `resolved_parameter_types` | TEXT nullable (JSON) | textual parameter signature |
| `resolved_return_type` | TEXT nullable | textual return type |
| `inspector_unwrapped` | BOOLEAN | inspector unwrapped to receiver producer |
| `shallow_inspector_pick` | BOOLEAN | accepted pick is an unresolvable-receiver inspector |
| `focal_type` | TEXT nullable | resolved focal class |
| `focal_type_source` | TEXT nullable | `PATH_AND_NAME`, `NAME_ONLY`, `PATH_ONLY`, `NONE` |
| `focal_agreement` | BOOLEAN nullable | pick's declaring type == focal type (null without focal) |
| `candidate_param_count` | INTEGER nullable | pick's parameter count |
| `candidate_param_supported` | BOOLEAN nullable | ≥1 param passes `TypeCapability.supportsGeneratedInput` |
| `candidate_return_supported` | BOOLEAN nullable | return passes `TypeCapability.supportsReturnValue` |
| `oracle_agreement` | TEXT nullable | reserved: `AGREED`, `REFUTED`, `ABSENT` (populated when PIT_ORIGINAL lands) |
| `candidate_details` | TEXT nullable (JSON) | ranked alternatives incl. the losers of T4 guesses |

`deciding_signal` values: `DIRECT_ACTUAL_CALL`, `LOCAL_VARIABLE_PRODUCER`, `FIELD_PRODUCER`,
`SUBEXPRESSION_PRODUCER`, `INSPECTOR_UNWRAP`, `UNIQUE_PRODUCER_ELIMINATION`,
`ASSERT_THROWS_LAMBDA`, `RANKED_GUESS`, `NONE`.

The former `AMBIGUOUS`/`MULTIPLE_PRODUCERS` terminal states demote to a `RANKED_GUESS` row with
`candidate_details` populated. JSON is stored in TEXT columns (Gson), matching every existing
table; SQL casts to jsonb where needed (`create-views.sql` precedent).

## Invariants (revised)

1. **Evidence-recording replaces abstain-on-ambiguity.** Every assertion gets an observation row;
   ambiguity yields a ranked guess with alternatives recorded, not an empty result. Abstention
   (T5) is reserved for "no candidate exists."
2. **Coherence backstop stands** (unchanged): incoherent picks self-exclude via the seed-first
   property; verified at `TestGeneralizationTask.java:313,358-373,423-426,480-508`.
3. **Tier-slicing is mandatory.** Every downstream analysis that aggregates over generalizations
   or resolution outcomes slices by `confidence_tier`. Headline soundness/attribution claims cite
   T1/T2 only; RQ1 mutation-score comparisons join the tier column. Until the killed-mutant
   refuter exists, T3/T4 picks are the ~43%-precision population (He et al.) and must never be
   presented as resolved-with-confidence.
4. **No regression of the working corpus** (unchanged): every pick the current
   `TestAnalysis.findTestedMethodCall` returns, the fusion resolver returns identically (same
   `CtInvocation`); new picks appear only where the current resolver returns empty. The ~250
   census generalizations stay identified and sound.

## Acceptance criteria

- The resolver returns a `MutResolution` for **every** assertion (never null, never empty):
  pick + tier + deciding signal + corroborators + ranked alternatives, or an explicit T5/`NONE`.
- `mut_resolution_observation` is written for every analyzed assertion in the same task that
  resolves it; analysis can compute the tier funnel ("X% T1, Y% T2, Z% T3, W% T4, V% T5") and the
  MissingValue×tier cross-tab from DB queries only.
- Characterization-grade rows never populate declaration-dependent `tested_*` columns; no
  assertion with a null declaration reaches `JpfInstrumentationTask` (the CtPath NPE documented in
  `2026-06-27-ensemble-mut-identification` §CtInvocation-recovery).
- Invariant 4 is demonstrated: characterization tests reproduce today's picks exactly, and the
  census generalization count does not drop on rerun.
- The 20-project spike quantifies: per-tier assertion counts, newly-attempted (previously
  `MissingValue`) assertions entering JPF, their per-tier survival to generalization, and the JPF
  block's added wall-clock cost.
- Mis-targeting spot-check stratified by tier (T4 sampled heaviest) shows T1 picks are the
  intended method in every sampled case; T3/T4 mis-target rates are recorded (not required to be
  zero — they are the honesty story), and any T1 miss is a resolver bug to fix before proceeding.

## Relationship to Methods2Test and the literature

- **Adopted from Methods2Test** (Tufano et al., MSR'22, arXiv:2203.12776): path matching +
  name matching for the focal class (their high-precision stage — the gap our current resolver
  has); name-strip matching test↔method and unique-call intersection as *indicators*.
- **Demoted relative to Methods2Test:** their method-stage heuristics decide outright (and they
  discard non-matching tests — precision ~91% only on the kept subset); here they are T2/T3
  corroborators/deciders inside a model whose T1 is a constructed dataflow proof they don't have,
  and whose downside is bounded by the coherence gate they don't have.
- **He et al. (FSE'24, DOI 10.1145/3660785):** LCBA 43.38%P/38.42%R against developer intent —
  the reason bare position evidence caps at T4 and never corroborates.
- **TCTracer (White & Krinke, EMSE'22, DOI 10.1007/s10664-021-10079-1):** ensemble fusion
  outperforms single techniques; their score-combination needs labeled ground truth we lack, hence
  lexicographic tiers instead of weights.
- **Ghafari et al. (ICST'15):** the mutator/inspector split behind inspector unwrapping.

## Scope

- **v1 (the companion plan):** AST-only — dataflow tiers, unique-producer elimination, ranked
  guess, path+name focal class, the observation table, tier funnel analysis, spike verification.
- **Deferred:** killed-mutant oracle tiers and `oracle_agreement` population (gated on enabling
  `PIT_ORIGINAL`, its own cost decision — PIT is 48% of pipeline runtime); setup-method/`@Before`
  field writes (v1 resolves in-test-method writes only); inherited test hierarchies
  (`2026-06-27-inherited-test-method-support`); the differential-oracle conjunct (retain the
  original asserted relationship as an independent conjunct in emitted tests) — revisit if spike
  mis-targeting for T3/T4 is worse than the He et al. baseline.
