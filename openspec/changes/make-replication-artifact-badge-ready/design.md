## Context

See proposal.md - Why. The corpus-publication work in `consolidate-evaluation-databases` owns database-local export, per-corpus dumps, and the verified corpus manifest. `make-report-runs-explicit` owns one complete registered report run and its generated artifact set. This change starts after those boundaries: it turns their outputs plus source, optional project/data payloads, and public metadata into a reviewer-facing archival release.

The existing package builder is a shell script that copies trees into seven ZIP files and checksums every `*.zip` already present in its output directory. Optional archives extract into shared `projects/` and `data/` roots, while quick start skips later archives as soon as either root is nonempty. The published Zenodo description, repository documentation, and current commands have diverged. The package also removes `.git`, although report provenance currently invokes Git while regenerating reports.

A release has two audiences with different trust boundaries. Maintainers need to build and publish it from complete verified inputs. Reviewers need to download declared components and exercise them without any author infrastructure. The release format must connect these paths without exposing the evaluation host as a package dependency or creating a second corpus or report registry.

## Goals / Non-Goals

**Goals:**

- Produce one manifest-declared, immutable, upload-ready release from complete verified inputs.
- Give each archive an independent identity and payload manifest. Keep large corpus and project families fine-grained so each workflow downloads only its declared data dependencies; keep smaller executable and evidence payloads cohesive in core.
- Make one root command the reviewer interface for preflight, smoke, results reproduction, reduced or full collection, and cleanup.
- Use the verified corpus manifest, report artifact manifest, and source provenance as inputs rather than rediscovering their contents.
- Run the primary reviewer path in pinned containers on a declared Linux x86-64 baseline without requiring host Python, Java, Gradle, Maven, Nix, or Git.
- Generate exact release summaries and validate stable structured facts in hand-authored guidance against machine-readable declarations.
- Prove the staged archives, not the source checkout, before publication.

**Non-Goals:**

- Redesigning evaluation-host corpus export, PostgreSQL schema boundaries, report registration, or consumer-repository artifact delivery.
- Re-running the full empirical study merely to repackage its immutable measured inputs.
- Claiming ACM Results Reproduced or Results Replicated without a subsequent independent study.
- Guaranteeing native execution on every operating system or CPU architecture.
- Making a 100-hour collection run fit within artifact review. The release provides bounded smoke and reduced paths and preserves the full path.
- Mutating the existing Zenodo version. A corrected or extended artifact is always a new archival version.
- Building a general package manager, workflow database, service manager, documentation parser, ZIP implementation, or archive cache. Components remain isolated and existing subsystem state remains authoritative.
- Publishing archived OCI images unless a measured availability requirement later justifies their size and licensing cost. Digest-pinned image references and Dockerfiles are the release contract.

## Decisions

### 1. Release assembly consumes three existing authorities

The release builder consumes:

1. The complete verified corpus package and its corpus manifest.
2. The complete registered report output and artifact/provenance manifest.
3. A new release declaration that names public components, workflows, platform support, licenses, citations, paper claims, and optional source/data inputs.

The release declaration references semantic corpus ids and registered report/artifact ids. It does not repeat physical database names, report implementations, or emitted-file discovery rules. Assembly resolves and cross-checks those references before reading bulk payloads.

A dedicated release module owns the declaration schema, component manifests, release manifest, documentation facts, archive planning, validation, and summaries. The existing corpus publisher remains responsible only for the corpus package. Thin shell entry points may set paths and invoke the release module, but do not parse or synthesize manifests independently.

#### Required release inventory and ownership

Release closure is computed from declarations, not from the files currently visible in a checkout. The final declaration and disposition ledger cover these boundaries:

