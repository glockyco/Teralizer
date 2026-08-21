---
name: verifying-pipeline-changes
description: Use when verifying a Teralizer pipeline change (codegen, SPF/JPF listener, filters, widening license, build-file managers, report collection), adding a fixture for a new pipeline defect, updating verification goldens, or when tempted to launch a full spike/corpus re-run to check a fix.
---

# Verifying Pipeline Changes

## Overview

Verification is tiered. Full spike and corpus runs are evaluation events, never debugging
loops. Goldens are observed truth from real runs: on a mismatch, investigate instead of editing
the golden to match broken output.

## Choosing the tier

| Situation | Do this | Cost |
|---|---|---|
| Iterating on one behavior | `scripts/run-verification-corpus.sh --only <fixture-name>` (resets the scratch database and runs that fixture. Inspect the database with ad hoc SQL because the full golden check expects the whole corpus) | ~45 s |
| Change is "done" | `scripts/verify-pipeline.sh` (build + full fixture corpus + golden check), ONCE | ~15 min (16 fixtures, ~47 s each) |
| Change touches real-world seams (surefire versions, report formats, big suites, source levels) | sentinel subset: `REPOREAPERS_SCRATCH_DB=scratch_sentinel_verify REPOREAPERS_DATA_DIR=data/sentinel-verify REPOREAPERS_CONFIG_DIR=project-configs/sentinel scripts/run-reporeapers-rerun.sh --reset-db`. Expected census sits in the config header comments. Drop the scratch DB and data dir afterwards | ~10 min |
| Corpus-level claims for the paper | full spike (`project-configs/fusion-spike/`), coordinated with the operator | ~1 h |

Never verify against kouchat, gedcom4j, xenqtt, uaicriteria (60s-ceiling jitter) or sparkey
(native mmap flake): run-to-run nondeterminism, evaluation corpus only.

## Fixture corpus mechanics

- Fixtures: `verification/fixtures/<name>/` — minimal Maven (JUnit 4.13.2, source/target 1.8
  unless the fixture deliberately pins otherwise), one CUT per behavior family, deterministic,
  fast. Config: `project-configs/verification/fixture-<name>.conf`. Profile:
  `project-configs/verification.conf`.
- Goldens: `verification/golden/<name>.tsv` (per-generalization: inclusion, exclusion label,
  `output_spec_class`, `diagnostic_kind`, `tries`, `distinct_tuples`). Checker:
  `scripts/check-verification-corpus.sh` (readable diff, non-zero exit on drift).
- Recording a golden: run the fixture, inspect the DB by hand, record what you OBSERVED. If the
  observation contradicts the intended design: STOP and escalate with evidence. It is either a
  product bug or a wrong expectation, and twice it has been the expectation.
- New pipeline defect ⇒ new fixture (or a new arm in an existing family) reproducing it,
  landing with the fix so the golden pins it.
- A behavior fix that changes an outcome MUST flip a golden. Update the golden in the same
  commit and say why in the commit body.

## Common mistakes

| Mistake | Reality |
|---|---|
| "Quickly re-run the spike to check my fix" | An hour of nondeterministic feedback where a fixture answers in 45 s. Spike runs are for evaluation. |
| Editing a golden so the check passes | Goldens pin observed truth. A surprising diff is a finding. Investigate first. |
| Verifying only at unit level for generator/engine seams | jqwik engine seams (edge-case injection, generator overloads) bypass naive wrappers — only the full-pipeline fixture catches them. |
| Reusing a protected DB for an experiment | Scratch databases (`scratch_<purpose>`) are created and dropped by runners. Protected DBs are listed in AGENTS.md. |
| Deleting a timed-out/failed project and rerunning it for a cleaner number | Runtime limits are real filters. The first run IS the observation. Record it. Selective reruns bias every census. Never rerun to pick the better of two numbers. |
