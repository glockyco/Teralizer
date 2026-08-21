## 1. Freeze the migration boundary

- [x] 1.1 Record the current branch graph, the current commits ahead of `origin/master`, every tracked `replication/datasets/` path, current and historical corpus LFS object ids and sizes, and every DOI-linked tag.
- [x] 1.2 Create a recoverable local reference for the pre-migration branch and prove it resolves to the recorded tip; do not push or rewrite any public reference.
- [x] 1.3 Inventory every source, test, CI, packaging, documentation, and OpenSpec reference that assumes production dumps or generated package metadata live under `replication/datasets/`.
- [x] 1.4 Classify each current dataset path as source declaration, schema, synthetic fixture, author-stage output, release-stage output, or archival payload and assign one destination.
- [x] 1.5 Reconcile this boundary with `consolidate-evaluation-databases` and `make-replication-artifact-badge-ready`; remove assumptions that the source checkout owns generated corpus bytes without duplicating their tasks.

## 2. Enforce the source repository boundary

- [x] 2.1 Remove production dump tracking from `.gitattributes` without disturbing LFS rules for unrelated assets.
- [x] 2.2 Ignore the production corpus-package output and author/release staging paths while retaining source declarations and schemas.
- [x] 2.3 Move or regenerate database test inputs under one dedicated synthetic-fixture boundary with explicit fixture metadata that cannot be mistaken for a published corpus.
- [x] 2.4 Add a repository hygiene detector that rejects tracked or staged PostgreSQL dump signatures and generated corpus-package markers outside the fixture boundary.
- [x] 2.5 Add rejected fixtures for a renamed production dump, a force-added ignored dump, a generated manifest, and a generated checksum inventory.
- [x] 2.6 Add allowed fixtures for corpus declarations, schemas, release references, and the dedicated synthetic database fixture.
- [x] 2.7 Run the hygiene detector from the existing pre-commit and CI entry points without requiring Git LFS hydration or a network connection.

## 3. Separate publication from the checkout

- [x] 3.1 Make database-local export and transfer promote complete dumps, manifest, and checksums only into an explicit external author-stage package directory.
- [x] 3.2 Make corpus package summarization and validation accept an explicit package directory and remove production fallbacks to `replication/datasets/` or filename discovery.
- [x] 3.3 Make release assembly require `CORPUS_PACKAGE_DIR` or its typed equivalent, validate it before archive creation, and ignore dump-like checkout residue.
- [x] 3.4 Make missing, partial, stale, fixture-only, wrong-revision, and checksum-mismatched external packages fail with corrective diagnostics before release staging changes.
- [x] 3.5 Keep the package manifest and checksum inventory beside the dumps throughout author staging, release assembly, and archival publication.
- [x] 3.6 Update importer and quick-start boundaries so source-development commands use fixtures while reviewer commands consume an installed, verified release component.
- [x] 3.7 Add focused tests proving an explicit complete package succeeds and no package, an ambient checkout package, and a synthetic fixture presented as production fail.

## 4. Bind the release handoff

- [x] 4.1 Make release assembly bind the corpus manifest, dump identities, sizes, checksums, provenance, and source revision to one verified candidate input.
- [x] 4.2 Assign release references, component archives, reviewer retrieval, archive-level acceptance, and Zenodo publication to `make-replication-artifact-badge-ready` without duplicating its release contract.
- [x] 4.3 Keep Zenodo under concept DOI `10.5281/zenodo.17950380` as the sole selected public payload authority while leaving creation of the next version to the release owner.
- [x] 4.4 Ensure maintainer, reviewer, requirements, and release documentation distinguish source checkout, author staging, release staging, and published installation commands.
- [x] 4.5 Prove normal source checkout, lint, format, build, and fixture CI complete without production dumps, Git LFS corpus downloads, Zenodo, or author credentials.

## 5. Remove the unpushed package from history

- [x] 5.1 Require a clean worktree and an explicit operator checkpoint before rewriting; preserve the recovery reference and record all pre-rewrite commit ids.
- [x] 5.2 Rewrite unpushed commit `d8e1b7d5` so production dumps, generated manifest, generated checksum inventory, and obsolete package-tree changes are absent while its valid non-package intent is preserved or removed explicitly.
- [x] 5.3 Replay the remaining unpushed and implementation commits without changing their order, subjects, or effective patches outside the planned storage-boundary changes.
- [x] 5.4 Compare old and rewritten commit ranges by changed path and patch, and account for every difference before deleting any recovery reference.
- [x] 5.5 Confirm the rewritten tip tracks no production corpus payload or generated package output and introduces no new corpus LFS object.
- [x] 5.6 Leave `origin/master`, public tags, DOI-linked commit identities, existing Zenodo records, and remote LFS objects unchanged.

## 6. Verify the complete boundary

- [x] 6.1 Run synthetic publication, package validation, import, hygiene positive-control, and hygiene negative-control tests.
- [x] 6.2 Run a production-package dry run from an external path and confirm release staging records the expected four corpus identities and 294,928,564 declared dump bytes without reading the checkout package path.
- [x] 6.3 Run archive-level acceptance from staged downloadable files in a clean location with no source checkout, author database, author credentials, or hydrated corpus LFS objects.
- [x] 6.4 Run `lefthook run pre-commit --all-files` and all repository checks required by the affected Python, shell, packaging, and documentation areas.
- [x] 6.5 Run `openspec validate separate-replication-data-from-source --strict` and validate the coordinated active changes strictly.
- [x] 6.6 Review the final source tree, Git attributes, ignored paths, release handoff, package staging, documentation, and commit graph; remove incidental changes.
- [x] 6.7 Report source-repository state, candidate release state, old GitHub LFS state, old Zenodo state, and future publication ownership separately.
