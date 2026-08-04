---
title: Reduction-Path Fixes Before Re-Collection
type: plan
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Reduction-Path Fixes Before Re-Collection

Close the reduction-stage failures that are ours to close, and make the remaining ones
readable in stored diagnostics, so the next RepoReapers collection produces data that can
be analyzed without reading command logs off disk. Evidence and mechanisms:
`2026-08-04-reduction-failure-anatomy`.

These fixes address 17 of the 31 reduction exclusions. They do not change applicability,
which is measured after Stage 4 and is unaffected by every failure here.

Out of scope: resource budgets. The original-suite test timeout stays at 300 s and the
mutation budget stays at 3600 s. Timeouts are normal attrition, not a defect to tune away.
Also out of scope: `pitest.original.enabled`, which stays `false`, so the mutation baseline
remains the `INITIAL` suite.

## File map

- Modify `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`: owns the derived POM, the surefire floor, and the argLine merge.
- Modify `src/main/java/teralizer/processing/diagnostics/TaskDiagnosticClassifier.java`: owns failure classification.
- Modify `src/main/java/teralizer/processing/diagnostics/TaskDiagnosticCodes.java`: owns the reason-code vocabulary.
- Modify `src/main/java/teralizer/processing/task/PitDataCollectionTask.java`: owns the mutation-report ingestion that rejects jqwik identifiers.
- Modify `src/main/java/teralizer/processing/filter/NonPassingTestFilter.java` and its input: owns which suite decides that a test passes.
- Modify `analysis/src/teralizer/eval/reports/_funnel.py` and `_taxonomy.py`: own the cause rows that consume the new reason codes.
- Test `src/test/java/teralizer/processing/dependencies/MavenSurefireFloorTest.java`, `src/test/java/teralizer/processing/diagnostics/TaskDiagnosticWriterTest.java`, plus new cases beside them.

## Tasks

### Task 1: Keep the argLine placeholder out of PIT's minion

**Files:**
- Modify: `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`
- Test: `src/test/java/teralizer/processing/dependencies/MavenSurefireFloorTest.java`

Our pitest plugin block is already in effect on the failing projects, `<jvmArgs>` included
(`src/main/resources/pitest-config-maven.txt:1-18`), so giving PIT its own arguments is not
the fix. PIT parses surefire's configuration *in addition* and performs its own property
substitution, which does not implement Maven's `@{…}` late replacement. Its log on
`github_com_astina_console` shows the attempt and the survival of the literal token:

```
[INFO] Replacing properties in argLine @{argLine} -Dfile.encoding=UTF-8
PIT >> INFO : MINION : Error: Could not find or load main class @{argLine}
```

Inlining a resolved JaCoCo agent path instead of the token is not available: the agent path
is set at runtime by `jacoco:prepare-agent` and is not knowable when the POM is written.

- [ ] Stop PIT from consuming surefire's `argLine`. Prefer disabling surefire-config parsing in
      the pitest plugin block if that plugin exposes such a parameter; verify the parameter name
      against pitest 1.17.0 before relying on it, since this checkout vendors no PIT source. If
      it does not exist, generate a separate PIT-specific POM without the `@{argLine}` token and
      point `AbstractTask.mavenBuildFileFor` at it for the two PIT stages, leaving the floored
      POM untouched for surefire runs.
  Verification: `./gradlew test --tests '*MavenSurefireFloorTest*'`
  Expected: surefire's argLine still begins with `@{argLine}`, and the POM PIT reads carries no
  `@{` token.

- [ ] Reproduce on the project that exposed the defect.
  Run: reduction on `github_com_astina_console` with `REPOREAPERS_PROFILE=project-configs/reporeapers-rq6.conf` into a scratch database
  Expected: `COLLECT_PIT_DATA_INITIAL` succeeds; the captured minion log contains no
  `Could not find or load main class @{argLine}` and no `MINION_DIED`.

- [ ] Commit.
  Message: `fix(pipeline): resolve the argLine placeholder for PIT`

### Task 2: Floor the pitest plugin version

**Files:**
- Modify: `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`
- Test: `src/test/java/teralizer/processing/dependencies/MavenSurefireFloorTest.java`

- [ ] Floor `pitest-maven` in the derived POM the way surefire is floored, so a project pinning
      an unusable version does not decide the mutation run. Leave a project's newer pin alone.
  Verification: `./gradlew test --tests '*MavenSurefireFloorTest*'`
  Expected: a POM pinning `pitest-maven` 0.24 yields the floor version; a POM pinning 1.17.0
  is unchanged.

