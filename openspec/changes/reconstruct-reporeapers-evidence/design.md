## Context

See `proposal.md` for motivation. The Air preserves eight RepoReapers database generations, 1,161 project logs, project checkouts, per-project run roots, configurations, generated state, and historical producer revisions. The active workstation has the canonical version 7 database.

The reconstruction must preserve measurement integrity. It cannot execute Teralizer, a corpus runner, a project build, or a failed task. It can read preserved evidence, restore a dump for read-only inspection, and create derived audit records.

Current reports can consume committed audit inputs with provenance. They must continue to resolve the real-world corpus to `postgres_reporeapers_rq6_v7`.

## Goals / Non-Goals

**Goals:**

- Establish a verified inventory of collected RepoReapers evidence.
- Recover the four named evidence questions from collected artifacts only.
- Preserve entity-level decisions and unresolved cases.
- Publish supported results through versioned audit inputs.
- Make every unreconstructable claim an explicit evidence gap.
- Prevent accidental pipeline execution during reconstruction.

**Non-Goals:**

- Do not rerun the corpus, a project, a task, or Teralizer.
- Do not repair or complete an old run.
- Do not change the version 7 corpus or its base tables.
- Do not combine old database generations with version 7.
- Do not treat historical databases as current corpus aliases.
- Do not commit raw dumps, logs, project trees, or generated run roots.
- Do not update thesis prose in this change.

## Decisions

### 1. Use three evidence layers with different authority

The reconstruction uses these layers:

| Layer | Evidence | Permitted use |
|---|---|---|
| Canonical | Version 7 database and its registered inputs | Current RepoReapers populations and quantities |
| Historical | Older dumps, old logs, old configurations, and producer revisions | Historical claim recovery and interpretation |
| Project state | Preserved source checkouts, run roots, command output, and generated files | Entity adjudication and failure diagnosis |

A historical result stays historical even when the thesis retains it. It cannot change a version 7 denominator.

**Alternative:** Register each historical database as a corpus. Rejected because it creates competing RepoReapers identities and weakens the version 7 boundary.

### 2. Inventory before interpretation

Create an acquisition manifest before claim analysis. The manifest uses logical source keys and SHA-256 identities. It records the Air location separately from evidence identity.

The inventory covers:

- all RepoReapers dump files and `facts.tsv` records;
- `project-1.txt` through `project-1161.txt`;
- every preserved RepoReapers project checkout;
- every per-project `command-data` and `teralizer-data` tree;
- evaluation project configurations;
- relevant generated tests and reports;
- producer and project Git revisions;
- existing analysis outputs that cite these sources.

The inventory compares declared project counts, observed path sets, and recorded revisions. Missing items remain missing. The inventory does not regenerate them.

**Alternative:** Copy the complete Air tree into the repository. Rejected because the tree contains large generated and protected measurement state.

### 3. Keep acquisition and analysis read-only

Use SSH or SCP only to read evidence from the Air. Verify copied files against their source digests. Do not edit remote files or run commands inside preserved project roots.

If an old dump needs SQL inspection, copy and verify the dump first. Restore it through database lifecycle tooling into an isolated scratch database. Use a read-only role and record the dump digest. Drop the scratch database through its owning lifecycle command after the derived audit input is verified.

A historical scratch database is an inspection surface. It is not report evidence by itself. The committed audit input records the dump identity and the derived decisions.

### 4. Add a reconstruction-only command surface

The reconstruction command accepts manifests and collected files. It does not accept project-runner configuration or pipeline stage selection. It has no code path to Gradle, Maven, JPF, PIT, Teralizer tasks, corpus runners, or detached runs.

The command performs only these actions:

- validate source identities;
- extract database populations;
- assemble source-review packets;
- validate manual decisions;
- reconcile entity and summary totals;
- emit normalized audit inputs;
- emit explicit evidence-gap records.

Tests use synthetic fixtures. They do not invoke a real project or corpus.

### 5. Define a common reconstruction record

