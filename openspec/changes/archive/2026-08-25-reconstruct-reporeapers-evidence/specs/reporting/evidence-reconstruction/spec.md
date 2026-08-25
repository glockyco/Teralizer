## Purpose

Defines how RepoReapers evidence is recovered from preserved measurements without executing the analysis pipeline or changing the canonical corpus.

## ADDED Requirements

### Requirement: RepoReapers reconstruction is observation-only

Evidence reconstruction SHALL read only collected databases, logs, source checkouts, run roots, configurations, and generated artifacts. It SHALL NOT execute Teralizer, a corpus runner, a project build, a failed task, or a measurement retry.

A reconstruction command SHALL fail before analysis if its requested operation can create a new pipeline observation.

#### Scenario: Reconstruction requests a failed project retry

- **WHEN** an operator requests reconstruction that would rerun a project or failed task
- **THEN** the command refuses the request before project execution
- **AND** it identifies the prohibited operation

### Requirement: Version 7 is the only RepoReapers corpus

Every RepoReapers population, numerator, denominator, and entity classification SHALL derive from the corpus id that resolves to `postgres_reporeapers_rq6_v7`. Reconstruction SHALL NOT read an older RepoReapers database or use it as evidence.

#### Scenario: A current quantity selects an older database

- **WHEN** reconstruction or report input assigns a current RepoReapers quantity to a database other than version 7
- **THEN** validation fails before publication
- **AND** it identifies the noncanonical database

### Requirement: Every reconstruction starts from a verified evidence manifest

The reconstruction SHALL use a versioned manifest that identifies every collected evidence source by logical role and content identity. The manifest SHALL record the version 7 database dump, project logs, project revisions, run roots, configurations, generated artifacts, and producer revisions.

Each file source SHALL have a stable digest. The version 7 database source SHALL have its recorded dump digest and observed project count. Each project source SHALL have its project root and Git revision where available. A machine path MAY locate a source during acquisition, but it SHALL NOT serve as evidence identity.

#### Scenario: An archived source has no digest

- **WHEN** a reconstruction input lacks a stable content identity
- **THEN** reconstruction refuses to use it as supporting evidence
- **AND** the manifest reports the missing identity

#### Scenario: A project checkout differs from its database revision

- **WHEN** the preserved checkout revision does not match the project revision recorded by the selected evidence
- **THEN** the source is marked incompatible for that entity
- **AND** its content is not used to support the classification

### Requirement: Reconstruction records entity-level decisions

Each reconstructed decision SHALL identify its claim, population, entity, source evidence, method, label, reviewer state, and rationale. Entity identity SHALL include the semantic corpus, project root, project revision, and stable test or assertion identity where applicable.

A manual label SHALL distinguish direct observation from inference. An inferred label SHALL NOT support an exact corpus count unless an accepted method validates that inference for the full population.

#### Scenario: A reviewer classifies a hidden assertion

- **WHEN** source inspection finds an assertion in a reachable helper method
- **THEN** the decision records the helper location and call path
- **AND** it labels the filter decision as a false positive under the declared classification rule

#### Scenario: Source evidence is ambiguous

- **WHEN** the collected source does not distinguish two valid labels
- **THEN** the decision remains unresolved
- **AND** it does not enter either label count

### Requirement: Reconstruction covers the three retained evidence questions

The reconstruction SHALL attempt to resolve these questions from version 7 evidence:

1. Which version 7 `NoAssertions` rejections are true positives, false positives, or unresolved?
2. Which version 7 assertion-to-MUT resolutions fail because the collected specification evidence is insufficient?
3. Which version 7 project exclusions result from default output-directory assumptions rather than absent build artifacts?

Each question SHALL declare its population and classification rules before adjudication. A result SHALL preserve unresolved entities and incompatible evidence as separate outcomes.

#### Scenario: A question has complete supporting evidence

- **WHEN** every counted entity has compatible evidence and a resolved classification
- **THEN** reconstruction emits the count, denominator, decisions, and verification totals

#### Scenario: A question has only partial evidence

- **WHEN** some population entities lack compatible collected evidence
- **THEN** reconstruction reports resolved and unresolved counts separately
- **AND** it does not extrapolate a complete-population count

### Requirement: Unsupported claims become explicit evidence gaps

Each target claim SHALL end in exactly one status: `supported`, `partially-supported`, `contradicted`, or `evidence-gap`. The status SHALL include the checked sources and the reason for the decision.

An `evidence-gap` SHALL replace an unsupported numeric claim in downstream consumers. It SHALL NOT trigger a rerun, a retry, a value estimate, or a substitution from another corpus version.

#### Scenario: Collected evidence contradicts a prose value

- **WHEN** the verified reconstruction produces a different value from the retained prose
- **THEN** the claim is marked `contradicted`
- **AND** the collected evidence value and population are recorded

### Requirement: Accepted reconstruction results are versioned audit inputs

A supported or partially supported reconstruction SHALL produce a normalized, versioned audit input. The input SHALL contain its manifest identity, population definition, entity decisions, summary totals, unresolved totals, and reconstruction status. A sample estimate used by a downstream report SHALL be stored as structured estimate data with its value, confidence bounds, estimator, and confidence method. A report SHALL NOT recover those values by parsing narrative rationale.

A registered report MAY consume this audit input together with version 7. It SHALL verify the audit schema and reconciliation totals. It SHALL NOT query remote logs or remote project state during report execution.

#### Scenario: A report consumes reconstructed evidence

- **WHEN** a registered report declares the normalized audit input
- **THEN** provenance records the committed input identity and its validated upstream identities
- **AND** report totals reconcile with the audit input

#### Scenario: An audit input changes without a schema revision

- **WHEN** labels, population identity, or upstream evidence identities change incompatibly
- **THEN** validation refuses the input
- **AND** it requires an explicit version and review