| Boundary | Required content | Authority | Release owner |
|---|---|---|---|
| Teralizer source and runtime | Committed source, build and analysis locks, configuration, schemas, scripts, reviewer controller, documentation, tracked EvoSuite LFS object, materialized `jpf-symbc`, and pinned `jpf-core` | Git tree, lock files, LFS object ids, `.gitmodules`, and submodule commits | `core` |
| Controlled project source | `projects/EqBench` and other controlled inputs at recorded revisions, without nested Git metadata | Corpus/config declarations and project commits | `projects-controlled` |
| Corpus data | Complete manifests and verified dumps for `controlled`, `real-world`, `jarvis-scenarios`, and `jarvis-benchmark` | Corpus registry and corpus package manifest | Corpus components |
| Registered evidence | Every registered Markdown report, LaTeX table, CSV file, figure, macro file, and provenance record from one complete report run | `ReportSpec`, `ArtifactSet`, and report-run manifest | `core` |
| Non-database report inputs | `project-source-facts.json`, `jarvis-value-facts.json`, `cut_values.tsv`, and accepted JARVIS completion evidence; no report may resolve a source-checkout fallback | Every registered `FileInputSpec` | `core` |
| JARVIS backing evidence | The 1,494 census and 30 scorecard value logs bound by the compact-facts checksums; raw CUT-PVC captures bound to `cut_values.tsv`; scorecard and census status/provenance ledgers | JARVIS evidence extractors, capture plan, run ledgers, and checksums | `core` |
| Project inputs | Redistributable source snapshots, exact revisions, configs, patches, licenses, and retrieval records for controlled, real-world, and JARVIS collection workflows, including the two scorecard and twelve census fixture roots | Corpus/config declarations plus the third-party rights ledger | Workflow-specific project components |
| Runtime | Digest-pinned analysis, PostgreSQL, and collection image references; Dockerfiles; Compose declarations; lock files; architecture and network facts | Release declaration and container build records | `core` |
| Public contract | Release and component manifests, checksums, schemas, requirements, citation, accepted-paper link, licenses, third-party notices, claim matrix, data dictionary, limitations, and acceptance record | Release declaration and measured release facts | Standalone release files and every independently downloadable component as applicable |

The JARVIS inventory is deliberate. `jarvis-value-facts.json` is sufficient to inspect the published RQ0 values but not to repeat its extraction from source logs. The release therefore retains the selected raw value logs, not the complete 13 GiB author run roots. `cut_values.tsv` currently has no retained raw capture tree; the release work must rerun the capture from pinned fixtures, retain the raw TSVs, and bind the aggregate to them before claiming that evidence as regenerable. The current optional marker `data/detached/census-gen.complete` and the runner-owned `data/jarvis-census/complete` are competing completion paths. They must become one declared completion-evidence record validated against the census status ledger and corpus database before the final report run is frozen.

Every remaining ignored or generated path receives an explicit disposition: required payload, reproducible build output, cache/scratch state, sensitive state, duplicate, or unrelated historical evidence. A clean-checkout rebuild and clean archive acceptance prove that no required byte depends on an ambient `data/`, `projects/`, submodule worktree, Maven/Gradle cache, report output, or author-only path. The release may omit caches, complete working run roots, and duplicate generated trees only after the ledger names the authoritative replacement and the workflow that reconstructs or does not require them.

**Alternative considered:** extend `corpus_publish.py` until it also builds the Zenodo record. Rejected because corpus integrity and public release composition have different inputs, lifecycle, and consumers. Combining them would make database publication responsible for project archives, papers, licenses, containers, and venue documentation.

### 2. The release uses fine-grained, self-identifying components

The upload set contains standalone `release-manifest.json`, `checksums.sha256`, a human-readable release summary, Zenodo metadata, and all declared component archives. The release manifest is the only release catalog. It records final archive checksums, sizes, workflow dependencies, and compatibility rules after all archives close. Core does not contain another component catalog.

Only three formats are public contracts: the release manifest, the component manifest, and the acceptance record. Provenance, claims, evidence lineage, and path dispositions remain typed payload sections or build records. They do not become independent versioned schema families unless a separate consumer is demonstrated.

Each archive has a unique component id and wrapper root. It contains `component-manifest.json`, a concise component README, applicable citation and license information, and one payload subtree. The component manifest records payload paths and checksums, release identity, purpose, dependencies, incompatibilities, and provenance. No component owns another component's payload.

The initial component graph is deliberately fine-grained:

