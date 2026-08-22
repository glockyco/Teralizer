## 1. Establish the contract input boundary

- [x] 1.1 Inventory every file whose behavior or bytes can change the synthetic PostgreSQL contract: its workflow, base and derived DDL, corpus registry and preparation boundary, database configuration, registry and PostgreSQL shell helpers, fixture, analysis lock, and Nix execution declarations. Record one reason for each included path or directory.
- [x] 1.2 Compare the inventory with the proposed push and pull-request path filters. Add every missing owner and remove paths that cannot affect the contract. Verify with one database-bound positive example and one renderer-only negative example.
- [x] 1.3 Reconcile the existing uncommitted workflow repair into the final split. Preserve its connection-routing correction where applicable, but leave no temporary in-place PostgreSQL setup in the default analysis job.

## 2. Keep default analysis validation database-free

- [x] 2.1 Remove the PostgreSQL service, database environment, and synthetic fixture preparation from the default `analysis` job while preserving checkout, uv and Nix setup, commit hooks, strict OpenSpec validation, and marker-free coverage tests.
- [x] 2.2 Correct the workflow comments so they distinguish the DB-free marker selection from the separately owned synthetic PostgreSQL contract. Do not claim that skipped database tests passed.
- [x] 2.3 Run the default analysis commands without PostgreSQL available and confirm hooks, OpenSpec validation, and marker-free tests execute and report failures independently of database setup.

## 3. Add the focused PostgreSQL contract workflow

- [x] 3.1 Add a separate workflow with native push and pull-request path filters for the complete task 1 inventory and a `workflow_dispatch` entry. Keep the database-free analysis workflow universal; do not add a third-party change-detection action or mark the path-scoped workflow as universally required.
- [x] 3.2 Configure one ephemeral PostgreSQL 17.1 service with a health check and explicit Teralizer owner and report-role connection settings. Do not mount, download, or reference a production corpus, package, report output, author path, or author credential.
- [x] 3.3 Resolve semantic corpus id `controlled` through `scripts/corpus-registry` before database creation. Use the resolved physical name only as a local observation; do not embed a registered physical database literal in the workflow.
- [x] 3.4 Route every SQL operation through `scripts/lib/psql.sh`. Give the single `createdb` invocation explicit host, port, owner, and password coordinates without adding a generic lifecycle helper or duplicate `PGHOST` configuration.
- [x] 3.5 Apply the canonical base DDL and tracked 13-project fixture, run preparation twice, and verify identical project counts and derived-view revisions. Fail at the first violated checkpoint with a diagnostic naming the operation.
- [x] 3.6 Connect with the configured report role, prove it can read the synthetic project rows, and invert the expected write command so CI fails if PostgreSQL permits the mutation.

## 4. Prove the focused behavior locally

- [x] 4.1 Validate both workflow files with `actionlint` and confirm their event declarations contain no unexpected broad trigger or undeclared third-party action.
- [x] 4.2 Start an isolated PostgreSQL 17.1 service on a verified unused port and execute the exact focused contract commands. Confirm 13 projects, two successful preparations, the current derived-view revision, readable views, report-role read success, and database-enforced write refusal.
- [x] 4.3 Run the same contract with an unreachable configured host and confirm the diagnostic names that target rather than a default Unix socket. Remove the isolated service after both controls.
- [x] 4.4 Review the workflow path set against the completed task 1 inventory again. Record why a DDL change triggers the contract and a renderer-only change does not.

## 5. Run repository gates

- [x] 5.1 Run `nix develop --command lefthook run pre-commit --all-files` and require every formatting, lint, type, and repository-boundary hook to pass.
- [x] 5.2 Run the exact DB-free CI commands: the Nix OpenSpec check and `uv run --directory analysis python -m pytest -m "not db" --cov=src --cov-report=term-missing`.
- [x] 5.3 Run `openspec validate separate-postgres-contract-ci --strict` and `openspec validate --all --strict`.
- [x] 5.4 Review the final diff and confirm it changes only CI validation and this change's planning state. Do not add a database framework, production payload, corpus result, release behavior, or unrelated lifecycle migration.

## 6. Verify GitHub-hosted execution

- [x] 6.1 Commit the coherent workflow and contract change with one causal subject, push it, and inspect the actual GitHub Actions run rather than treating local service success as proof of hosted networking.
- [x] 6.2 Confirm the universal analysis job completes without a PostgreSQL service and reports hooks, strict OpenSpec validation, and marker-free tests independently.
- [x] 6.3 Confirm the workflow-changing push triggers the focused PostgreSQL contract and that its synthetic lifecycle, idempotence, read, and write-refusal checkpoints pass on the GitHub PostgreSQL service.
- [x] 6.4 Confirm manual dispatch is available for the focused workflow and that no branch rule requires its absent status on unrelated changes. Record the successful run URLs and archive only after every task is complete.

Hosted evidence: [universal analysis and build](https://github.com/glockyco/Teralizer/actions/runs/32591179873) and [PostgreSQL contract](https://github.com/glockyco/Teralizer/actions/runs/32591179881). The focused workflow is active with manual dispatch. The repository has no rulesets, and `master` has no branch protection.
