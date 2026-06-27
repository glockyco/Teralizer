---
title: Data Reuse & MSR Paper Potential (secondary outputs)
type: note
status: active
created: 2026-06-26
parent: 2026-06-26-teralizer-overview
---

# Data Reuse & MSR Paper Potential (secondary outputs)

**Primary target stays beating JARVIS** (see `2026-06-26-teralizer-overview`).
This note is a **deferred backlog** of opportunistic secondary outputs (an MSR data /
mining paper) plus the provenance/data they'd want.

Sources: `history://MsrOracle` (strategy consult), `history://FocalMethodLit`
(source-verified focal-method literature), MSR Data/Tool-Showcase norms +
Methods2Test (web), and first-hand audits of the data model + `data/` on disk.

## Status: deferred, gated on the JARVIS result

**No full re-run is planned.** Current focus is *only* the JARVIS comparison. The
sequence is **(1) beat JARVIS → (2) extend data collection → (3) re-run the full
evaluation**, and (2)–(3) happen *only if* (1) gets reasonable results. So:
- Everything here is **deferred** until (1) lands — a backlog, not a to-do list.
- Step-(1) primary implementation work is tracked separately, not in this backlog.
- Items that need a re-run to populate are **aspirational** (step 3). Reassuringly,
  most of the value — the tagging layer and both papers — is **buildable post-hoc from
  the 31 GB already on disk**, no re-run (see §"What needs a re-run").
- Better provenance for the **JARVIS eval** specifically is cheap (quick to re-run) and
  can wait until there are first promising results.

## Guiding principle (the bar every output must clear)

