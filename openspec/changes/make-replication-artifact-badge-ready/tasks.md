## 1. Establish the release input boundary

- [ ] 1.1 Complete and validate tasks 5.9 through 6.5 of `consolidate-evaluation-databases`; require its four-corpus manifest, clean import, preparation, read-only report run, and clean-tree checks before this release consumes it.
- [ ] 1.2 Verify `make-report-runs-explicit` supplies one complete registered report run whose manifest covers Markdown reports, LaTeX tables, CSV data, figures, and provenance; record and test the release-facing loader rather than rediscovering output directories.
- [ ] 1.3 Inventory every intended version-2 source, corpus, result, project, data, paper, license, container, and documentation input, assign it one semantic component and install path, and reject any input with no declared owner.
- [ ] 1.4 Add the typed release declaration under `replication/` with release identity, DOI roles, paper identity, component relationships, workflow selections, supported platforms, resources, network phases, claims, licenses, and references to semantic corpus/report ids.
- [ ] 1.5 Add declaration validation tests for duplicate component ids, physical database names outside the corpus registry, unknown corpus/report/artifact references, cyclic or absent companions, incompatible alternatives, missing install paths, and incomplete production DOI metadata.

## 2. Build manifest-declared component archives

- [ ] 2.1 Define and publish versioned schemas for release, component, component-catalog, release-provenance, claim-result, and acceptance-record documents; reject unknown required semantics while preserving forward-compatible optional metadata.
- [ ] 2.2 Implement explicit source-to-archive planning from the release declaration, including normalized safe paths, required input checksums, unique wrapper roots, component-owned payloads, and legal-distribution eligibility.
- [ ] 2.3 Implement the deterministic Zip64 writer that streams declared files, normalizes metadata, records payload SHA-256 values and sizes, and writes one self-identifying `component-manifest.json` without a copied payload staging tree.
- [ ] 2.4 Implement archive verification that rejects missing, extra, duplicated, absolute, parent-traversing, symlink-escaping, or checksum-mismatched members and confirms the internal manifest checksum recorded by the release.
- [ ] 2.5 Build optional components before core, generate the core component catalog with their final checksums, and keep the core archive's self-referential outer checksum only in the standalone release manifest.
- [ ] 2.6 Generate `release-manifest.json`, its schema, `checksums.sha256`, the human-readable release summary, and the exact upload inventory from completed declared archives only.
- [ ] 2.7 Add attempt-directory assembly and atomic promotion so a failed late component or validation step cannot alter the previous complete release, and make component retries reuse only archives whose declaration and checksums still match.
- [ ] 2.8 Replace `prepare-zenodo-package.sh` archive membership, wildcard checksum, copied-staging, and fixed-count logic with a thin invocation of the release builder; retain no second shell implementation of the format.
- [ ] 2.9 Build synthetic multi-component fixtures and test stale output exclusion, absent components, changed inputs, interrupted writes, deterministic rebuilds, Zip64 metadata, failed promotion, and successful replacement of one complete release by another.

## 3. Install and compose archives safely

- [ ] 3.1 Implement a staged installer that verifies outer checksums when available, extracts into private temporary roots, verifies every component manifest and payload byte, and computes the complete install plan before modifying a workspace.
- [ ] 3.2 Implement declared merge behavior: deduplicate checksum-identical immutable files, reject divergent path collisions with both component ids, and reject sample-plus-full alternatives with a corrective command.
- [ ] 3.3 Record release id, installed component ids, owned paths, checksums, services, and volumes in installation state; make promotion atomic and block overwriting unowned pre-existing files.
- [ ] 3.4 Make repeated installation verify and reuse compatible state, allow a missing nonconflicting component to be added, and provide deterministic recovery or cleanup after interrupted extraction or promotion.
- [ ] 3.5 Remove the `projects/`-nonempty and `data/`-nonempty shortcuts from quick start and package extraction, then prove every selected project and data component installs when shared destination roots already contain an earlier selected component.
- [ ] 3.6 Put release identity, citation, license mapping, purpose, payload inventory, schema/provenance context, and component-appropriate instructions in every independently downloadable archive.
- [ ] 3.7 Add focused installer tests for archive substitution, checksum failure, path traversal, identical and divergent collisions, redundant full/sample selection, unowned destinations, retry, incremental installation, and cleanup ownership.

## 4. Preserve provenance and verify paper evidence

