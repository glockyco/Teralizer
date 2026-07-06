---
title: "Replication Package Documentation Improvements"
type: plan
status: active
created: 2026-06-25
parent: 2026-06-26-teralizer-overview
---

Verifiable replication-package documentation for ACM artifact eval: three
verification workflows, database/variant clarity, and notebook-to-paper mapping
in the root `README.md` (the separate `replication/README.md` was consolidated
into the root file in commit `fbb24921`).

## Tasks

- [x] Add the three Verification Workflows to the root README (Inspect /
  Verify Analysis / Verify Pipeline), with archives-needed and expected-output
  per workflow. *(README §Verification Workflows, L83–150.)*
- [x] Make container naming consistent — `docker compose exec -T postgres`
  everywhere, no `docker exec postgres-replication`. *(Verified clean in
  `replication/scripts/`.)*
- [x] Add the notebook-to-paper-RQ mapping table. *(README §Analysis
  Notebooks, L156–162.)*
- [x] Replace the ad-hoc "10GB" quick-start figure with a per-workflow disk
  space table. *(`REQUIREMENTS.md` — RAM/disk table + Disk Space by Workflow.)*
- [x] Rename the full-reproduction section for clarity. *(Now §Complete
  Reproduction, README L166.)*
- [x] Fix `verify-outputs.sh` exit codes — exit 1 when `original` vs `verify`
  differs (should be identical), exit 0 for `original` vs `replicate`
  (differences expected). *(`verify-outputs.sh` L189–194.)*
- [ ] Add a `DATASET_VARIANT` clarification table to the root README showing
  that the variant controls both database selection and output directory
  (`original` / `verify` / `replicate`), and expand `replication/.env.example`
  comments to match.
- [ ] Add a Database Structure section to the root README explaining the four
  databases (`postgres_dev`, `postgres_test`, `postgres_dev_replication`,
  `postgres_test_replication`) and that the `*_replication` databases are
  empty schemas populated by Workflow 3.
- [ ] Add an evaluator quick-link near the top of the root README pointing to
  the Verification Workflows section.
- [ ] Improve `run-notebooks.sh` failure messages — when a notebook fails,
  print the debug command to re-run it in isolation.
- [ ] Adopt `scripts/lib/run-supervisor.sh` in `replication/scripts/run.sh`
  (local mode): group-kill traps, per-project wall cap (30 min default, env
  knob to disable), STOP-file pause, done-markers, and a `status.tsv` ledger.
  Straight lift from `scripts/run-reporeapers-rerun.sh`. An unattended
  replicator crash at project 900 of 1161 must resume, not restart.
- [ ] Give Docker mode the same semantics with the container as the kill
  unit: named container per config, cap enforced by watchdog + `docker stop`,
  traps stop the container instead of a process group.
- [ ] Correct the runtime estimates in the README and the `run.sh` header.
  Measured on an M2: 23.6 h uncapped for the extended dataset (not "~15
  hours"), ~12 h with the 30-min cap. Document that a handful of projects
  legitimately run to the cap and are ledgered as exit 124 — expected
  behavior, not a hang. Consistency pass over INSTALL.md and
  REQUIREMENTS.md for the same numbers.
- [ ] State the capped methodology in §Complete Reproduction: the paper's
  extended numbers come from capped runs, and the artifact reproduces with
  the same rule.

## Not in scope

- **`generate-replication-configs.sh`**: configs are pre-generated and tracked
  in git; evaluators never run it.
