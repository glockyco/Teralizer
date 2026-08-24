---
description: Java processing-pipeline conventions and invariants for Teralizer
globs:
  - "src/main/java/teralizer/**/*.java"
  - "src/test/java/teralizer/**/*.java"
  - "src/main/resources/reference.conf"
  - "src/main/resources/db/*.sql"
  - "analysis/src/teralizer/stages.py"
---

# Pipeline conventions

- Before changing stage ordering, phase scheduling, or `ProcessingStage`, read `PipelinePlanner`,
  the `ProcessingStage` enum, `src/main/resources/db/create-views.sql`, and the accepted
  `pipeline/cross-stage-contracts` capability.
- Three independently-toggled phases run in order: generation, generalization, reduction. Reduction
  is last on purpose, so a PIT/JaCoCo failure can never drop generalizations. `PipelinePlanner`
  drives the clear -> check-preconditions -> schedule -> drain loop per requested phase.
- Stage numbering lives in three places that MUST stay in lockstep across all stages: the
  `ProcessingStage` enum, `stage_order()` in `src/main/resources/db/create-views.sql`, and
  `analysis/src/teralizer/stages.py`. Renumbering one without the others silently breaks
  runtime-by-stage analysis.
- The accepted `reporting/exclusion-accounting` capability and
  `analysis/src/teralizer/eval/reports/rq6_causes.py` define the five exclusion mechanisms and where
  each records its decision. Adding a sixth without an explicit report bucket makes it disappear
  into whichever predicate matches. `AbstractTask` clears `is_included` only for the record attached
  to the failing task, so project-scoped failures clear nothing.
- jOOQ: a DDL *column* change needs `scripts/regenerate-jooq.sh`; adding a `ProcessingStage` enum
  constant does NOT (runtime `EnumConverter` keyed on the class).
- `./gradlew run` exits 0 even when a pipeline task fails. The authoritative success check is the
  `task` table `WHERE status = 'FAILED'` (all sub-ids null = structural breakage; otherwise
  per-assertion attrition).
- Verify one fixture with `scripts/run-verification-corpus.sh --only <fixture>`. Run
  `scripts/verify-pipeline.sh` once at the end of a related change wave.
