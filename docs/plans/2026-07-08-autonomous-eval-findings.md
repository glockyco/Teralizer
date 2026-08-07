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

### RQ0 census breadth: three projects yield no generalizations

Verified against `postgres_jarvis_census` (per-task stage/status/info), the RQ0
breadth dashes are genuine limits at specification extraction and test execution,
not a since-fixed generation bug:

- **jexl** — every JPF symbolic execution of its assertions fails (`EXECUTE_JPF`:
  uncaught `IllegalArgumentException` / `NoClassDefFoundError` under JPF, 9 tasks),
  and `ANALYZE_JPF` then excludes all assertions, so no specification is extracted
  and no generalization is produced.
- **email** — `ANALYZE_JPF` excludes all assertions during specification extraction
  (no specifications), so no generalization is produced.
- **pool** — its slow original suite exceeds the runtime ceiling at
  `EXECUTE_TESTS_ORIGINAL` (genuine timeout); the run never reaches extraction.
- **io** — is NOT dashed (774 PVC / 7 MUTs). It generalizes successfully
  (`GENERALIZE_TESTS`, `FILTER_GENERALIZATIONS`, and `EXECUTE_TESTS_GENERALIZED`
  all succeed); 767 of its per-assertion `EXECUTE_JPF` tasks fail (generic JPF
  execution errors and `SEARCH_DEPTH_LIMIT` aborts) but the survivors yield 7 MUTs.

Decision (declined): no rerun. These causes are genuine SPF/JPF extraction limits
(io/jexl/email) and a runtime timeout (pool); none is the equality-filter
generation issue fixed later, so a rerun would not materially change them. Per the
first-run-numbers-stand principle the breadth is final as measured, and the thesis
RQ0 tables match this census exactly.

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

## RepoReapers RQ6 result

Two valid RepoReapers databases hold the RQ6 evidence over the full
1161-project corpus:

- `postgres_reporeapers` — the gen-only Stages 1-4 applicability baseline: 84
  projects have at least one included generalization; 944 generalizations
  included and 2915 excluded. Structural attrition concentrates at the early
  build/setup stages (`SETUP_PROJECT` 355, `BUILD_SPOON_MODEL` 342,
  `EXECUTE_TESTS_ORIGINAL` 237, `BUILD_PROJECT_ORIGINAL` 188 -- expected for an
  uncurated corpus, e.g. unresolvable `pom.xml` classpaths). The dominant funnel
  reject categories are unsupported return types, methods with no generalizable
  parameters, unresolved tested-file metadata, and assertion-free or non-passing
  tests.
- `postgres_reporeapers_rq6` — a superseded full-pipeline RQ6 corpus
  (`IMPROVED_200`, with Stage-5 reduction), collected before the reduction-path
  and JUnit 3 assertion-analysis fixes. Kept for archaeology, not citable.

Consumption decision: both the paper and the thesis draw RQ6 from the
`postgres_reporeapers_rq6_v6` corpus, collected with JUnit 3 assertion analysis,
guaranteed resolver telemetry, and resolver-attributed `MissingValue` rejects.
Applicability is measured after Stage 5 of the full pipeline, including
test-suite reduction, and the pre-reduction Stage-4 count is reported beside it.
Pre-recollection figures cannot be reproduced from any surviving database and
are not presented. RQ1--RQ5 stay on `postgres_dev` and are not re-run, so their
tables and prose are unchanged. Chapter work is tracked in the thesis repository
under `2026-08-04-teralizer-chapter-refresh`.

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
