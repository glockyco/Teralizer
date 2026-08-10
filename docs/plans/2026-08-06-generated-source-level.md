---
title: Generated Code Compiles Before Java 8
type: plan
status: implemented
created: 2026-08-06
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Generated Code Compiles Before Java 8

A project compiles the generalized tests with its own build, and a pre-v6 measurement counted 785 of
1,172 corpus projects declaring a source level below 1.8. Those figures are historical and must not be
cited as current corpus counts. The emitted code therefore uses no lambda and no method reference.
Anonymous classes and plain statements carry the same meaning at every source level, and the code the
builders emit already uses them.

The constraint is only about language syntax. A project pins `maven.compiler.source`, but the JDK that
runs the build is the pipeline's own, so a Java 8 library type such as `java.util.Optional` resolves
and jqwik runs. Only the syntax has to be older.

`src/test/java/teralizer/spoon/codegen/GeneratedSourceLevelTest.java` reads every template and fails
on a lambda or a method reference. That guard exists because the failure is expensive: a single
lambda can fail `BUILD_PROJECT_GENERALIZED` for the whole project. Generalizations that fail to
compile are quarantined by `ProjectBuildTask` and recorded with reason code
`UNCOMPILABLE_GENERALIZED_TEST`. See [the exclusion model](../exclusion-model.md) for the pipeline's
quarantine and exclusion behavior.

## What the constraint costs when it is broken

Historical pre-v6 measurement on `github_com_tolgamyth_Common-Java-Utilities`, which declares source
1.7, before and after the templates dropped their four lambda and method-reference expressions. These
figures are not current corpus measurements.

| Generalizations | total | included | executed | reports | filter-passed | usable |
|---|---|---|---|---|---|---|
| Four lambdas present | 8 | 8 | 0 | 0 | 0 | 0 |
| No lambdas | 8 | 6 | 8 | 8 | 6 | 6 |

A historical v2 measurement on `postgres_reporeapers_rq6_v2` found that 7 of 131 projects that
reached the generalized build failed it this way, stranding 26 included generalizations. These figures
must not be cited for RQ6.

## Replacing part of a collection

The four rewritten expressions compute the same values as the originals: an `Optional` that holds a
`TooManyFilterMissesException` still reports filter exhaustion, and an absent `Optional` still yields
the empty string. A project whose build already succeeded therefore produces identical results before
and after, which is what makes a partial re-collection sound.

The runner supports it. `--reset-db` is omitted to resume, a project is skipped when
`DATA_DIR/done/project-N` exists, and every table references `project` with `ON DELETE CASCADE`. So a
single project is re-collected by deleting its `project` row, its done marker, and its ledger line,
after which the run continues from where it stopped.

This applies only to a change that provably preserves behavior for the projects it does not fix. A
change that alters what the pipeline decides needs a full re-collection, because the corpus would
otherwise mix two measurements.
