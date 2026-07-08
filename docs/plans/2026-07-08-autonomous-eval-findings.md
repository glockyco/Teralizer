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

### PIT and JaCoCo command-timeout classification gap

`TaskDiagnosticClassifier` maps a "Command execution timeout exceeded" to
attrition (`SUITE_TIMEOUT` / `EXECUTION_TIMEOUT`) only for the three
`EXECUTE_TESTS_*` stages. A command timeout in `COLLECT_PIT_DATA_*` or
`COLLECT_JACOCO_DATA_*` falls through to a generic breakage code, so
`throwOnStructuralFailures` counts it as breakage and the runner's post-run
check reports the run as failed. Under the phase-decoupled model the runner
loop does not break on a failed config, so the census PIT still collects data
for every fitting project -- only the final pass/fail label and the
breakage-vs-attrition funnel bucket are affected, not the collected PVC.

The census configs set `pitest.max-execution-time = 3600` and call it a
"tripwire ... drops the whole fixture downstream", so a PIT timeout on lang or
math is an expected drop, not breakage. The intent is genuinely ambiguous:
either a PIT/JaCoCo timeout should be attrition (consistent with the
test-execution timeout fix and the tripwire wording), or the drop is meant to
be structural. Deciding it one way lets the census PIT report cleanly. Left for
the operator; not changed mid-run without sign-off.

### rerun3 PIT is blocked and out of scope for this run

Running the reduction (PIT) phase over the rerun3 workspace is blocked, and not
needed for RQ6:

- The rerun profile disables PIT and never sets a mutation budget, so the stored
  rerun3 configuration carries the reference default `pitest.max-execution-time`
  of 60 seconds. A useful budget (the census uses 3600) cannot be supplied by a
  system property on the reduction pass, because `pitest.max-execution-time` is
  part of the project-identity hash, so raising it would drift the identity and
  the attach guard would refuse to resume. Unlike `pitest.enabled`, the budget
  also shapes the measured outcome, so excluding it from identity is a
  measurement-semantics decision for the operator, not an autonomous change.
- The rerun runner skips any project carrying a per-project done-marker, and the
  generation pass marked all 1161, so a reduction pass would also need a separate
  marker namespace plus its own log and ledger paths to stay independently
  resumable.

RQ6 is the real-world failure funnel, which the rerun3 generation pass already
delivered, and the rerun profile states outright that mutation scores are not
relevant to it. Census PIT covers the curated projects where mutation scores
matter for RQ0. Recommendation: treat rerun3 PIT as optional and, if wanted,
settle the budget-versus-identity question first.

## Fixes applied during this run (committed)

- **Test-execution timeout is attrition, not a halt** — a slow original, initial,
  or generalized suite records a timeout diagnostic and the run continues instead
  of a project-level failure aborting the whole corpus run.
- **`--no-reduction` on the RepoReapers rerun runner** — the rerun profile
  disabled PIT but did not set the phase toggle, so under the phase-decoupled
  model the reduction phase defaulted on for a funnel-only rerun.
- **`pitest.enabled` is run-scoped, not project identity** — the reduction-only
  PIT pass forces PIT on via a system property the generation pass lacked, which
  drifted the config-identity hash so the attach guard refused to resume the
  generated workspace. It now shares the phase toggles' exclusion from the
  identity projection.

(These are in addition to the spec-soundness fixes from the interactive phase:
the SPF `String.length` collect-mode fix, the seed-vs-spec guard, the dedup
bound, the generalized-suite-timeout attrition, the `NO_INPUT_SPEC` attrition,
and the var/var string-equality by-construction binding.)

## RepoReapers rerun3 result (RQ6)

Gen-only funnel over the full 1161-project corpus (PIT disabled). 75 projects
have at least one included generalization; 1043 included and 2697 excluded
generalizations; 1252 structural failures clustered at the early stages
(`SETUP_PROJECT` 355, `BUILD_SPOON_MODEL` 342, `EXECUTE_TESTS_ORIGINAL` 236,
`BUILD_PROJECT_ORIGINAL` 188) -- expected build/setup attrition for an
uncurated corpus (329 of 355 setup failures are "Failed to resolve classpath
from pom.xml"). Top funnel reject reasons: `MISSING_TESTED_FILE` 45606,
`NO_GENERALIZABLE_PARAMETERS` 44202, `UNSUPPORTED_RETURN_TYPE` 36609,
`NO_ASSERTIONS` 21508, `EXCLUDED_PARENT_TEST` 18965, `TEST_NOT_PASSING` 7794.

Not a regression. An early comparison to a "514 usable" rerun2 figure was a
metric mismatch: 514 was rerun2's broad `final_usable` count, while the strict
"at least one included generalization" metric gives rerun2 = 59 and rerun3 =
75. On the same metric rerun3 slightly beats rerun2, consistent with the
spec-soundness fixes.

## Census PIT status

The first reduction-only launch failed immediately: the pass injects
`-Dteralizer.pitest.enabled=true`, which the generation pass had left at its
default, so the rendered config drifted and the `ProjectIdentity` attach guard
refused to resume the stored workspace. Fixed at the root by treating
`pitest.enabled` as run-scoped and excluding it from the identity projection
(alongside the phase toggles), then relaunched.

Reduction-only runs over the nine projects with included generalizations,
smallest first (csv 2, collections 5, configuration 21, codec 30, io 32, text
54, cli 201, math 273, lang 856); email/pool/jexl have zero included
generalizations and are skipped. math and lang are expected to trip the 3600s
PIT tripwire (see the classification-gap item above).