| Component | Payload | Required by |
|---|---|---|
| `core` | Complete Teralizer source and runtime inputs, reviewer controller, configuration, locks, schemas, documentation, embedded provenance, registered reference results, compact report inputs, selected JARVIS backing evidence, claims, and data dictionary | Reference inspection and all executable reviewer workflows |
| `corpus-controlled` | Verified `controlled` corpus package | Workflows that read the controlled corpus |
| `corpus-real-world` | Verified `real-world` corpus package | Workflows that read the real-world corpus |
| `corpus-jarvis-scenarios` | Verified `jarvis-scenarios` corpus package | JARVIS census report workflows |
| `corpus-jarvis-benchmark` | Verified `jarvis-benchmark` corpus package | JARVIS scorecard report workflows |
| `projects-controlled` | Redistributable controlled-project inputs, including `EqBench` | Controlled collection |
| `projects-real-world-sample` | Redistributable reduced real-world subset | Reduced real-world collection |
| `projects-real-world-remainder` | Redistributable full-set projects not present in the sample | Full real-world collection together with the sample |
| `projects-jarvis-scoreboard` | Two pinned scorecard fixture roots and configs | JARVIS scorecard collection and CUT-PVC capture |
| `projects-jarvis-census` | Twelve pinned census fixture roots and configs | JARVIS census collection |

`core` is the default owner for source, runtime support, reports, compact inputs, and small backing evidence. A separate component is justified only when at least one documented workflow omits it and measured size or redistribution rights make that omission useful. A split must preserve semantic ownership and must not duplicate bytes already owned by another component. Small or tightly coupled payloads stay in core; archive count is not a goal.

The release declaration publishes an exact component set for each workflow. Reference inspection and JARVIS evidence audit require core only. Bounded smoke requires core plus the smallest corpus package that supports its representative report. RQ0 reproduction requires core plus the two JARVIS corpus components. All-report reproduction requires core plus all four corpus components. Collection requires core plus only the applicable project component; full real-world collection adds the remainder to the sample. The Zenodo description and release summary show each workflow's component names and total download, unpacked, restored, and peak-disk sizes. A user never needs to download an unrelated corpus or project family.

Every archive remains independently understandable. The small component README identifies its purpose, dependencies, contents, citation, license, and the release-manifest filename. Exact outer archive facts remain only in the standalone release manifest and release summary, which avoids recursive checksums.

**Alternative considered:** put every corpus in one data archive and every project in one source archive. Rejected because those are the large payload families and independent workflows use different subsets. The smaller executable, report, and evidence payloads remain together in core.

**Alternative considered:** embed the final release manifest in every archive. Rejected because an archive cannot contain a stable manifest with its own checksum. The component manifest binds payload bytes; the standalone release manifest binds archive bytes and relationships.

### 3. Archive creation uses a proven Zip64 library and fresh candidate directories

The release declaration produces an ordered source-to-archive plan. A thin archive adapter uses the language standard library or another maintained Zip64 implementation. It streams declared files, normalizes timestamps and permissions, records payload hashes and sizes, and writes `component-manifest.json`. It does not implement ZIP records, compression, or central-directory structures.

After each archive closes, verification reads it through an independent library path. Verification checks the central directory, safe member names, manifest membership, payload checksums, wrapper root, and deterministic rebuild behavior.

Each production build uses a new versioned candidate directory. The builder never scans an old output directory and never reuses cached archives automatically. It generates the standalone release files only after all declared component archives verify. A failed candidate remains ineligible for upload and cannot change a previous accepted or published release.

Streaming avoids a second tens-of-gigabytes payload tree. A small temporary file or directory for one component is acceptable when the selected library requires it. Correctness and bounded disk use take priority over a custom format implementation.

**Alternative considered:** retain the existing copy-tree-plus-`zip` script and add more exclusion patterns. Rejected because shell globs and ambient output directories caused the completeness defect.

**Alternative considered:** implement a deterministic ZIP writer or reuse component archives by cache key. Rejected because both create new correctness mechanisms. A maintained Zip64 library and fresh release candidate are easier to verify.

### 4. Components install independently into immutable component roots

The core installer verifies one selected archive at a time against the standalone release manifest. It extracts the archive into a temporary directory, verifies every declared member, and renames that directory to `components/<component-id>` only after verification. It never merges payload trees or writes files owned by another component.

A release workspace contains the immutable release manifest, installed component directories, and disposable workflow state. The presence of one component does not cause another selected component to be skipped. Installing a component whose directory already exists succeeds only when its component manifest and payload checksums already match. Otherwise the command fails and instructs the user to remove that component directory or create a new release workspace.