- [ ] 4.1 Generate `release-provenance.json` in a clean source checkout with repository-relative producing-source paths, source SHA-256 values, last-changing commits, repository identity, and resolvable source URLs; fail on dirty or unattributed producing sources.
- [ ] 4.2 Extend report provenance resolution with verified release mode that activates only through validated package metadata, checks the producing source bytes, and returns its embedded per-file revision without invoking Git.
- [ ] 4.3 Add provenance tests that compare checkout and Git-free release attribution and reject absent records, mismatched source bytes, unrelated checkout commits, a common fabricated release commit, and an unverified Git-less directory.
- [ ] 4.4 Define the claims-to-evidence matrix for every RQ0-RQ6 paper claim represented by the artifact, including semantic inputs, non-database inputs, exact command, emitted artifact ids, expected values or invariants, tolerance, runtime, and reproduction versus inspection status.
- [ ] 4.5 Mark every claim outside the distributable or bounded artifact explicitly unsupported with its legal, empirical, or practical reason; do not leave a report name or generic nondeterminism statement as an acceptance rule.
- [ ] 4.6 Extend the published report artifact manifest as needed so every registered report, table, CSV file, figure, and canonical figure source is declared and connected to its producing report and supported claims.
- [ ] 4.7 Make deterministic report rendering reproducible in the pinned environment or define and emit its documented canonical form; retain explicit invariant/tolerance comparison only for newly collected stochastic evidence.
- [ ] 4.8 Implement one manifest-driven evidence verifier that detects missing, extra, and changed outputs and emits equivalent concise text and machine-readable claim/category summaries with a failing exit status on any required disagreement.
- [ ] 4.9 Test the verifier by independently changing a report, table, CSV value, figure source value, rendered figure, claim expectation, and output membership; prove file-count equality cannot pass changed evidence.

## 5. Freeze the reviewer runtime and public controller

- [ ] 5.1 Pin every reviewer base image and service image by digest, pin the analysis dependency installer and lock resolution, remove unlocked fallbacks, and record image architecture and provenance in release metadata.
- [ ] 5.2 Make the analysis image run every registered report and verifier without host Python, `uv`, Nix, Git history, or a writable source tree; expose only the declared output and state mounts.
- [ ] 5.3 Fix Compose service and container-name disagreements, use service discovery rather than author deployment names, and prove corpus restore, preparation, database UI, analysis, and report-role checks under an arbitrary Compose project name.
- [ ] 5.4 Harden the Java collection image and launcher so third-party Maven or Gradle logic runs in disposable scratch state with resource limits, no host credentials, no Docker socket, and no writable mount over packaged source.
- [ ] 5.5 Add the root `artifact` controller with stable `preflight`, `smoke`, `reproduce-results`, `collect-reduced`, `collect-full`, `status`, and `clean` commands and one shared state/checkpoint model.
- [ ] 5.6 Implement preflight checks for release/component identity, supported or emulated architecture, Docker and Compose compatibility, image digests, ports, workflow-specific archive set, memory, disk scope, and declared network needs.
- [ ] 5.7 Implement bounded smoke setup that starts services, restores and prepares its declared corpus, proves report-role read-only access, runs one representative report, verifies expected evidence, prints one checkpoint summary, and completes within the measured 30-minute baseline bound.
- [ ] 5.8 Make smoke idempotent and resumable: verify compatible existing services and volumes, preserve and name failed checkpoint state, and print exact retry and owned-state cleanup commands.
- [ ] 5.9 Route `reproduce-results` through every registered report and the manifest-driven evidence verifier, then prove the workflow runs with network disabled after images and components are present.
- [ ] 5.10 Route reduced and full collection through the same production stage implementations, ledgers, per-item outcomes, resource caps, and resume checkpoints; make the reduced real-world subset finish within one day and state which full-study claims it cannot establish.
- [ ] 5.11 Add integration tests for controller dispatch, working-directory independence, unsupported platforms, active ports, setup retry, incompatible state, read-only failures, offline results reproduction, collection resume, status, and owned cleanup.

## 6. Make reuse, licensing, and documentation complete

