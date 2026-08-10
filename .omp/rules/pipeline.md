---
description: Java processing-pipeline conventions and invariants for Teralizer
globs:
  - "src/main/java/teralizer/**/*.java"
  - "src/test/java/teralizer/**/*.java"
  - "src/main/resources/reference.conf"
  - "src/main/resources/db/*.sql"
---

# Pipeline conventions

- Architecture map: `docs/architecture.md` (stage list, phase model, key components). Read it
  before changing stage ordering, phase scheduling, or `ProcessingStage`.
- Three independently-toggled phases run in order: generation, generalization, reduction. Reduction
  is last on purpose, so a PIT/JaCoCo failure can never drop generalizations. `PipelinePlanner`
  drives the clear -> check-preconditions -> schedule -> drain loop per requested phase.
- Stage numbering lives in three places that MUST stay in lockstep across all stages: the
  `ProcessingStage` enum, `stage_order()` in `src/main/resources/db/create-views.sql`, and
  `analysis/src/teralizer/stages.py`. Renumbering one without the others silently breaks
  runtime-by-stage analysis.
- Excluding an entity: `docs/exclusion-model.md` lists the five existing mechanisms and where each
  records its decision. Adding a sixth without a home in the analysis makes it disappear into
  whichever bucket a predicate happens to match. `AbstractTask` clears `is_included` only for the
  record attached to the failing task, so project-scoped failures clear nothing.
- jOOQ: a DDL *column* change needs `scripts/regenerate-jooq.sh`; adding a `ProcessingStage` enum
  constant does NOT (runtime `EnumConverter` keyed on the class).
- `./gradlew run` exits 0 even when a pipeline task fails. The authoritative success check is the
  `task` table `WHERE status = 'FAILED'` (all sub-ids null = structural breakage; otherwise
  per-assertion attrition).
- Verifying pipeline changes: `skill://verifying-pipeline-changes` (golden-corpus workflow).
