## Context

See proposal.md - Why. The corpus-publication work in `consolidate-evaluation-databases` owns database-local export, per-corpus dumps, and the verified corpus manifest. `make-report-runs-explicit` owns one complete registered report run and its generated artifact set. This change starts after those boundaries: it turns their outputs plus source, optional project/data payloads, and public metadata into a reviewer-facing archival release.

The existing package builder is a shell script that copies trees into seven ZIP files and checksums every `*.zip` already present in its output directory. Optional archives extract into shared `projects/` and `data/` roots, while quick start skips later archives as soon as either root is nonempty. The published Zenodo description, repository documentation, and current commands have diverged. The package also removes `.git`, although report provenance currently invokes Git while regenerating reports.

A release has two audiences with different trust boundaries. Maintainers need to build and publish it from complete verified inputs. Reviewers need to download declared components and exercise them without any author infrastructure. The release format must connect these paths without exposing the evaluation host as a package dependency or creating a second corpus or report registry.

## Goals / Non-Goals

**Goals:**

- Produce one manifest-declared, immutable, upload-ready release from complete verified inputs.
- Give each archive an independent identity and payload manifest while allowing deliberate composition into one workspace.
- Make one root command the reviewer interface for preflight, smoke, results reproduction, reduced collection, status, and cleanup.
- Use the verified corpus manifest, report artifact manifest, and source provenance as inputs rather than rediscovering their contents.
- Run the primary reviewer path in pinned containers on a declared Linux x86-64 baseline without requiring host Python, Java, Gradle, Maven, Nix, or Git.
- Generate exact release summaries and validate hand-authored guidance against machine-readable declarations.
- Prove the staged archives, not the source checkout, before publication.

**Non-Goals:**

- Redesigning evaluation-host corpus export, PostgreSQL schema boundaries, report registration, or consumer-repository artifact delivery.
- Re-running the full empirical study merely to repackage its immutable measured inputs.
- Claiming ACM Results Reproduced or Results Replicated without a subsequent independent study.
- Guaranteeing native execution on every operating system or CPU architecture.
- Making a 100-hour collection run fit within artifact review. The release provides bounded smoke and reduced paths and preserves the full path.
- Mutating the existing Zenodo version. A corrected or extended artifact is always a new archival version.

## Decisions

### 1. Release assembly consumes three existing authorities

The release builder consumes:

1. the complete verified corpus package and its corpus manifest;
2. the complete registered report output and artifact/provenance manifest; and
3. a new release declaration that names public components, workflows, platform support, licenses, citations, paper claims, and optional source/data inputs.

The release declaration references semantic corpus ids and registered report/artifact ids. It does not repeat physical database names, report implementations, or emitted-file discovery rules. Assembly resolves and cross-checks those references before reading bulk payloads.

A dedicated release module owns the declaration schema, component manifests, release manifest, documentation facts, archive planning, validation, and summaries. The existing corpus publisher remains responsible only for the corpus package. Thin shell entry points may set paths and invoke the release module, but do not parse or synthesize manifests independently.

**Alternative considered:** extend `corpus_publish.py` until it also builds the Zenodo record. Rejected because corpus integrity and public release composition have different inputs, lifecycle, and consumers. Combining them would make database publication responsible for project archives, papers, licenses, containers, and venue documentation.

### 2. The release has a standalone manifest and self-identifying components

The upload set contains standalone `release-manifest.json`, its published schema, `checksums.sha256`, a human-readable release summary, Zenodo metadata, and the declared component archives. The release manifest records the final archive checksums and sizes and is therefore generated after every archive closes.

Each archive has a unique wrapper root and contains `component-manifest.json`, citation and license information, and a payload subtree. Component ids are stable semantic names such as `core`, `results`, `projects-controlled`, `projects-real-world-sample`, `projects-real-world`, `data-controlled`, and `data-real-world`; filenames remain presentation. The component manifest records every installed path and checksum, component relationships, release identity, and payload provenance.

