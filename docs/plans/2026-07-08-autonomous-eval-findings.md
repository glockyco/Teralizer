---
title: Autonomous Evaluation Run Findings
type: audit
status: active
created: 2026-07-08
parent: 2026-06-26-teralizer-overview
---

# Autonomous Evaluation Run Findings

Findings from the unattended evaluation run (census RQ0, then RepoReapers rerun3
RQ6, then PIT reduction). Captured for discussion. Fixes marked committed are on
`master`; open items need a decision.

## Open items for discussion

### Census understates io, jexl, and pool

The census run that produced the current `postgres_jarvis_census` predates two
fixes that were committed while it ran, so three projects are understated and a
clean re-run would change their numbers:

- **io, jexl** — a correct but near-unsatisfiable equality filter
  (`str1.equals(str2)` with the two parameters drawn independently) spun in
  jqwik's filtered generator and timed out into attrition. The equality-binding
  fix now generates such a pair by construction, but these two projects had
  already timed out in this run.
- **pool** — its original test suite (slow concurrency/eviction tests) timed out
  during `EXECUTE_TESTS_ORIGINAL`. In this run that surfaced as a structural
  failure and halted the census at 11/12; the timeout-as-attrition fix landed
  afterward. text (project 12) was then run separately to complete the set.

Recommendation: a clean full census re-run with every fix in place gives the
definitive RQ0 breadth for io/jexl/pool. Deferred here to prioritize the
RepoReapers rerun per the run sequence.

### jqwik memoization-equality amplifier (jqwik-internal, open)

Low-acceptance filters are slow to give up because jqwik memoizes generators
keyed on arbitrary equality, and comparing `.map`/`.flatMap` arbitraries reflects
over captured lambda fields and throws `WrongMethodTypeException` per attempt (the
`fillInStackTrace` cost dominates). The equality-binding fix sidesteps this for
equality constraints, but any correct-but-selective filter still pays it. Options
if it recurs materially: hoist per-parameter arbitraries to shared instances so
they memoize once, or lower the generated `@Property` discard threshold so a
selective filter gives up in seconds. Not attempted (jqwik-internal, uncertain
payoff, tuning tradeoff).

### MISSING_TESTED_FILE prevalence (flag, not investigated)

`MISSING_TESTED_FILE` rejections are large corpus-wide (commons-math 3308,
commons-lang 3043). Expected for full suites, which exercise library and
non-project methods with no resolvable source file, but if RQ0/RQ6 breadth looks
low this filter is the first place to audit.

## Fixes applied during this run (committed)

- **Test-execution timeout is attrition, not a halt** — a slow original, initial,
  or generalized suite records a timeout diagnostic and the run continues instead
  of a project-level failure aborting the whole corpus run.
- **`--no-reduction` on the RepoReapers rerun runner** — the rerun profile
  disabled PIT but did not set the phase toggle, so under the phase-decoupled
  model the reduction phase defaulted on for a funnel-only rerun.

(These are in addition to the spec-soundness fixes from the interactive phase:
the SPF `String.length` collect-mode fix, the seed-vs-spec guard, the dedup
bound, the generalized-suite-timeout attrition, the `NO_INPUT_SPEC` attrition,
and the var/var string-equality by-construction binding.)
