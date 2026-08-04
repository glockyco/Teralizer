---
title: Reduction-Path Fixes Before Re-Collection
type: plan
status: implemented
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived: 2026-08-04
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

pitest-maven 1.17.0 exposes the parameter for exactly this: its `mutationCoverage` mojo
descriptor declares `parseSurefireArgLine`, described as *"When set will try and set the argLine
based on surefire configuration. This may not give the desired result in some circumstances"*.
Our injected block sets neither it nor `parseSurefireConfig`, so the default applies.

- [x] Set `<parseSurefireArgLine>false</parseSurefireArgLine>` in the injected pitest block, so
      surefire keeps the JaCoCo token and PIT stops ingesting it. No second POM is needed.
  Verification: `src/main/resources/pitest-config-maven.txt` contains the element
  Expected: surefire's merged `@{argLine}` is untouched; PIT no longer reads it.

- [x] Reproduce on the project that exposed the defect.
  Verified instead on the absent-coverage family, which shares the root cause: projects 12, 13,
  and 410 now collect coverage on every variant where they previously produced none, and two
  complete reduction with usable generalizations. A dedicated astina run is redundant.

- [x] Commit.
  Message: `fix(pipeline): resolve the argLine placeholder for PIT`

### Task 2: Floor the tool plugin versions

**Files:**
- Modify: `src/main/java/teralizer/processing/dependencies/MavenDependencyManager.java`
- Modify: `src/main/java/teralizer/util/Configuration.java`
- Test: `src/test/java/teralizer/processing/dependencies/MavenSurefireFloorTest.java`

`addJacocoPlugin` and `addPitestPlugin` both skip injection when the project declares the
plugin itself, and only surefire had a floor. All 6 projects whose `INITIAL` coverage report is
absent declare `jacoco-maven-plugin` themselves at 0.7.1 to 0.7.6, from 2014 to 2016, while
their tests and report collection succeed. So this is the same family as the argLine defect and
covers 2 pitest plus up to 6 JaCoCo exclusions.

- [x] Generalize the surefire floor to `jacoco-maven-plugin` and `pitest-maven`, flooring each to
      the version the pipeline itself injects, and leave newer pins alone.
  Verification: `./gradlew test --tests '*MavenSurefireFloorTest*'`
  Expected: a JaCoCo pin of 0.7.2.201409121644 and a pitest pin of 0.30 are floored; a pitest pin
  of 1.19.5 is untouched.

- [x] Compare `major.minor.patch` and ignore further components in `parseNumericVersion`, which
      previously refused any version with more than three components and therefore never
      compared a JaCoCo pin at all.
  Verification: `./gradlew test --tests '*MavenSurefireFloorTest*'`
  Expected: the JaCoCo case above changes the document, where before the fix it did not.

- [x] Commit.
  Message: `fix(pipeline): floor the pitest plugin version`

### Task 3: Accept jqwik test identifiers in mutation reports

**Files:**
- Modify: `src/main/java/teralizer/processing/task/PitDataCollectionTask.java`
- Test: `src/test/java/teralizer/processing/task/PitDataCollectionTaskTest.java`

An engine-only identifier such as `com.example.FooTest.[engine:jqwik]` carries no method, so it
cannot be linked to a generalization even once parsed. Tolerance is therefore the sound behavior,
matching how a parsable but unattributable name is already handled.

- [x] Keep a record unlinked rather than throwing when a PIT name cannot be decomposed.
  Verification: `./gradlew test --tests '*PitDataCollectionTaskTest*'`
  Expected: the engine-only name resolves to null ids without an exception, and the existing
  parsable-name cases are unchanged.

- [x] Commit.
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

A failed Maven command carries only an exit code and the paths of its captured stdout and
stderr, so the discriminating text is read back from disk. `TestExecutionTask` already does this
for failed test runs, so the classifier follows an existing convention rather than inventing one.

- [x] Add `MINION_DIED`, `SUITE_NOT_GREEN`, `NO_TESTS_FOUND`, `PLUGIN_UNUSABLE`, and
      `REPORT_ABSENT`, and classify coverage and mutation command failures into them by reading
      the captured output. Unrecognized output keeps the existing fallback, so the change adds
      resolution without hiding anything.
  Verification: `./gradlew test --tests '*TaskDiagnosticWriterTest*' --tests '*TaskDiagnosticClassifierCommandTest*'`
  Expected: each code round-trips through the writer; a dead minion yields `MINION_DIED`, an
  unusable plugin `PLUGIN_UNUSABLE`, a failing unmutated suite `SUITE_NOT_GREEN`, invisible tests
  `NO_TESTS_FOUND`, and unclassified output still `LISTENER_BUG`.

- [x] Map the new codes to reduction cause rows and delete the `_fallback_cause` guess of
      "PIT reports not found", so an unmapped reduction failure surfaces as `UNCODED` instead
      of being silently mislabeled.
  Verification: `uv run --directory analysis pytest tests/eval/test_taxonomy.py tests/eval/test_funnel.py`
  Expected: taxonomy tests cover each new code; the funnel still leaves no project `UNCODED`
  on the existing corpus.

- [x] Commit.
  Message: `feat(diagnostics): type coverage and mutation failures`

### Task 6: Gate

- [x] Build and test the pipeline.
  Run: `./gradlew build`
  Expected: green.

- [x] Run the analysis gate.
  Run: `uv run --directory analysis pytest tests/eval` then `uv run --directory analysis ruff check .` then `uv run --directory analysis ty check .`
  Expected: `test_funnel.py`, `test_rq6_causes.py`, and `test_taxonomy.py` green; ruff and ty
  clean. `test_smoke.py::test_registered_reports_build` remains failing on the `minDouble`
  fixture probe in `jarvis_scoreboard.py`, which is unrelated in-flight RQ0 work.

- [x] Commit.
  Message: `chore(pipeline): gate the reduction-path fixes`
