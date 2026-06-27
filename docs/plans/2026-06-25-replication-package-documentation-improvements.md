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

## Not in scope

- **`generate-replication-configs.sh`**: configs are pre-generated and tracked
  in git; evaluators never run it.
