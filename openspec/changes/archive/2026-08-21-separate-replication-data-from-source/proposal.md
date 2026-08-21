## Why

Production PostgreSQL dumps are generated publication outputs, but the source repository tracks them through Git LFS even though the artifact already publishes database payloads through Zenodo. This duplicates storage, couples ordinary clones and CI to metered binary delivery, and conflicts with repository data-hygiene rules.

## What Changes

- **BREAKING**: Remove production corpus dumps and generated corpus-package outputs from the current source tree and stop tracking `replication/datasets/*.dump` with Git LFS.
- Keep source code, corpus declarations, schemas, tiny synthetic fixtures, and future release references in Git; keep production dumps, generated manifests, and checksum inventories in ignored author staging and downstream archival releases.
- Make corpus export, package verification, and release assembly consume an explicit external package directory and fail rather than rediscovering payloads from the checkout.
- Hand versioned archives, reviewer retrieval, release references, and Zenodo publication to `make-replication-artifact-badge-ready`, which consumes the verified external corpus package.
- Add repository hygiene checks that reject production dumps and generated corpus-package residue while allowing deliberately scoped synthetic fixtures.
- Rewrite the unpushed corpus-package commit without altering the other unpushed commits. Preserve published Git history, DOI tags, existing Zenodo versions, and remote LFS objects.

## Capabilities

### New Capabilities

- `replication/corpus-artifact-storage`: Defines the source, author-staging, release-handoff, integrity, and history boundaries for production corpus packages.

### Modified Capabilities

None.

## Impact

The change affects `.gitattributes`, ignore and hygiene rules, corpus export and import paths, release assembly inputs, CI fixtures, documentation, and the unpushed local commit series. It coordinates with `consolidate-evaluation-databases` and hands reviewer download and public release behavior to `make-replication-artifact-badge-ready`; it does not redesign corpus identity, dump contents, report semantics, or Zenodo DOI versioning. Public history and existing DOI-linked tags remain stable by default.