- [ ] Commit.
  Message: `fix(pipeline): floor the pitest plugin version`

### Task 3: Accept jqwik test identifiers in mutation reports

**Files:**
- Modify: `src/main/java/teralizer/processing/task/PitDataCollectionTask.java`
- Test: `src/test/java/teralizer/processing/task/PitDataCollectionTaskTest.java`

- [ ] Parse `<class>.[engine:jqwik]` style identifiers, and keep a record unlinked rather than
      throwing when a name cannot be read, matching how the coverage mapper already tolerates
      inherited and auxiliary methods.
  Verification: `./gradlew test --tests '*PitDataCollectionTaskTest*'`
  Expected: a report row named
  `net.byteseek.compiler.matcher.SequenceMatcherCompilerTest.[engine:jqwik]` is ingested and
  linked to its generalization; an unreadable name is stored unlinked without an exception.

- [ ] Commit.
  Message: `fix(pipeline): read jqwik test identifiers in mutation reports`

### Non-green suites are accepted attrition, not a task

`NonPassingTestFilter` is one of the stage-1 filters that *constitutes* the `INITIAL` suite,
so its input cannot be derived from `INITIAL` without circularity. The only non-circular
options are to execute `ORIGINAL` a second time against the floored POM, which doubles the
most expensive stage and changes what `ORIGINAL` measures, or to add a gate after
`COLLECT_JUNIT_REPORTS_INITIAL` that excludes newly failing classes from the mutation run
only — which introduces a suite subset the evaluation would then have to name.

Neither is worth 6 projects. PIT requires a green suite; some real-world suites are not green
in the environment PIT mutates. That is reported as a limit, in the same way resource limits
are. Two of the six are generalized-side anyway, where our own generalized tests fail PIT's
unmutated run, which is a separate question from this filter.

### Task 5: Type reduction failures in stored diagnostics

**Files:**
- Modify: `src/main/java/teralizer/processing/diagnostics/TaskDiagnosticCodes.java`
- Modify: `src/main/java/teralizer/processing/diagnostics/TaskDiagnosticClassifier.java`
- Modify: `analysis/src/teralizer/eval/reports/_taxonomy.py`
- Modify: `analysis/src/teralizer/eval/reports/_funnel.py`
- Test: `src/test/java/teralizer/processing/diagnostics/TaskDiagnosticWriterTest.java`
- Test: `analysis/tests/eval/test_taxonomy.py`

- [ ] Add `MINION_DIED`, `SUITE_NOT_GREEN`, `NO_TESTS_FOUND`, `PLUGIN_UNUSABLE`, and
      `REPORT_ABSENT`, classify coverage and mutation failures into them from the command
      output, and stop returning `LISTENER_BUG` for anything that is not a JPF listener fault.
  Verification: `./gradlew test --tests '*TaskDiagnosticWriterTest*'`
  Expected: each code round-trips; a minion-death log yields `MINION_DIED`, an unmutated-run
  failure yields `SUITE_NOT_GREEN`, an absent report yields `REPORT_ABSENT`.

- [ ] Map the new codes to reduction cause rows and delete the `_fallback_cause` guess of
      "PIT reports not found", so an unmapped reduction failure surfaces as `UNCODED` instead
      of being silently mislabeled.
  Verification: `uv run --directory analysis pytest tests/eval/test_taxonomy.py tests/eval/test_funnel.py`
  Expected: taxonomy tests cover each new code; the funnel still leaves no project `UNCODED`
  on the existing corpus.

- [ ] Commit.
  Message: `feat(diagnostics): type coverage and mutation failures`

### Task 6: Gate

- [ ] Build and test the pipeline.
  Run: `./gradlew build`
  Expected: green.

- [ ] Run the analysis gate.
  Run: `uv run --directory analysis pytest tests/eval` then `uv run --directory analysis ruff check .` then `uv run --directory analysis ty check .`
  Expected: `test_funnel.py`, `test_rq6_causes.py`, and `test_taxonomy.py` green; ruff and ty
  clean. `test_smoke.py::test_registered_reports_build` remains failing on the `minDouble`
  fixture probe in `jarvis_scoreboard.py`, which is unrelated in-flight RQ0 work.

- [ ] Commit.
  Message: `chore(pipeline): gate the reduction-path fixes`