Workflow preflight resolves installed component ids from their verified manifests. It reports every missing or incompatible component before starting services. The real-world full workflow requires both the sample and remainder components, so the release stores no duplicate sample payload. Components with no declared shared paths need no collision-deduplication engine.

The installer does not keep a package database, global ownership ledger, service inventory, or workspace transaction log. Existing corpus preparation records, run ledgers, Compose labels, and report manifests own mutable state. Cleanup addresses only the selected release's disposable workflow state and leaves immutable component directories intact unless the user explicitly removes them.

The old `projects/`-nonempty and `data/`-nonempty shortcuts are removed. Existing author files cannot satisfy a component dependency because workflows resolve only verified component roots.

### 5. One reviewer controller owns the public workflow

A root executable inside core provides stable commands:

```text
artifact preflight
artifact smoke
artifact reproduce-results
artifact collect-reduced <corpus-id>
artifact collect-full <corpus-id>
artifact clean
```

It delegates to focused existing helpers and owns only working-directory resolution, workflow component checks, command sequencing, error presentation, and final summaries. It does not add a workflow database, generic checkpoint store, service manager, corpus restorer, report registry, or status model. Existing component manifests, corpus preparation state, Compose state, run ledgers, and report manifests remain authoritative.

`smoke` verifies the selected components, checks requirements, starts services, restores and prepares its declared corpus, proves read-only report access, runs one representative report, and checks expected output. If existing compatible subsystem state is present, the delegated helper can reuse it. Otherwise the controller fails with the existing retry or cleanup command; it does not invent recovery state. `clean` invokes the existing release-scoped cleanup operations for disposable services, volumes, outputs, and scratch directories.

Author-only export and release-build commands are excluded from packaged reviewer guidance. Maintainer documentation may describe them in the source repository, but the core archive's primary README does not ask reviewers for SSH aliases, Docker executable paths, source-container names, or an author database.

**Alternative considered:** document the current collection of scripts directly. Rejected because several require different working directories and expose internal sequencing. A small stable controller provides one observable interface while preserving focused helpers underneath.

### 6. The primary reviewer environment is container-only above the host runtime

The release-tested baseline is Linux x86-64 with Docker Engine or Docker Desktop and Compose V2. The report analysis, PostgreSQL service, and Java collection pipeline use images pinned by immutable digest. Locked dependency installation has no permissive fallback. Image digests, container architecture, and build provenance enter the release manifest and clean-acceptance record.

The reviewer may pull declared images during setup. Once images and selected release components are present, smoke and results reproduction run without network access. The release does not archive OCI images in this version. Apple Silicon through `linux/amd64` emulation is claimed only after a separate compatibility run on the final component set. Native Windows outside WSL2 is not claimed.

Host-based `uv`, Nix, Java, or Gradle remain developer conveniences and do not appear as primary reviewer prerequisites. The Java collection container executes project builds in a disposable workspace without the Docker socket, host credentials, or writable mounts over the release source. Network access for third-party dependency resolution is isolated to the documented collection workflow.

### 7. Release provenance replaces Git only in verified packaged mode

Release assembly runs in a clean Git checkout and materializes `release-provenance.json`. For every source file that can produce a report artifact, it records repository-relative path, SHA-256, last-changing commit, source repository, and resolvable source URL template. The file itself is included in the core component manifest.

The provenance resolver has two exclusive modes:

- development mode uses Git exactly as the accepted specification requires.
- release mode first verifies the release and source-file checksum, then returns the embedded per-file revision.

Release mode is entered only from verified release metadata, not merely because `.git` is absent. Missing, extra, or mismatched provenance fails report startup. The package does not ship repository history and does not synthesize a common release commit for every source.

### 8. Report verification consumes artifact manifests, not directory scans

The results workflow compares the emitted registered `ArtifactSet` with the published reference manifest. Every report, table, CSV file, and figure is named by declared artifact identity and checked for missing, extra, and changed content.

The accepted artifact-provenance contract already requires byte-identical regeneration when source and data are unchanged. The pinned reviewer environment therefore makes deterministic report outputs, including figures and rendered Markdown, byte-identical or gives them an explicit canonical representation before hashing. A figure count is never evidence of equality. The claims declaration maps report artifacts to paper claims and supplies separate invariant/tolerance checks only for newly collected stochastic data.