Each claim record contains:

- schema version and claim key;
- reconstruction status;
- canonical or historical population identity;
- population definition and denominator;
- evidence-manifest digest;
- producer revision;
- method and classification rules;
- entity decisions and source references;
- resolved, unresolved, and incompatible counts;
- reviewer state and rationale;
- summary checks;
- evidence-gap reason where applicable.

Entity joins use corpus identity, project root, project revision, and stable test or assertion identity. Database surrogate IDs remain local locators only.

The status vocabulary is closed: `supported`, `partially-supported`, `contradicted`, and `evidence-gap`.

### 6. Reconstruct each question with a specific method

| Question | Population | Primary evidence | Decision method |
|---|---|---|---|
| `NoAssertions` | Version 7 eligible `NO_ASSERTIONS` rejections | Test rows, exact source revision, project source, existing assertion records | Source-review packets and manual labels for genuine absence, reachable helper assertion, unsupported oracle, or unresolved evidence |
| Assertion-to-MUT | Version 7 assertion-resolution observations | `mut_resolution_observation`, filter results, assertion source, candidate declarations | Full mechanism partition, operational definition of insufficient evidence, and manual review of weak or unresolved strata |
| Output directories | Version 7 affected projects | Project path fields, task records, command logs, build files, and preserved output trees | Confirm whether a valid artifact existed outside the searched default path at failure time |
| Doubled timeouts | Historical reported population of 89 timed-out projects | Historical dumps, configurations, project logs, run identities, and producer revision | Recover an exact paired baseline and doubled-timeout transition, or record an evidence gap |

For `NoAssertions`, automated extraction may prepare review packets but cannot assign a semantic label from a text match alone. If full adjudication is infeasible, publish the reviewed sample, sampling method, confidence interval, and unresolved population. Do not present an estimated count as an exact corpus count.

For assertion-to-MUT evidence, preserve existing confidence tiers. Do not collapse a weak resolved mapping into a proven mapping. Define “insufficient specification evidence” before reviewing entities.

For output directories, require proof that the artifact existed at failure time. A later build or a present build configuration is insufficient.

For doubled timeouts, version-number adjacency does not prove a pair. Require matching project revisions, timeout configuration, producer revision, and paired outcomes.

### 7. Separate claim recovery from report publication

The reconstruction produces committed audit inputs under the existing report-input model. Registered reports read version 7 and the audit input. They do not connect to the Air or historical scratch databases.

Each report validates:

- audit schema version;
- manifest identity;
- version 7 population compatibility where required;
- resolved plus unresolved reconciliation;
- claim status;
- summary totals against entity decisions.

An evidence gap is a publishable result. It removes the unsupported number and names the checked evidence boundary.

### 8. Preserve reviewable checkpoints

Implement and commit the work in these units:

1. evidence inventory and identity validation;
2. common reconstruction schema and validators;
3. one reconstruction module per evidence question;
4. normalized audit inputs and report integration;
5. final cross-claim reconciliation.

Each unit uses focused fixtures and analysis checks. No checkpoint runs the pipeline or a project.

## Risks / Trade-offs

- **An Air path changes after inventory.** The digest and logical key preserve identity. Reacquisition must match the recorded digest.
- **A historical dump schema differs from current bindings.** Use schema-specific read-only queries. Do not migrate measured base rows.
- **A source checkout does not match the recorded project revision.** Mark that entity incompatible and exclude it from resolved counts.
- **Manual labels are inconsistent.** Use written rules, reviewer status, and disagreement resolution. Preserve unresolved labels.
- **The `NoAssertions` population is too large for full review.** Publish a declared sample estimate and unresolved population, not an exact rate.
- **The timeout experiment cannot be paired.** Record an evidence gap. Do not rerun it.
- **Historical evidence appears to improve a version 7 result.** Keep it separate. Version 7 remains canonical.
- **A reconstruction helper can accidentally execute project code.** Keep runner and build dependencies out of the command surface and test this prohibition.
