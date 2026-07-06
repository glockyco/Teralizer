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
- [x] Adopt `scripts/lib/run-supervisor.sh` in `replication/scripts/run.sh`
  (local mode): group-kill traps, per-project wall cap (30 min default,
  `REPLICATION_PROJECT_TIMEOUT=0` disables it), STOP-file pause under the
  run-state dir, done-markers, and a `status.tsv` ledger with config name,
  exit code, and terminal-only log marker. An unattended replicator crash at
  project 900 of 1161 must resume, not restart.
- [x] Give Docker mode the same semantics with the container as the kill
  unit: named container per config, cap enforced by watchdog + `docker stop`,
  and active traps that stop the container before cleaning up compose-run.
- [x] Correct the runtime estimates in the README and the `run.sh` header.
  Measured on an M2: 23.6 h uncapped for the extended dataset, ~12 h with the
  30-min cap, and ~44 s median per project. A handful of projects legitimately
  run to the cap and are ledgered as exit 124, which is expected behavior, not
  a hang. INSTALL.md does not carry runtime estimates. REQUIREMENTS.md carries
  the same capped and uncapped numbers.
- [x] State the capped methodology in §Complete Reproduction: the paper's
  extended numbers come from capped runs, and the artifact reproduces with
  the same rule.

Static shell verification is the acceptance gate while the live corpus run owns
the machine. The E2E smoke stays deferred until that run finishes.

## Not in scope

- **`generate-replication-configs.sh`**: configs are pre-generated and tracked
  in git; evaluators never run it.