Verification emits JSON for release gating and concise text for reviewers. Both derive from the same results. A non-passing required claim or output kind produces a nonzero exit status.

The claims matrix classifies each edge as direct observation, deterministic transformation, analytic derivation, external published reference, or stochastic collection. In particular, the JARVIS RQ0 comparison records the JARVIS paper/table citation, the two scorecard and twelve census fixture revisions, the two restored semantic corpora, compact value facts, selected raw value logs, CUT-PVC measurements, completion/status evidence, and every rendered RQ0 artifact. `reproduce-results` reruns the report from frozen inputs. It does not claim to repeat the original multi-day JARVIS collection. Reduced and full collection commands name exactly which logs, facts, and database rows they regenerate, and comparisons never present inspection of frozen facts as independent empirical reproduction.

### 9. Requirements and public metadata are rendered from verified facts

A release declaration carries stable prose-independent facts: component roles, workflow dependencies, supported platforms, minimum resources, expected runtime classes, network phases, DOI relationships, licenses, and paper identity. Measured archive sizes, restored database sizes, peak resources, image sizes, and execution times come from the release build and clean acceptance record.

The release builder renders compact tables for component sizes, workflow download sets, resource requirements, DOI relationships, and supported platforms. The top-level README, `REQUIREMENTS.md`, release summary, and Zenodo metadata consume those generated facts. Hand-authored sections explain purpose, limitations, reuse, security, evidence claims, and external references.

Automated documentation validation covers stable structured facts: controller commands and options, component ids, archive filenames, DOI values, checksums, sizes, supported platforms, manifest-owned paths, and internal links. Human release review checks explanatory claims, limitations, citations, and prose. The validator does not parse arbitrary prose or create a second semantic model of the documentation.

The new version DOI is reserved before the final candidate so it can be embedded consistently. The existing version DOI remains cited as version 1. `CITATION.cff` labels version and concept DOI correctly. Publication uploads the validated attempt directory without renaming or recompressing its members.

### 10. Data reuse and redistribution are release gates

Release assembly generates a data dictionary from the checked schema plus maintained semantic descriptions for tables, columns, units, null meanings, views, and exported result fields. Corpus documentation records selection, source, revision, transformation, and report use.

Every redistributed project has a third-party record containing origin URL, commit, license classifier, retained license path and checksum, and redistribution decision. Unknown or incompatible rights exclude source bytes from the archive. The record may retain only distributable identity and retrieval information. Generated tests and reports are audited for material copied from third-party sources and are covered by an explicit component license decision.

This legal inventory is separate from corpus identity. It answers whether bytes may be redistributed, not which corpus or project produced a result.

### 11. Release acceptance has fixture and real-package tiers

Fast tests build tiny synthetic component archives. They cover the three public document formats, archive verification, isolated component extraction, missing workflow dependencies, stale-output exclusion, provenance release mode, structured documentation facts, and focused failure diagnostics. Recovery and resume behavior stays in the subsystem tests that own that state.

The release gate builds the real candidate and launches a clean Linux x86-64 acceptance job from only the staged upload files. The job uses a new path and empty Docker volumes. It installs the documented smoke and all-report workflow component sets, runs smoke, reproduces all reports through the read-only role, verifies every claim summary, checks release-scoped cleanup, records measurements, and repeats results reproduction without network access. A source-checkout test cannot satisfy this gate.

Reduced and full collection are not final-candidate acceptance reruns. Their stage behavior, resource controls, and resumability remain covered by their existing subsystem tests and run ledgers. One reduced production unit for each materially different collection path is exercised from packaged components before the release candidate freezes. Apple Silicon receives one separate compatibility run only when the public release claims that support.

## Risks / Trade-offs