- [ ] 6.1 Generate a checked data dictionary for published tables, columns, units, null meanings, derived views, result CSV fields, and provenance links; fail when the corpus/report schema contains an undocumented public field.
- [ ] 6.2 Implement the third-party inventory and release gate for origin URL, immutable revision, detected license, retained license path and checksum, attribution, redistribution decision, and component membership.
- [ ] 6.3 Resolve and review redistribution records for every controlled-corpus project component, excluding source bytes with unclear or incompatible permission while retaining only permitted identity and retrieval information.
- [ ] 6.4 Resolve and review redistribution records for the sampled real-world project component and verify every included project carries its original license and attribution.
- [ ] 6.5 Resolve and review redistribution records for the full real-world project component; if source redistribution cannot be established, publish the declared retrieval/provenance component instead of implying that the artifact license covers those sources.
- [ ] 6.6 Audit generalized and generated tests, logs, database dumps, report data, and figures for copied third-party material, human-participant or sensitive data, and component-level licensing; record every resulting inclusion or exclusion decision.
- [ ] 6.7 Add the top-level source, data/documentation, and third-party license map, retained third-party license texts, notices, ethical statement, and explicit statement that analyzed projects keep their original licenses.
- [ ] 6.8 Render and validate the reviewer README, `REQUIREMENTS.md`, `INSTALL.md`, `STATUS.md`, release summary, and archive-local instructions from the release declaration and measured manifests while keeping explanatory prose hand-authored.
- [ ] 6.9 Document data inspection, schema interpretation, project/configuration addition, safe new-input collection, report extension, evidence export, stable interfaces, network phases, writable state, cleanup, and untrusted-build risks.
- [ ] 6.10 Make the results and data components independently understandable by including study identity, citation, licenses, schemas, provenance, contents, and the commands appropriate without core or the Zenodo page.
- [ ] 6.11 Correct `CITATION.cff`, paper metadata, accepted-paper or stable preprint inclusion, concept-versus-version DOI labels, badge table, and Zenodo metadata so every surface names the same release and claims only Available and Evaluated - Functional/Reusability targets.
- [ ] 6.12 Add documentation validation that resolves every named command, option, component, archive, DOI, database term, paper claim, and file and compares every size, resource, platform, network, and runtime field with machine-readable release facts.

## 7. Automate archive-level acceptance

- [ ] 7.1 Add fast fixture-package tests to pull-request CI, covering schemas, archive build and verification, installation, provenance release mode, documentation validation, controller smoke, and injected failures without requiring the real corpora.
- [ ] 7.2 Add a manually dispatched or release-candidate Linux x86-64 workflow that receives only staged upload files, starts in a new directory with empty Docker volumes and no source checkout or author credentials, and retains its acceptance record and logs.
- [ ] 7.3 Record release-manifest checksum, host and container architecture, image digests, exact commands, checkpoint outcomes, wall times, peak memory, peak disk by scope, network phases, and cleanup result in a schema-validated acceptance record.
- [ ] 7.4 Make archive acceptance install the documented smoke component set, test first-run failure recovery and second-run reuse, complete smoke within its bound, test owned cleanup, and repeat analysis with network disabled.
- [ ] 7.5 Make archive acceptance install the full results component set, run every registered report over all four restored corpora through read-only roles, and require every supported claim and output category to pass.
- [ ] 7.6 Run the reduced production collection path inside the archive boundary, interrupt and resume it once, and verify its recorded scope, per-item outcomes, resource caps, and explicit non-claims.
- [ ] 7.7 Scan the staged release and reviewer containers for author filesystem paths, SSH hosts, private endpoints, credentials, Docker socket mounts, undeclared executables, mutable image tags, and unowned writable source paths; fail release eligibility on any finding.
- [ ] 7.8 Run the archive acceptance path on Apple Silicon through the actual declared emulation stack and record it as compatibility-tested only if smoke and results reproduction pass within the published measurements.

## 8. Build and publish the immutable version

- [ ] 8.1 Create a draft successor under Zenodo concept DOI `10.5281/zenodo.17950380`, reserve its version DOI, and commit the consistent DOI, version, citation, release declaration, and public metadata before building the final candidate.
- [ ] 8.2 Build a provisional complete release from the verified four-corpus package and every declared optional component, then run Linux acceptance to capture actual archive, restore, resource, setup, reproduction, reduced-run, and cleanup measurements.
- [ ] 8.3 Update measured release facts, regenerate all human-readable requirements and Zenodo metadata, rebuild from a clean checkout, and require that no placeholder or superseded version-1 value remains.
- [ ] 8.4 Run the complete Linux archive acceptance suite against the exact final release manifest and upload files, including all report evidence, retry, cleanup, no-network analysis, reduced collection, and failure diagnostics.
- [ ] 8.5 Obtain human review of archive selection, paper-claim mappings, accepted-paper metadata, licenses, third-party redistribution decisions, ethical statement, badge wording, DOI roles, and the generated Zenodo landing-page content.
- [ ] 8.6 Run frozen repository tests, formatting, lint, types, commit hooks, strict OpenSpec validation, fixture CI, clean-tree checks, and the full release-integrity validator without modifying the accepted upload set.
- [ ] 8.7 Upload the exact validated manifest, schemas, summaries, checksums, and component archives to the draft record; compare every remote draft file byte-for-byte with the accepted local inventory before publication.
- [ ] 8.8 After explicit approval of the immutable file set and landing-page metadata, publish the new Zenodo version without altering version DOI `10.5281/zenodo.18242626` or labeling the concept DOI as a version DOI.
- [ ] 8.9 Download the published release by its version DOI into a clean location, verify its complete manifest and checksums, rerun smoke and results reproduction, and retain the post-publication acceptance record tied to the downloaded bytes.
- [ ] 8.10 Update repository release links and status with the published version and acceptance record, then confirm every public link resolves and every badge claim remains no stronger than the evidence.
