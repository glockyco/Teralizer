## Context

See `proposal.md` for motivation. `.github/workflows/verification-corpus.yml` currently runs on every push to `main` or `master`, weekly, and by manual dispatch. `scripts/verify-pipeline.sh` builds the complete Java project, executes each tracked synthetic fixture against PostgreSQL, and compares the resulting database state with checked-in goldens. The workflow uses one branch-independent concurrency group and has no job timeout.

Across 27 completed hosted runs inspected on 2026-08-22, 18 succeeded, 4 failed, and 5 were cancelled. Successful runs ranged from about 19.0 to 21.6 minutes, with a median near 20.6 minutes. A cancelled outlier remained active for about 361 minutes. Historical failures also prove that this lane detects real pipeline, environment, fixture, and golden regressions.

## Goals / Non-Goals

**Goals:**

- Keep push coverage for every tracked input that can affect the full synthetic pipeline result.
- Avoid corpus runs for changes that cannot affect that result.
- Preserve manual diagnosis and weekly runner/dependency drift detection.
- Bound a stuck hosted job without constraining normal successful runs.
- Make omissions from the owner-path set detectable in repository validation.

**Non-Goals:**

- Reduce fixture coverage, bypass the full pipeline, or replace the lane with unit tests.
- Move the corpus to pull requests or make it a review gate.
- Change fixture behavior, goldens, database lifecycle, or pipeline semantics.
- Schedule production corpus, report generation, collection, mutation-testing, sentinel, hotspot, or JARVIS runs.
- Introduce a third-party changed-files action or a generic workflow framework.

## Decisions

### 1. Treat the workflow path list as an executable ownership boundary

Add native push `paths` filters for these input groups:

- `.github/workflows/verification-corpus.yml`;
- `scripts/verify-pipeline.sh`, `scripts/run-verification-corpus.sh`, `scripts/check-verification-corpus.sh`, `scripts/corpus-registry`, and the `scripts/lib/` helpers they source;
- `src/`, `jpf-symbc/`, and the Gradle wrapper and build declarations used by the hosted workflow;
- `project-configs/verification.conf` and `project-configs/verification/`;
- `verification/fixtures/` and `verification/golden/`.

The implementation task must trace imports and file reads again before editing and add any missed transitive input. Broad owner roots are preferred over fragile lists of individual Java classes or fixture files.

Scheduled and manual triggers do not use changed-path filtering. Keep both unchanged.

**Alternative:** Keep every push. Rejected because unrelated prose and report-renderer changes consume the same 20-minute lane without increasing pipeline confidence.

**Alternative:** Use `paths-ignore`. Rejected because a positive owner list states the contract directly and can be checked for representative inclusions. An ignore list silently treats every unknown future directory as pipeline-relevant.

### 2. Use a 35-minute job timeout

Set `timeout-minutes: 35` on the corpus job. This leaves more than 13 minutes above the slowest observed successful run while stopping failures far earlier than the six-hour outlier.

The timeout belongs to the job, not an individual step, because checkout, dependency setup, PostgreSQL installation, Gradle build, fixture execution, and golden comparison are all required parts of one result.

**Alternative:** 30 minutes. Rejected because its margin over observed success is smaller without a material cost benefit; cancellation already removes superseded runs.

**Alternative:** No timeout or the platform maximum. Rejected because the historical outlier proves that pipeline or runner failure can remain active for hours.

### 3. Preserve cancellation but scope it per branch

Keep `cancel-in-progress: true`, but include the workflow name and ref in the concurrency group. A newer qualifying push to the same branch supersedes the old result. A scheduled or manual run on another ref does not invalidate it.

**Alternative:** Retain one global group. Rejected because runs on different refs answer different questions and should not cancel each other.

### 4. Check the trigger contract without calling GitHub

Add a repository check that loads the workflow and evaluates its declared push path patterns against a table of representative paths. Positive cases must cover every owner group. Negative cases must include OpenSpec prose, thesis or documentation text, and analysis report-renderer files.

The check also asserts the weekly schedule, manual dispatch, branch-scoped cancellation, and 35-minute timeout. It validates declarations; hosted execution remains the proof that GitHub accepts and runs the workflow.

Use the repository's existing pinned Python/Nix environment and YAML parser. Do not add a third-party action or duplicate path detection inside the workflow.

**Alternative:** Inspect the YAML manually. Rejected because owner-path drift would remain invisible until an expected run was absent.

## Risks / Trade-offs

- **A transitive input is omitted.** A relevant push could skip the corpus. Mitigate with broad owner roots, import/read tracing, positive fixture cases for every owner group, and review of the full driver dependency graph.
- **A broad path causes unnecessary runs.** This costs hosted time but does not reduce correctness. Prefer conservative inclusion when ownership is ambiguous.
- **A valid run exceeds 35 minutes after the fixture set grows.** The timeout will expose the growth as a failed measurement. Reassess it from first-run evidence; do not rerun to select a cleaner duration.
- **GitHub path semantics differ from the local check.** Keep the check to the subset of glob behavior used by the workflow and validate the first hosted push whose changed files include the workflow.
- **Cancellation hides an older failure.** Preserve the first-run result in GitHub history; cancellation means only that a newer revision superseded the question, not that the older run passed.

## Migration Plan

1. Trace the complete verification driver dependency and owner-path inventory.
2. Add the declaration-level trigger contract check and prove its positive and negative fixtures against the current workflow.
3. Add push path filters, branch-scoped concurrency, and the 35-minute job timeout.
4. Run repository validation and the declaration-level contract check.
5. Push the workflow change. Confirm a relevant-path push schedules and completes the hosted corpus, and confirm an unrelated-path push does not schedule it.
6. Record hosted run URLs in the task checklist before archiving.

Rollback is a normal commit revert. It restores broader scheduling and does not mutate corpus databases or generated data.
