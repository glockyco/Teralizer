## Why

The published Zenodo artifact describes the retired notebook and two-database workflow, while the current repository uses registered reports, semantic corpus ids, and four published corpora. A new release must be independently usable after standard archive extraction, internally consistent, and demonstrably usable under the artifact-review criteria applied by TOSEM, ICSE, FSE, and ASE rather than relying on author-only publication machinery or unverified documentation.

## What Changes

- Build one immutable release set from the verified four-corpus package, complete registered report run, every declared database and file input, committed source plus required submodules, workflow-specific project snapshots, licenses, citation metadata, and an explicit release manifest. Stage and validate the whole set before replacing or uploading anything.
- Preserve the complete JARVIS RQ0 evidence chain: scorecard and census corpus dumps, fixture revisions and configs, run status/completion evidence, normalized value facts, their selected raw value logs, CUT-PVC aggregate and raw captures, report outputs, provenance, and the JARVIS publication reference. Distinguish frozen-evidence inspection from reduced or full collection.
- Keep source, runtime support, registered results, compact inputs, and small backing evidence together in `core`. Publish the large corpus and project families as fine-grained, self-identifying components so each workflow downloads only the data it needs. Core extracts the `teralizer/` workspace tree, each optional archive extracts one unique immutable component subtree, and workflow preflight verifies identities, checksums, and dependencies without merging payload trees.
- Publish exact, human-readable requirements and one authoritative reviewer route. A replicator can learn download, unpacked, peak-disk, memory, architecture, software, network, and runtime requirements without running project code.
- Provide a clean, pinned reviewer environment with a setup and smoke path that completes within 30 minutes on the declared baseline, a bounded results-reproduction path, and reduced and full data-collection paths with progress and expected outcomes.
- Map every supported paper claim to its inputs, command, output, expected comparison, tolerance, and runtime, and name claims that the artifact does not support. Verification covers report text and figure evidence as well as tables and CSV data.
- Embed release provenance so report regeneration works after packaging removes Git metadata while preserving the exact producing source revisions required by the accepted provenance contract.
- Document database and result schemas, corpus selection and provenance, third-party project licenses and attribution, redistribution decisions, and the security boundary for executing untrusted project builds.
- Create a new immutable Zenodo version with version-specific DOI and synchronized landing-page, citation, license, checksum, and paper metadata. The existing published version remains unchanged.
- Keep evaluation-host export and other author-only operations outside the reviewer path. This change consumes the complete corpus package produced by `consolidate-evaluation-databases`. It does not redesign corpus export.
- **BREAKING**: release membership is declared by the release manifest, not by output-directory wildcards or whatever archives happen to exist. Optional archives use verified component identities instead of a shared-directory-nonempty shortcut.

## Capabilities

### New Capabilities

- `replication/artifact-release`: immutable release-set composition, fine-grained archive identity and integrity, exact workflow download requirements, collision-free component extraction, and clean release validation.
- `replication/artifact-evaluation`: supported reviewer environments, bounded smoke and reproduction workflows, claims-to-evidence verification, clean-machine evidence, and documented reuse and security boundaries.

### Modified Capabilities

- `reporting/artifact-provenance`: report generation from an immutable release without a Git checkout must resolve producing revisions from verified embedded release provenance while preserving the existing per-source attribution contract.

## Impact

- Packaging and replication entry points under `scripts/packaging/` and `replication/`, including fine-grained archive assembly, component verification and extraction, workflow preflight, setup, report execution, output comparison, and cleanup.
- Release metadata and reviewer documentation: `README.md`, `REQUIREMENTS.md`, `INSTALL.md`, `STATUS.md`, `LICENSE*`, `CITATION.cff`, third-party notices, the accepted paper or stable paper link, and Zenodo metadata.
- Analysis provenance resolution and report verification under `analysis/src/teralizer/eval/` plus focused tests.
- Docker/Compose definitions, dependency locks, image provenance, supported-platform declarations, and release CI.
- Corpus and report manifests from `consolidate-evaluation-databases` and `make-report-runs-explicit` remain the authorities for their respective contents. This change resolves all seven current non-database report inputs (`project-source-facts.json`, `jarvis-value-facts.json`, `cut_values.tsv`, JARVIS completion evidence, `reporeapers-reconstruction-audit.json`, `reporeapers-reconstruction-inventory.json`, and `reporeapers-output-directories-population.json`), adds a release-level manifest, and does not duplicate either registry. The release ledger also covers every collected source named by the reconstruction inventory without treating a machine path as evidence identity.
- The existing Zenodo DOI `10.5281/zenodo.18242626` remains the immutable first version. The new artifact receives a new version DOI under concept DOI `10.5281/zenodo.17950380`.