The core component contains the installer, reviewer documentation, embedded source provenance, corpus package, report reference outputs, and a component catalog. Optional components are built before core so that the catalog can bind their checksums and facts. The catalog deliberately omits the core archive's own final checksum. The standalone release manifest binds that value after core closes. This avoids recursive manifests whose bytes would depend on their own checksum or size.

Every archive is independently understandable. The standalone release summary and Zenodo landing page provide exact pre-download sizes. Packaged requirements provide exact runtime, unpacked, restored-data, and peak-resource facts and identify the external release manifest for the final outer-archive byte sizes.

**Alternative considered:** embed the final release manifest in every archive. Rejected because an archive cannot contain a stable manifest that includes that archive's own checksum. Iterating until a rounded size appears stable would be a build trick, not an integrity contract.

### 3. Archive creation streams explicit source mappings into an atomic stage

The release declaration produces an explicit source-to-archive plan. The archive writer streams each declared input into a temporary Zip64 archive, hashes file bytes while writing, normalizes archive metadata needed for deterministic output, and writes the completed component manifest. It then verifies the archive by reading its central directory and manifest before renaming the temporary file complete.

The builder writes every archive and standalone release file into a new attempt directory. It promotes the directory only after schema validation, cross-component dependency checks, checksums, documentation validation, and the release acceptance suite pass. It never scans an existing output directory for membership. Failed attempts remain diagnosable or are removed explicitly; they never modify the previous complete release.

This approach avoids copying tens of gigabytes into a second payload staging tree before compression. The unavoidable archive and checksum reads remain sequential and restart boundaries are per component.

**Alternative considered:** retain the existing copy-tree-plus-`zip` script and add more exclusion patterns. Rejected because shell globs and ambient output directories caused the completeness defect, while copied staging doubles peak disk and still provides no typed component model.

### 4. Installation stages components and promotes one verified workspace

The core installer reads the release/component catalog and the selected workflow. It verifies sibling archive checksums, extracts each archive into a temporary component directory, validates every internal path and checksum, and computes the combined installation plan before changing the workspace.

Components install from their unique payload roots into declared workspace paths. Identical duplicate paths are deduplicated. Different bytes at one destination fail with both component ids and the path. Sample and full real-world components are declared as alternatives; selecting both returns a corrective command rather than producing a mixed workspace. Installation records the release and installed component ids so reruns can verify compatible state and add a missing nonconflicting component.

The old `projects/ is nonempty` and `data/ is nonempty` shortcuts are removed. Existing unowned files at a planned destination block promotion rather than being overwritten or silently accepted.

### 5. One reviewer controller owns the public workflow

A root executable inside core provides stable commands:

```text
artifact preflight
artifact smoke
artifact reproduce-results
artifact collect-reduced <corpus-id>
artifact collect-full <corpus-id>
artifact status
artifact clean
```

It delegates to focused existing helpers but owns working-directory resolution, component prerequisites, checkpoint order, error presentation, and final summaries. `smoke` includes package verification, requirements checks, service startup, corpus restore/preparation, read-only report access, one representative report, expected-output validation, and state reporting. Repeated commands verify and reuse compatible state. `clean` names and removes only state owned by the recorded installation.

Author-only export and release-build commands are excluded from packaged reviewer guidance. Maintainer documentation may describe them in the source repository, but the core archive's primary README does not ask reviewers for SSH aliases, Docker executable paths, source-container names, or an author database.

**Alternative considered:** document the current collection of scripts directly. Rejected because several require different working directories and expose internal sequencing. A small stable controller provides one observable interface while preserving focused helpers underneath.

### 6. The primary reviewer environment is container-only above the host runtime

The release-tested baseline is Linux x86-64 with Docker Engine or Docker Desktop and Compose V2. The report analysis, PostgreSQL service, database UI when requested, and Java pipeline use images pinned by immutable digest. Locked dependency installation has no permissive fallback. Image digests, container architecture, and build provenance enter the release manifest and clean-acceptance record.

