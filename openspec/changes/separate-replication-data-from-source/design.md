## Context

See `proposal.md` for motivation. The repository currently assigns `/replication/datasets/*.dump` to Git LFS. `origin/master` contains LFS pointers for the earlier `postgres_dev` and `postgres_test` dumps, while unpushed commit `d8e1b7d5` replaces that package with four production dumps totaling 294,928,564 bytes plus generated manifest and checksum files.

The public Zenodo version already distributes database dumps inside `teralizer-core.zip`. The next artifact design also names Zenodo as the immutable release authority and accepts a verified external corpus package through `CORPUS_PACKAGE_DIR`. The database consolidation publisher already separates database-local export, resumable transfer, package assembly, and restore verification.

Public DOI metadata links to a Git tag. Rewriting published history for storage hygiene would damage that provenance without deleting GitHub's LFS objects. GitHub retains removed LFS objects, and Zenodo independently retains published bytes. This source-boundary migration therefore leaves all published references and remote objects unchanged.

This change does not fork the completed database consolidation publisher: it reuses that change's
corpus identities, package manifest, export, transfer, validation, and import behavior while moving
production outputs behind an explicit external path.

## Goals / Non-Goals

**Goals:**

- Make the source checkout complete for development and fixture-based validation without production corpus bytes.
- Keep one explicit package boundary from verified export through release assembly and reviewer restore.
- Use Zenodo as the public, immutable, DOI-addressed payload authority.
- Prevent generated corpus artifacts from returning through Git or Git LFS.
- Remove the new package from unpushed history without changing the other unpushed work.

**Non-Goals:**

- Change corpus identities, database contents, report semantics, or PostgreSQL dump format.
- Add DVC, DataLad, git-annex, or another data-versioning client.
- Use GitHub Releases as a second publication authority.
- Rewrite public commits or DOI-linked tags merely to reduce LFS usage.
- Claim that removing current pointers deletes historical GitHub or Zenodo bytes.

## Decisions

### 1. Use four explicit storage zones

The system has four zones with one-way promotion:

```text
source repository
  code, declarations, schemas, synthetic fixtures
        |
        | export command and declared inputs
        v
author staging
  durable dumps, generated manifest, checksums
        |
        | complete-package validation
        v
release staging
  immutable component archives and release manifest
        |
        | archive-level acceptance
        v
Zenodo version
  DOI-addressed public bytes
```

Production payloads never move backward into the source zone. Author staging remains ignored and may be local, SSH-backed, or object-backed without changing the package contract. Release staging is attempt-scoped and promotes only a complete validated set.

**Alternative considered:** Keep LFS pointers in Git and treat LFS as staging. Rejected because LFS couples source history to whole-file binary versions, owner storage and bandwidth, and optional source-archive behavior while providing neither release acceptance nor a DOI.

### 2. Keep the package manifest with the package

The generated corpus manifest and `checksums.sha256` remain authoritative for a particular package and travel beside its dumps. They are not committed as ambient source state. Git keeps the manifest schema, corpus registry, publisher, importer, and synthetic examples.

After publication, Git may record a small handoff descriptor containing the artifact version, version DOI, release-manifest URL or record id, and expected release-manifest SHA-256. The release manifest remains authoritative for archive membership and binds the full corpus manifest.

**Alternative considered:** Commit the generated manifest but not the dumps. Rejected because the manifest is a release output tied to exact payload bytes and producer revision. Keeping it in the source tree invites stale or recursively inconsistent provenance.

### 3. Require an explicit external package path

Production release assembly accepts exactly one explicit corpus package directory. It validates that directory before use and does not fall back to `replication/datasets`, filename discovery, Git LFS hydration, or another ambient location. Reviewer restore accepts an installed or downloaded release component and applies the same manifest and checksum verification before database mutation.

Developer and CI tests use generated or checked-in synthetic fixtures under a dedicated fixture path. A production command refuses fixture identities; a fixture test never requires archival downloads.

**Alternative considered:** Automatically download the latest Zenodo corpus when no path is supplied. Rejected because “latest” is not reproducible, introduces network access into author builds, and can silently mix source and artifact versions.

### 4. Make Zenodo the sole public payload authority

The next public package is a new version under concept DOI `10.5281/zenodo.17950380`. Its version-specific DOI, release manifest, corpus manifest, archive checksums, and source revision identify one immutable release. Existing versions remain unchanged.

GitHub Releases may hold short-lived release-candidate artifacts only if automation needs them. They are not documented as the reviewer source and are deleted according to a declared retention policy after Zenodo publication. No DVC or object-store credentials appear in reviewer instructions.

**Alternative considered:** Publish dumps as GitHub Release assets. Rejected as the authority because the project already has DOI versioning, preservation, licensing metadata, and a release acceptance design in Zenodo.

### 5. Enforce the source boundary at ignore, attribute, and validation layers

The production dataset output path becomes ignored. The broad Git LFS rule for production dumps is removed. Repository hygiene scans tracked paths and staged changes for PostgreSQL dump signatures and generated corpus-package markers outside the fixture boundary. Synthetic fixtures are allowlisted by path and fixture metadata, not by a size threshold alone.

Normal CI runs the hygiene detector and synthetic publication/import tests without LFS hydration or archival downloads. Manual release acceptance starts from staged downloadable archives, as already required by `make-replication-artifact-badge-ready`.

**Alternative considered:** Rely only on `.gitignore`. Rejected because ignored files can still be force-added and already tracked paths remain tracked.

### 6. Rewrite only unpushed package history

Before pushing the current unpushed commit series, implementation records the commit graph and creates a recoverable local reference. It rewrites `d8e1b7d5` so the production dumps, generated manifest, checksum inventory, and obsolete `postgres_test` deletion are absent from the source commit. The other six unpushed commits retain their order, messages, and effective patches. The rewritten series is compared with the original outside the removed package paths before any push.

Published `origin/master`, DOI tags, and existing Zenodo versions are not rewritten by this migration. The current branch instead removes the old tracked package at its new tip and establishes the external boundary.

**Alternative considered:** Rewrite all repository history and force-push. Rejected because it breaks public commit and tag identities, does not itself purge GitHub LFS storage, and would invalidate DOI-linked provenance for routine storage cleanup.

## Risks / Trade-offs

- **A clean checkout no longer contains production data.** Reviewer and maintainer commands must distinguish source development from artifact installation and print the exact package or DOI input they require.
- **Author staging becomes another state boundary.** Existing manifest checks, resumable transfer, atomic promotion, and explicit paths constrain it; adding a new data-versioning product would duplicate those controls.
- **Old LFS storage continues to count on GitHub.** This is accepted. Current source and CI stop adding new dump objects.
- **The unpushed rewrite can lose unrelated work.** A recovery reference, commit-graph inventory, patch comparison, and no push before review are mandatory.
- **Synthetic fixtures can drift from production behavior.** Fixture tests cover package shape and failures; release-candidate acceptance still restores the real archival package on a clean environment.