- **The new change overlaps unfinished corpus-publication work.** → Finish the non-destructive publication and clean-import tasks in `consolidate-evaluation-databases` first. Consume its manifest. Do not fork its implementation.
- **Archive manifests and release manifests drift into two registries.** → Component manifests describe payload bytes. The release manifest describes archive relationships. Both reference corpus/report authorities rather than restating them.
- **Exact outer archive facts are recursively self-referential.** → Keep final archive hashes and byte sizes only in the standalone release manifest and public summary. Internal component manifests bind payload facts.
- **Pinned images remain hosted outside Zenodo.** → Record immutable digests and Dockerfiles and verify the offline-after-setup path. Do not add an OCI archive without a measured availability requirement and a separate size and licensing decision.
- **The x86-64 Java stack is slow under Apple Silicon emulation.** → Make Linux x86-64 the release baseline, measure Apple compatibility separately, and do not promise the 30-minute bound on an unmeasured platform.
- **A third-party project has no redistribution license.** → Exclude its source payload and preserve only permitted provenance/retrieval data. Record the resulting full-rerun limitation rather than applying the artifact license to others' code.
- **The clean release gate is expensive.** → Use tiny fixture packages on every change and run the complete real-package gate once per release candidate. Never replace it with source tests.
- **A new DOI is embedded before publication but the deposit changes.** → Reserve the version DOI, freeze the candidate, upload the exact validated files, then verify the downloaded record. Any byte change creates a new candidate and, after publication, a new version.
- **Venue document layouts differ.** → Keep README as the complete entry point and retain plain-text requirements, status, install, and license documents. Venue submission wrappers may select documents but do not change artifact behavior or bytes.
- **A reduced collection run passes while the full run has environment-dependent failures.** → State the reduced scope precisely, retain full-run ledgers and expected failure semantics, and never present reduced execution as independent reproduction of full empirical results.
- **Compact JARVIS facts make a report runnable while their source evidence is omitted.** → Package the selected value logs and regenerated raw CUT captures with checksums and lineage; classify frozen-fact inspection separately from collection.
- **JARVIS path contracts currently disagree.** → Reconcile `data/detached/census-gen.complete` with the runner-owned completion path, and reconcile `data/jarvis-source-cache` in the CUT capture script with the fixture preparer's owned source cache before freezing evidence. A source-checkout fallback is not accepted.
- **A source snapshot records gitlinks but omits nested repositories.** → Materialize every required submodule at its recorded commit, inventory its license and payload ownership, and prove the Git-free build from staged source bytes.
- **Broad project and data directories hide unrelated or stale working state.** → Derive membership from corpus, report, and release declarations and require a path-disposition ledger with no unowned required path. Do not archive entire ignored roots as a shortcut.
- **Fine-grained archives overwhelm reviewers or duplicate small payloads.** → Keep source, runtime support, reports, compact inputs, and small evidence in core. Split only large corpus and project families that at least one documented workflow omits.
- **Many component choices make required downloads unclear.** → Publish named workflow sets with exact component lists, aggregate sizes, and copyable commands. Preflight reports the complete missing set before execution.

## Migration Plan

1. Complete the corpus export, transfer, assembly, clean import, and read-only report tasks from `consolidate-evaluation-databases`; finish, validate, and archive the report/evidence changes that define the final published artifact set. Do not begin release assembly from a partial corpus or provisional report set.
2. Freeze a declaration-derived input ledger. Resolve every `ReportSpec` corpus and file input, all producing source and submodule bytes, all project/config inputs, and every output. Reconcile the JARVIS completion and source-cache path mismatches, retain selected value logs, regenerate and retain raw CUT captures, and prove the compact evidence lineage before archive work.
3. Introduce the release declaration and schemas, then build fixture component/release manifests and atomic archive assembly without changing the published version.
4. Add embedded per-source provenance and prove report generation from a Git-free fixture release.
5. Replace shared-root archive extraction with verified component-local extraction and add the thin root reviewer controller.
6. Freeze the reviewer containers and move the primary analysis path off host `uv`. Verify smoke and results workflows on clean Linux x86-64.
7. Define claims, output comparisons, data dictionaries, third-party notices, and generated public documentation. Remove or correct every retired command and unsupported claim.
8. Build the complete candidate from the four-corpus package and all declared components. Run clean archive acceptance, capture exact measurements, and render final requirements and Zenodo metadata.
9. Reserve a new Zenodo version DOI, rebuild and revalidate the frozen candidate with that DOI, and obtain human review of licenses, claims, paper metadata, and archive selection.
10. Upload the exact validated set as a new version. Download it by version DOI, verify release checksums, and repeat smoke plus results reproduction from the downloaded release set.
11. If validation fails before publication, discard the candidate and leave version 1 untouched. If a defect is found after publication, preserve that immutable version and publish a corrected successor rather than replacing files.