The reviewer may pull declared images during setup. Once images and release components are present, smoke and results reproduction run without network access. An optional archived OCI image component may be published when its measured size is acceptable; it is not a substitute for Dockerfiles and locks. Apple Silicon through `linux/amd64` emulation is claimed only if the release acceptance matrix passes it. Native Windows outside WSL2 is not claimed.

Host-based `uv`, Nix, Java, or Gradle remain developer conveniences and do not appear as primary reviewer prerequisites. The Java collection container executes project builds in a disposable workspace without the Docker socket, host credentials, or writable mounts over the release source. Network access for third-party dependency resolution is isolated to the documented collection workflow.

### 7. Release provenance replaces Git only in verified packaged mode

Release assembly runs in a clean Git checkout and materializes `release-provenance.json`. For every source file that can produce a report artifact, it records repository-relative path, SHA-256, last-changing commit, source repository, and resolvable source URL template. The file itself is included in the core component manifest.

The provenance resolver has two exclusive modes:

- development mode uses Git exactly as the accepted specification requires;
- release mode first verifies the release and source-file checksum, then returns the embedded per-file revision.

Release mode is entered only from verified release metadata, not merely because `.git` is absent. Missing, extra, or mismatched provenance fails report startup. The package does not ship repository history and does not synthesize a common release commit for every source.

### 8. Report verification consumes artifact manifests, not directory scans

The results workflow compares the emitted registered `ArtifactSet` with the published reference manifest. Every report, table, CSV file, and figure is named by declared artifact identity and checked for missing, extra, and changed content.

The accepted artifact-provenance contract already requires byte-identical regeneration when source and data are unchanged. The pinned reviewer environment therefore makes deterministic report outputs, including figures and rendered Markdown, byte-identical or gives them an explicit canonical representation before hashing. A figure count is never evidence of equality. The claims declaration maps report artifacts to paper claims and supplies separate invariant/tolerance checks only for newly collected stochastic data.

Verification emits JSON for release gating and concise text for reviewers. Both derive from the same results. A non-passing required claim or output kind produces a nonzero exit status.

### 9. Requirements and public metadata are rendered from verified facts

A release declaration carries stable prose-independent facts: component roles, workflow dependencies, supported platforms, minimum resources, expected runtime classes, network phases, DOI relationships, licenses, and paper identity. Measured archive sizes, restored database sizes, peak resources, image sizes, and execution times come from the release build and clean acceptance record.

The release builder renders the compact tables used by the top-level README, detailed `REQUIREMENTS.md`, release summary, and Zenodo metadata. Hand-authored sections explain purpose, limitations, reuse, and security. Validation checks every command in documentation against the packaged controller help and every named file/component/claim against its manifest. The source repository may retain TOSEM-style `INSTALL.md`, `STATUS.md`, and dedicated license files, while README remains complete enough for venues that request one consolidated artifact document.

The new version DOI is reserved before the final candidate so it can be embedded consistently. The existing version DOI remains cited as version 1; `CITATION.cff` labels version and concept DOI correctly. Publication uploads the validated attempt directory without renaming or recompressing its members.

### 10. Data reuse and redistribution are release gates

Release assembly generates a data dictionary from the checked schema plus maintained semantic descriptions for tables, columns, units, null meanings, views, and exported result fields. Corpus documentation records selection, source, revision, transformation, and report use.

Every redistributed project has a third-party record containing origin URL, commit, license classifier, retained license path and checksum, and redistribution decision. Unknown or incompatible rights exclude source bytes from the archive; the record may retain only distributable identity and retrieval information. Generated tests and reports are audited for material copied from third-party sources and are covered by an explicit component license decision.

This legal inventory is separate from corpus identity. It answers whether bytes may be redistributed, not which corpus or project produced a result.

### 11. Release acceptance has fixture and real-package tiers

