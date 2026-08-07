---
title: Generated Properties Need Java 8
type: plan
status: draft
created: 2026-08-06
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Generated Properties Need Java 8

A generated property uses lambdas and method references. Its nested `FirstValueArbitrary`,
`TestParametersSupplier`, and `JqwikValueRecorder` classes all contain them. A project that pins
`maven.compiler.source` below 1.8 cannot compile the property, and the compiler reports
`method references are not supported in -source 1.7` and
`lambda expressions are not supported in -source 1.7`.

`BUILD_PROJECT_GENERALIZED` then fails for the whole project. Every generalization the project
created is stranded: `generalization.is_included` stays true, no `junit_test_report` row appears, no
lifecycle flag advances, and no reason code records what happened. The generalizations cannot inflate
a yield number, because they never reach `generated_filter_passed`. They are invisible in the funnel
instead.

`GeneratedTestValidator.compilationErrors` does not catch this. It compiles each generalized test
with its own settings rather than the source level the project declares, so it reports no error and
the per-generalization quarantine passes the file through to a build that then fails.

## Measurements

Taken on `postgres_reporeapers_rq6_v2`, which processed 1,048 projects, and confirmed on v4 and v6.

| Quantity | Value |
|---|---|
| Checked-out projects pinning a source level below 8 | 785 of 1,172 |
| Projects reaching `BUILD_PROJECT_GENERALIZED` | 131 |
| Of those, projects whose generalized build failed | 7 (5.3%) |
| Included generalizations stranded by those failures | 26 |

The 79% figure does not carry through to the funnel. Most projects that pin an old source level fail
before the generalization stages, and the scaffolding that needs Java 8 is only emitted for some
plans. v4 lost 3 of 44 projects this way and v6 lost 1 of 13, which is the same rate.

The behavior is identical in v2, v4, and v6, so a corpus stays internally consistent. RQ6 can report
the limitation as measured on its own corpus.

## Two candidate fixes

- **Make the loss countable.** Give `GeneratedTestValidator` the source level the project declares.
  Affected generalizations are then quarantined one at a time as `UNCOMPILABLE_GENERALIZED_TEST`, and
  a project keeps the generalizations that do compile instead of losing all of them. This reports the
  limitation accurately. It does not remove it.
- **Floor the derived source level.** Raise `maven.compiler.source` and `maven.compiler.target` to
  1.8 in the derived POM only, the way the pipeline already floors surefire. This recovers the yield
  rather than counting the loss. It also compiles the measured artifact at a different level than the
  project's own build, which the thesis must then state, and a project can fail to compile at 8.

The first fix is a reporting correction and is safe. The second changes what the corpus measures, so
it needs its own gate and a decision about provenance.

## Assumptions & contingencies

- **A limitation, not only a defect.** Teralizer emits Java 8 source. A project that cannot compile
  Java 8 cannot run a generalized property, whatever the pipeline does about attribution. The
  reporting fix makes the boundary visible. Only a rewrite of the generated code to Java 7 would
  remove it, which is not worth the constraint it would put on every future template.
- **Do not interrupt a running corpus for this.** The rate is near 5% of the projects that reach
  Stage 4, and the behavior is uniform across collections, so a run that is under way stays citable.