Every item below must have **a named consumer + the task it unblocks**, not just
volume. The value is created by a **descriptive feature/tagging layer** (§"Make the
data useful") that turns a pile of specs into a *queryable* benchmark — without it,
none of these are publishable.

## Snapshot the at-risk asset

The real corpus is `~/Projects/test-generalization/data/` = **31 GB / 34,838
symbolic spec files ≈ ~17k assertion-level specs** (the eval run; the 5.7 GB / 937 in
`-dev` is a subset). With 16 GB `projects/` + a 23 GB `database/` dir, the footprint is
**why a full Zenodo upload was never done**. Existing partial backups:
`replication/datasets/*.dump`, `data-dev.zip`, `database/db.sqlite`. The only
genuinely-ephemeral bit is the gitignored spec content + original env versions;
everything else is re-derivable from the kept sources + DB + commit SHAs. This is a
step-(2)/(3) backlog item.

## Secondary outputs (re-ranked, benefit-grounded)

1. **Barrier study — strongest, data in hand; needs a real thesis, not a failure census.**
   "Symbolic execution is weak on strings/loops/native calls" is textbook; a stats
   dump is rejectable. The defensible contribution is the **shadowing-aware,
   co-occurrence decomposition** on 1,160 real repos / 122k assertions: *which*
   barrier blocks *what fraction* of real-world test-generalization attempts,
   separating **fundamental (symex theory) vs self-inflicted (tool scoping) vs
   engineering-fixable**, with the multi-blocker result (73% blocked by ≥2 filters →
   single fixes are marginal) and the object/string-**state** ceiling. *Benefit:* it
   tells the test-generalization / PBT-inference subfield **where engineering
   investment actually pays off**, and ships a **barrier-labelled corpus** others can
   mine. Parallel precedent that this framing gets accepted: He et al. (FSE'24) is a
   pure empirical study of *where* automated focal-method/oracle techniques fail.
   **Risk to flag honestly:** lands only if (a) no prior work does this decomposition
   for test-generalization specifically, and (b) every barrier is tied to a concrete
   capability + actionable fix. Venue: MSR Technical / EMSE / ICSME. ~3–5 wk.
2. **Path-exact symbolic-specification dataset — highest novelty; concrete consumers.**
   *Who uses it:* (i) **ground-truth evaluation set for neural assertion/oracle
   generators** (ATLAS, T5, IR, TOGA) — He et al. (FSE'24) shows these are
   re-evaluated against better focal-method/oracle ground truth; our
   **execution-verified** path-exact oracles + valid input partitions are exactly that,
   and ~17k is ample for an eval/benchmark; (ii) **benchmark for
   specification-inference / invariant-detection** (Daikon-style, PBT property
   inference) — compare inferred properties against our path-exact specs; (iii) seed
   corpus for PBT-generation research. Honest: strongest as an *evaluation*
   ground-truth (17k plenty); modest as ML *training* data. Scale is **~17k extracted
   specs (eval run)**, though a fraction have degenerate/null output oracles (exact
   usable count from the `generalization` table). Venue: MSR Data & Tool Showcase
   (Zenodo DOI) or the artifact behind an ISSTA/ICSE paper. ~4–6 wk; deferred to step
   (2), but buildable post-hoc from existing data + the tagging layer (no re-run).
   *Absorbs* MUT/focal-mapping as a data-flow-provenanced facet.
3. **SPF/Z3 support — supporting evidence, NOT a standalone paper.**
   A state-of-the-art *survey* of solver/type support is not a contribution (SV-COMP
   already benchmarks solvers/verifiers). The spf-eval data (~80 subjects,
   Full/Partial/Crash + root causes: the `Double.MIN_VALUE` bounds bug,
   transcendental/bitwise crashes, native-peer gaps) is valuable **only as evidence for
   our own SPF improvements** ("here's what we extended — `FastMath.abs` model, bounds
   config — and here's what's next"). Fold into the primary paper / a short tool note;
   **drop it as a separate secondary output**.
4. **Teralizer tool + provenance-schema showcase** — open, DOI-citable; ship jointly
   with (2), not alone.

*Not a standalone paper:* our verified oracles double as a TOGA-class eval set — a
*reuse hook* for (2), already covered by consumer (i) above.

## Make the data useful (#5 — the value layer)

Specs are **structured typed expression trees** (`{_type, left, op, right}` over
`VariableReal`/`VariableInteger`/`Operator`), four files per assertion
(symbolic/concrete × input/output), and a **flat CSV export already exists**
(`dataset/dataset.csv` ≈147 MB + `build-extended-dataset.py`) plus a SQLite mirror.
Raw structure is fine.

The gap that actually creates research value is a **descriptive feature/tagging layer**
so others can slice the corpus — computable cheaply from the spec trees + Spoon AST +
`*_model_statistics` we already have:
- **Spec/MUT/test tags:** has-loop, has-recursion, involved types
  (primitive / `String` / array / collection / object), #branches & path-length,
  operation kinds in the path, exception-path flag, assertion kind, MUT signature
  shape, #constraints (have it), constraint-utilization (have it).
- **Packaging:** a documented schema + **datasheet-for-datasets**, stable natural-key
  IDs, and a clean **columnar/JSONL export** with the spec trees embedded + tags
  (supersede the ad-hoc `dataset/*.csv`). This is what makes (1) and (2) benchmarks
  rather than dumps.

## What needs a re-run vs. what's buildable now

**Buildable post-hoc from the existing 31 GB (NO re-run) — the bulk of the value:**
- The **descriptive tagging/feature layer** above — derivable from the stored spec
  trees + Spoon AST over the kept `projects/` checkouts + the DB.
- **SPDX license + repo identity** — re-resolvable from the pinned commit SHA
  (`git_version`), not dependent on a re-run.
- **Decouple "spec extracted" from `generalization.is_included`** — a DB/derivation
  question over data we already have.
- Both papers (1) and (2) themselves run on the already-collected DB + specs.

**Only obtainable from the eventual re-run (step 3 — aspirational, gated on JARVIS):**
- **Per-run env/toolchain versions** (Z3/JDK/OS at run time) — for the *existing* data
  only approximately reconstructable from the tool commit + machine; capture cleanly on
  any future run.
- **Raw SPF-native form** beside the `teralizer.domain` model (only if the transform
  proves lossy) + rendered `inputJava`/`outputJava`.
- **PVC / value-coverage hook** + a **structured `failure_cause` enum** captured at
  filter/JPF time. (PVC for the *JARVIS cases* is step-1 primary work — the JARVIS
  scoreboard — and is done there, not here.)

## Already present — do NOT rebuild (verified)

`input/output_model_statistics`, `generalization.total/used_constraint_count`,
`assertion.assertion_name`, the full per-filter decision log in `filter_result`
(no within-stage short-circuit — confirmed), `project.git_version` + `tool_git_version`,
the `dataset/*.csv` flat export + `database/db.sqlite` mirror.
NB: `equivalent_assertions` is a declared-but-empty placeholder — populate or drop.

## MUT identification: tracked in primary JARVIS work (#6)

The Ghafari mutator/inspector MUT-id improvement is step-(1) work tracked in
`2026-06-26-beat-jarvis-phase1`.

## Differentiation

- **vs Methods2Test (MSR'22):** they map test↔focal *syntactically* (name-strip +
  unique-call-intersection; 780k pairs / 91k repos; ~91% precision on the *kept* subset,
  non-matching tests discarded). Orthogonal to us *in kind* — verified path-exact
  partition + output oracle + concrete witnesses. Their mapping is
  high-precision-by-discarding; our labels can train/eval their model class.
- **vs TOGA (ICSE'22):** predicts oracles; ours are verified ground truth → eval set.
- **vs EqBench:** an input we run on, not a competitor.
- **Analytic independence:** (1) and (2) score on what we already extracted/measured, so
  they don't depend on the PVC *result* being good. Per current strategy they are still
  **deferred** until the JARVIS comparison shows promise (step 1) — the optionality is
  "a fallback we can build later from data in hand," not "work in parallel now."

## Redistribution note (right-sized, #7)

Reusing RepoReapers (an existing public dataset) for *analysis* needs no special
treatment — the SE community doesn't gate on it, and the barrier study (aggregate
stats) is fully shareable across all 1,160 repos. The *only* real question is **legal
redistribution of derived source snippets** *if* we publish source-bearing data — and
even that is moot if we ship aggregates/tags + a **rehydration script** (URL+commit+tool)
instead of verbatim code. If we do ship source-bearing data, do the one cheap thing
Methods2Test did: record SPDX license per project and restrict that release to
permissive licenses. Not an "ethics" section — a one-line licensing checkbox.

## Bottom line

**Deferred backlog, gated on JARVIS.** Snapshot deferred. No re-run planned, so the
re-run-only provenance (env versions, raw SPF form, full-eval PVC, failure enum) is
aspirational (step 3) — but the **tagging layer + both papers are buildable post-hoc
from the 31 GB already on disk**, so the fallback survives without a re-run. Step-(1)
primary implementation work is tracked separately. Sequence: **(1) beat JARVIS → (2)
extend collection → (3) re-run** — and we don't touch (2)/(3) until (1) is promising.