Fast tests build tiny synthetic component archives to exercise schema validation, atomic promotion, collision handling, stale-output exclusion, documentation checks, provenance release mode, setup retry, and failure diagnostics.

The release gate then builds the real candidate and launches a clean acceptance job from only the staged upload files. The job uses a new path and empty Docker volumes, installs the workflow components, runs smoke, reproduces all reports through the read-only role, verifies every claim summary, tests cleanup and retry, records measurements, and repeats the no-network analysis path. A source-checkout test cannot satisfy this gate.

Linux x86-64 is required for release. Apple Silicon compatibility is recorded only after a separate run on the actual environment. Full 12-hour and 100-hour collection are not release-gating reruns; their resumability and stage behavior are covered by the existing run ledgers plus reduced production-stage execution.

## Risks / Trade-offs

- **The new change overlaps unfinished corpus-publication work.** → Finish the non-destructive publication and clean-import tasks in `consolidate-evaluation-databases` first. Consume its manifest; do not fork its implementation.
- **Archive manifests and release manifests drift into two registries.** → Component manifests describe payload bytes; the release manifest describes archive relationships; both reference corpus/report authorities rather than restating them.
- **Exact outer archive facts are recursively self-referential.** → Keep final archive hashes and byte sizes only in the standalone release manifest and public summary. Internal component manifests bind payload facts.
- **Pinned images remain hosted outside Zenodo.** → Record immutable digests and Dockerfiles, verify the offline-after-setup path, and publish an OCI archive component when size and licensing permit.
- **The x86-64 Java stack is slow under Apple Silicon emulation.** → Make Linux x86-64 the release baseline, measure Apple compatibility separately, and do not promise the 30-minute bound on an unmeasured platform.
- **A third-party project has no redistribution license.** → Exclude its source payload and preserve only permitted provenance/retrieval data. Record the resulting full-rerun limitation rather than applying the artifact license to others' code.
- **The clean release gate is expensive.** → Use tiny fixture packages on every change and run the complete real-package gate once per release candidate. Never replace it with source tests.
- **A new DOI is embedded before publication but the deposit changes.** → Reserve the version DOI, freeze the candidate, upload the exact validated files, then verify the downloaded record. Any byte change creates a new candidate and, after publication, a new version.
- **Venue document layouts differ.** → Keep README as the complete entry point and retain plain-text requirements, status, install, and license documents. Venue submission wrappers may select documents but do not change artifact behavior or bytes.
- **A reduced collection run passes while the full run has environment-dependent failures.** → State the reduced scope precisely, retain full-run ledgers and expected failure semantics, and never present reduced execution as independent reproduction of full empirical results.

## Migration Plan

1. Complete the corpus export, transfer, assembly, clean import, and read-only report tasks from `consolidate-evaluation-databases`. Do not begin release assembly from a partial corpus set.
2. Introduce the release declaration and schemas, then build fixture component/release manifests and atomic archive assembly without changing the published version.
3. Add embedded per-source provenance and prove report generation from a Git-free fixture release.
4. Replace shared-root archive extraction with staged component installation and add the root reviewer controller.
5. Freeze the reviewer containers and move the primary analysis path off host `uv`; verify smoke and results workflows on clean Linux x86-64.
6. Define claims, output comparisons, data dictionaries, third-party notices, and generated public documentation. Remove or correct every retired command and unsupported claim.
7. Build the complete candidate from the four-corpus package and all declared components. Run clean archive acceptance, capture exact measurements, and render final requirements and Zenodo metadata.
8. Reserve a new Zenodo version DOI, rebuild and revalidate the frozen candidate with that DOI, and obtain human review of licenses, claims, paper metadata, and archive selection.
9. Upload the exact validated set as a new version. Download it by version DOI, verify release checksums, and repeat smoke plus results reproduction from the downloaded core.
10. If validation fails before publication, discard the candidate and leave version 1 untouched. If a defect is found after publication, preserve that immutable version and publish a corrected successor rather than replacing files.
