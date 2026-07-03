---
title: Telemetry Harness as Precompiled Support Artifact
type: spec
status: draft
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
---

# Telemetry Harness as Precompiled Support Artifact

**One concern:** every generated `_*_Generalized_*_Test.java` inlines the ~150-line telemetry harness (`src/main/resources/templates/jqwik-value-recorder.vm`, 241 lines), duplicating it ~12× per project and coupling the generated file's language level to the harness's syntax; extract it into a small precompiled support artifact the generated test calls into.

## Why

- **Deletes a defect class.** The `-source 1.5/1.6/1.7` build failures (10 spike projects) exist only because harness lambdas/method references land inside files compiled at the target project's level. With the harness precompiled at Teralizer's level, the generated file contains just the cloned test body — its language level then genuinely matches the original test's. The test-source floor (`2026-07-03-generalized-validation-repair` Task 1) becomes unnecessary for the harness's sake and can be retired if nothing else needs it.
- **Shrinks generated files** to the cloned test body + supplier, improving reviewability of generated output and cutting per-file compile cost.
- **One harness version** to maintain instead of a template macro-expanded into thousands of files.

## Shape

A tiny jar (`teralizer-jqwik-support`) containing the recorder (`JqwikValueRecorder`, diagnostics sidecar writer, reset lifecycle hooks). Delivery seam: the dependency managers already inject jqwik itself (`Configuration.JQWIK_DEPENDENCY` in `MavenDependencyManager`/`GradleDependencyManager`); the support jar rides the same seam — either as a locally-installed artifact (`mvn install:install-file` into the pipeline's local repo at build time) or a system-scoped dependency pointing into Teralizer's build output. Resolution strategy is the main open design question (offline corpora must keep working; no network fetch).

## Constraints

- Generated-test behavior is unchanged: same sidecar paths (`jqwik-data/executions/<executionId>/…`), same env-var contract (`TERALIZER_JQWIK_DIAGNOSTICS_MODE`, `TERALIZER_JQWIK_EXECUTION_ID`), same `@BeforeProperty` reset semantics — `JunitDataCollectionTask.importJqwikDiagnostics` must not notice the difference.
- The support jar compiles once at Teralizer's level (Java 8); target projects only need it on the *test* classpath.
- Corpus runs are hermetic: the artifact comes from the local build, never a remote repository.

## Acceptance

- Generated files contain no harness code (only the test body, supplier, and `@BeforeProperty` hook calling into the support artifact).
- A spike project that previously built only via the test-source floor builds and records diagnostics identically (byte-identical sidecar schema).
- Census + `jqwik_property_execution` coverage unchanged on a spike re-run.
