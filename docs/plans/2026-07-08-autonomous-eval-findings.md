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

Decision (declined): no rerun. Per the first-run-numbers-stand principle, the
census breadth is final as measured and the io/jexl/pool dashes are accepted
outcomes (pool's slow original suite times out; io/jexl trace to the
since-fixed jqwik equality-filter inefficiency; email extracts no
specifications). The thesis RQ0 tables match this census exactly and are final.

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
  measurement-semantics decision to make deliberately, not an autonomous change.
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
- **PIT report mapping tolerates unattributable records** — generalized test
  classes run inherited or auxiliary methods that are neither the generalization
  nor an original test, and the coverage and mutation mappers threw when a record
  matched neither, halting the run. They now keep such records unlinked, matching
  the existing handling of names the parser cannot read, and the shared resolution
  is extracted into one tested seam.
- **Chunked PIT report inserts** — a full-suite coverage report has one row per
  covered block and test, millions of rows for a large project, and jOOQ rendered
  the whole batch into a single insert statement that exhausted the heap on
  commons-configuration. The coverage and mutation inserts now run in bounded
  chunks so memory stays flat across project sizes.
- **PIT and JaCoCo command timeouts are attrition** — a mutation or coverage
  command timeout was generic breakage, so io's original-suite PIT exceeding the
  3600-second tripwire halted the run. The classifier now records such timeouts
  as EXECUTION_TIMEOUT and the diagnostic writer's stage gate covers those
  stages, so a slow project drops its downstream data while the run continues.
- **Post-run breakage check excludes attrition** — the shell check counted
  timed-out tasks as breakage and reported the run failed. It now excludes the
  same attrition diagnostics the planner does.

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

Consumption decision: the thesis keeps the earlier 632-project / 11-complete
(1.7%) RepoReapers results; the rerun3 (1161-corpus) numbers are propagated only
into the paper at resubmit, not back-ported to the thesis. The stale thesis
exclusion/filtering/processing tables and RQ6 prose are therefore intentional,
not defects to fix.

## Census PIT status

The first reduction-only launch failed immediately: the pass injects
`-Dteralizer.pitest.enabled=true`, which the generation pass had left at its
default, so the rendered config drifted and the `ProjectIdentity` attach guard
refused to resume the stored workspace. Fixed at the root by treating
`pitest.enabled` as run-scoped and excluding it from the identity projection
(alongside the phase toggles), then relaunched.

The relaunch resumed cleanly but halted again on collections: PIT attributes
coverage and kills to inherited or auxiliary methods that generalized test classes
run (a JUnit-4 helper inherited from the original class), and the report mapper
threw when such a record matched neither a known test nor a generalization. Fixed
by keeping those records unlinked, matching how the mapper already tolerates names
it cannot parse, and relaunched once more. csv and collections then produced
mutation data.

The run then reached configuration and halted a third time, out of heap while
inserting coverage data because the batch insert built one statement for
millions of rows. Fixed by chunking the inserts, then relaunched from
configuration onward with csv and collections already complete.

It then halted a fourth time on io: a mutation or coverage command timeout was
generic breakage, and io's original-suite PIT exceeds the 3600-second tripwire.
The classifier and the diagnostic writer's stage gate now record such timeouts
as attrition, matching the planner and the tripwire intent, so a timed-out
project drops rather than halting the run.

Final result: seven of the nine projects with included generalizations produced
generalized mutation scores -- lang, configuration, collections, codec, text,
cli, and csv. io and math time out during original-suite PIT (their full suites
exceed the 3600-second budget) and are recorded as attrition. email, pool, and
jexl have no included generalizations. The run completes with no structural
halt.
