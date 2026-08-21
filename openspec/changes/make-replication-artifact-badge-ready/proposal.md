## Why

The published Zenodo artifact describes the retired notebook and two-database workflow, while the current repository uses registered reports, semantic corpus ids, and four published corpora. A new release must be independently installable, internally consistent, and demonstrably usable under the artifact-review criteria applied by TOSEM, ICSE, FSE, and ASE rather than relying on author-only publication machinery or unverified documentation.

## What Changes

- Build one immutable release set from the verified corpus package, report evidence, source snapshot, optional project/data components, licenses, citation metadata, and an explicit release manifest. Stage and validate the whole set before replacing or uploading anything.
- Make every downloadable archive self-identifying and safely composable. Installation verifies selected archive identities and checksums, merges all requested components without silently skipping a later archive, and rejects collisions or incomplete workflow inputs.
- Publish exact, human-readable requirements and one authoritative reviewer route. A replicator can learn download, unpacked, peak-disk, memory, architecture, software, network, and runtime requirements without running project code.
- Provide a clean, pinned reviewer environment with a setup and smoke path that completes within 30 minutes on the declared baseline, a bounded results-reproduction path, and reduced and full data-collection paths with progress and expected outcomes.
- Map every supported paper claim to its inputs, command, output, expected comparison, tolerance, and runtime, and name claims that the artifact does not support. Verification covers report text and figure evidence as well as tables and CSV data.
- Embed release provenance so report regeneration works after packaging removes Git metadata while preserving the exact producing source revisions required by the accepted provenance contract.
- Document database and result schemas, corpus selection and provenance, third-party project licenses and attribution, redistribution decisions, and the security boundary for executing untrusted project builds.
- Create a new immutable Zenodo version with version-specific DOI and synchronized landing-page, citation, license, checksum, and paper metadata. The existing published version remains unchanged.
- Keep evaluation-host export and other author-only operations outside the reviewer path. This change consumes the complete corpus package produced by `consolidate-evaluation-databases`; it does not redesign corpus export.
- **BREAKING**: release membership is declared by the release manifest, not by output-directory wildcards or whatever archives happen to exist. Optional archives use verified component identities instead of a shared-directory-nonempty shortcut.

## Capabilities

### New Capabilities

- `replication/artifact-release`: immutable release-set composition, archive identity and integrity, exact public metadata and requirements, safe component installation, and atomic release validation.
- `replication/artifact-evaluation`: supported reviewer environments, bounded smoke and reproduction workflows, claims-to-evidence verification, clean-machine evidence, and documented reuse and security boundaries.

### Modified Capabilities

- `reporting/artifact-provenance`: report generation from an immutable release without a Git checkout must resolve producing revisions from verified embedded release provenance while preserving the existing per-source attribution contract.

## Impact

- Packaging and replication entry points under `scripts/packaging/` and `replication/`, including archive assembly, preflight, extraction, setup, report execution, output comparison, and cleanup.
- Release metadata and reviewer documentation: `README.md`, `REQUIREMENTS.md`, `INSTALL.md`, `STATUS.md`, `LICENSE*`, `CITATION.cff`, third-party notices, the accepted paper or stable paper link, and Zenodo metadata.
- Analysis provenance resolution and report verification under `analysis/src/teralizer/eval/` plus focused tests.
- Docker/Compose definitions, dependency locks, image provenance, supported-platform declarations, and release CI.
- Corpus and report manifests from `consolidate-evaluation-databases` and `make-report-runs-explicit` remain the authorities for their respective contents. This change adds a release-level manifest and does not duplicate either registry.
- The existing Zenodo DOI `10.5281/zenodo.18242626` remains the immutable first version. The new artifact receives a new version DOI under concept DOI `10.5281/zenodo.17950380`.
