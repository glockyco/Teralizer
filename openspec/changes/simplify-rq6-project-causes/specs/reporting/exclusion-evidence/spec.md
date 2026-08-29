## ADDED Requirements

### Requirement: Project exclusion evidence avoids inferred ownership classes

The RQ6 project-level exclusion table SHALL publish each observed exclusion stage, concrete cause description, and count. It SHALL use `Cause of Project-level Exclusion` as the cause-column heading. It SHALL NOT publish an internal, external, or mixed type for a project-level row.

Removing the type SHALL NOT by itself change the row set, recorded stage, cause description, or count. When entity evidence, stage-transition evidence, or task diagnostics contradict a legacy fallback, the report SHALL correct the affected cause rows and stage attribution while preserving the eligible-project total, final inclusion count, and total project exclusions. An earlier stage transition SHALL use evidence recorded at that boundary and SHALL NOT use a final mutable entity status that a later stage can change. The table SHALL retain pipeline-stage order and SHALL order causes within each stage by descending count, then ascending cause text. Generated metrics, table cells, validation, and downstream publication artifacts SHALL use the reduced schema consistently, with no compatibility alias for the removed type.

#### Scenario: Project exclusions are rendered

- **WHEN** the RQ6 report renders the project-level exclusion table
- **THEN** each row contains its stage, cause description, and count
- **AND** neither the table nor its generated metrics contain an internal, external, or mixed type

#### Scenario: Taxonomy removal changes evidence

- **WHEN** the report migrates from the typed table to the reduced table
- **THEN** unchanged causes equal the corresponding field projection from the prior evidence
- **AND** each changed cause has entity or task-diagnostic evidence that contradicts the prior fallback
- **AND** causes within each stage appear by descending count, then ascending cause text
- **AND** generation fails if the reduced rows do not reconcile to the project funnel

#### Scenario: Later failures change final entity status

- **WHEN** an assertion survives Stage 1 + 2 filtering and a later processing failure excludes it
- **THEN** the project remains in the Stage 1 + 2 survivor set
- **AND** project exclusion is attributed to the later stage that owns the failure
- **AND** every stage survivor set remains a subset of the preceding survivor set

#### Scenario: Internal mechanisms have the same reader-facing interpretation

- **WHEN** internal mechanism combinations identify the same failed transition and do not change its interpretation
- **THEN** the report aggregates them under one established reader-facing cause
- **AND** the cause does not expose internal mechanism names
- **AND** the aggregated rows reconcile to the project-funnel exclusion total

#### Scenario: Project and entity granularity remain separate

- **WHEN** a project-cause row is explained by filter rejections, processing failures, widening refusals, or another entity mechanism
- **THEN** the project row records the failed transition and its material reader-facing cause
- **AND** filter classes, exception subtypes, internal mechanism combinations, and individual entity counts remain in dedicated generated evidence

#### Scenario: Result prose explains an aggregate cause

- **WHEN** the thesis describes a broad project-cause category
- **THEN** it gives selected evidence-backed examples that explain the principal mechanisms
- **AND** it distinguishes overlapping subtype counts from exclusive project rows
- **AND** it leaves exhaustive subtype distributions to the replication package
- **AND** it does not present an outcome code or catch-all diagnostic as a validated root cause

#### Scenario: Reduction fails on different suite sides

- **WHEN** reduction diagnostics identify failures for the initial and generalized suites
- **THEN** project-cause rows distinguish the suite side when it changes the failed operation
- **AND** both rows remain within the Stage 5 project denominator

#### Scenario: JUnit report collection has detailed diagnostics

- **WHEN** task diagnostics distinguish a missing report file from an unsupported report layout
- **THEN** generated evidence preserves both diagnostics
- **AND** the project table aggregates them as a JUnit report collection failure
- **AND** the result description MAY state the material diagnostic split

#### Scenario: A consumer expects the removed type

- **WHEN** report validation or publication encounters a metric, table declaration, or artifact that still requires the project exclusion type
- **THEN** the change remains incomplete until that consumer uses the reduced schema
- **AND** the producer does not emit a placeholder or deprecated type

### Requirement: Filtering inclusion is comparable only at the shared filtering boundary

The reporting system SHALL publish controlled and RepoReapers filtering evidence as two corpus-local observations with the same entity type and boundary. Each observation SHALL count generalized tests that have a filtering result. It SHALL partition that count into included and excluded tests and publish the included share with the filtering total as its denominator.

The controlled observation SHALL derive its boundary from explicit filtering and generation-task evidence rather than from `generalization.is_included` alone. The RepoReapers observation SHALL use the accepted real-world generalization relation and its filtering result. For each corpus, included plus excluded SHALL equal the filtering total.

The report SHALL preserve separate corpus identities, inputs, numerators, denominators, and provenance. It SHALL NOT combine the two observations into one effect size. It SHALL NOT treat either filtering denominator as the complete generalization-attempt or project population.

#### Scenario: Both corpus-local filtering observations are complete

- **WHEN** the controlled and RepoReapers inputs contain supported generalized tests with included and excluded filtering results
- **THEN** the report emits filtering total, included count, excluded count, and included share for each corpus
- **AND** each included share names its own corpus-local filtering total as denominator
- **AND** included plus excluded equals that filtering total

#### Scenario: A controlled generated test lacks a filtering result

- **WHEN** controlled evidence shows that generalized test creation or its task failed before filtering produced a result
- **THEN** that test is excluded from the controlled filtering denominator
- **AND** it is not inferred to be included or excluded from `generalization.is_included`

#### Scenario: Corpus-local results are interpreted together

- **WHEN** downstream RQ6 analysis compares the two included shares
- **THEN** the evidence supports only the bounded finding that filtering includes a similar proportion in both settings
- **AND** it does not represent overall generalization success, project applicability, a paired-project effect, or a causal estimate

### Requirement: Test-type explanations match recorded declarations

The registered RQ6 report SHALL publish provenance-backed counts for each recorded declaration category rejected by `TestType`. The categories SHALL distinguish unsupported execution models from declaration-resolution failures, and their counts SHALL sum to the `TestType` rejection population.

The thesis SHALL NOT describe an inherited JUnit test resolved to an overridden declaration as an unsupported JUnit test type. It SHALL distinguish that declaration-resolution limitation from unsupported JUnit theories and TestNG tests.

#### Scenario: TestType rejects several declaration mechanisms

- **WHEN** `TestType` rejects JUnit theories, inherited tests resolved to overridden declarations, and TestNG tests
- **THEN** the report publishes a separate count for each category
- **AND** the category counts sum to the `TestType` rejection count
- **AND** the thesis describes each mechanism with its recorded semantics

### Requirement: Oracle-recovery directions preserve distinct mechanisms

Reader-facing improvement prose SHALL distinguish assertion recognition, tested-method identification, and assertion-to-MUT mapping. Support for interprocedural assertions or additional assertion libraries SHALL NOT be presented as a repair for tested-method identification or assertion-to-MUT mapping.

The thesis MAY propose an explicit `does not throw` oracle for tests with no explicit assertion because a passing test already requires no unexpected exception. It SHALL present this as an unimplemented extension, preserve framework-declared expected-exception semantics, and SHALL NOT convert the reconstruction estimate into a measured recovery count.

#### Scenario: A test has no explicit assertion

- **WHEN** future-work prose proposes a `does not throw` oracle
- **THEN** it identifies the proposal as an extension of the current implementation
- **AND** it requires expected-exception behavior to remain distinct
- **AND** it does not claim that the extension recovers a measured number of tests

#### Scenario: An assertion becomes recognizable

- **WHEN** interprocedural analysis or assertion-library support exposes an assertion
- **THEN** the prose still identifies tested-method identification and assertion-to-MUT mapping as separate required improvements
- **AND** it does not treat assertion recognition as proof that a sound generalization candidate exists

### Requirement: Stage-local conclusions retain their population boundary

A reader-facing Stage 4 conclusion SHALL name the projects or generalization attempts that reach that boundary. It MAY identify widening refusal as the most frequent recorded Stage 4 exclusion mechanism. It SHALL NOT infer that fixing widening would recover the most projects or preserve the same distribution after earlier-stage support changes.

The established project-completion framing remains survivor-based: a project completes a stage when at least one relevant entity crosses its boundary. Failures of other entities in that project do not negate the completed transition.

#### Scenario: Widening dominates recorded Stage 4 exclusions

- **WHEN** widening refusal has the largest recorded count among Stage 4 exclusion mechanisms
- **THEN** the thesis scopes the statement to the population that enters Stage 4
- **AND** it does not rank expected project recovery across stages

#### Scenario: A surviving project also has entity failures

- **WHEN** at least one entity crosses every stage boundary while other entities fail
- **THEN** the project counts as completing all five stages
- **AND** the thesis does not require every entity or task in that project to succeed

### Requirement: Entity-level RQ6 revisions preserve the established source narrative

The thesis SHALL use the TOSEM source in the thesis `projects/teralizer-paper` submodule as the narrative baseline for the `Test, Assertion, and Generalization Exclusions` results and their later interpretation in the Chapter 5 discussion, Chapter 6 reflections, and Chapter 7 conclusion. A revision SHALL preserve established wording, paragraph order, and narrative progression unless the current corpus, generated values, pipeline semantics, or accepted interpretation requires a change. The RQ6 introduction and `Project-Level Exclusions` results SHALL remain unchanged.

Every restored numerical claim SHALL trace to registered metrics, generated reports, or an existing reconstruction audit for the accepted RQ6 population. When the source narrative requires a quantity that those owners do not publish, the change SHALL register provenance-backed evidence for that population before publishing the claim. It SHALL NOT omit or weaken the claim only to avoid the evidence work.

Within the immediate discussion of the filtering-results table, the thesis SHALL identify an entry by its filter name and SHALL NOT add a generated row reference for that entry. A claim outside that context MAY use a row reference when the filter name and population are not otherwise established. A reference to an unnumbered heading SHALL NOT use a bare `\cref` when the compiled reference resolves to a broader numbered parent. The prose SHALL name the topic and, when a citation is needed, use a useful numbered section or table owner.

#### Scenario: Frozen RQ6 text is encountered

- **WHEN** a revision compares the thesis with the TOSEM source
- **THEN** it excludes the RQ6 introduction and `Project-Level Exclusions` results from the mutable scope
- **AND** it does not change their wording, structure, or references

#### Scenario: Existing evidence supplies an updated quantity

- **WHEN** the mutable RQ6 text needs a quantity that already exists in a registered metric, generated table, or reconstruction audit
- **THEN** the thesis uses that evidence
- **AND** the implementation does not create a duplicate analysis or sample

#### Scenario: A required quantity is missing

- **WHEN** supported source wording requires a quantity that is absent from the accepted RQ6 evidence
- **THEN** the reporting system registers provenance-backed evidence for the accepted RQ6 population before publication
- **AND** the thesis does not avoid the evidence gap by dropping or weakening the claim

#### Scenario: Source wording remains supported

- **WHEN** the TOSEM wording remains accurate for the current population and interpretation within the mutable scope
- **THEN** the thesis preserves that wording and its narrative position
- **AND** it does not replace the wording only for stylistic variation

#### Scenario: Current evidence requires a departure

- **WHEN** current populations, values, pipeline semantics, or accepted interpretation contradict the TOSEM wording
- **THEN** the thesis preserves the evidence-required correction
- **AND** it does not restore the contradicted source claim

#### Scenario: A filter is discussed beside its result table

- **WHEN** the surrounding RQ6 paragraph establishes the filtering-results table and names the filter
- **THEN** the filter name uniquely identifies the result
- **AND** the paragraph does not cite the generated table row

#### Scenario: Later prose names an unnumbered topic

- **WHEN** later interpretation must refer to an unnumbered subsubsection or paragraph
- **THEN** the prose names the topic directly
- **AND** any cross-reference identifies a useful numbered section or table rather than the broader parent number alone

## REMOVED Requirements

### Requirement: Filtering retention is comparable only at the shared filtering boundary

**Reason**: `Retained` is not the approved outcome at the filtering-result boundary. The replacement requirement uses `included` and `excluded` without changing the corpus-local comparison semantics.

**Migration**: Rename filtering-outcome symbols, metrics, generated artifacts, accepted contracts, and prose to `included`. Do not change unrelated retention or test-suite-reduction terminology.

## MODIFIED Requirements

### Requirement: Filtering comparison artifacts use established thesis terms

Generated tables, metric labels, macro documentation, provenance, handoff records, accepted specifications, and thesis prose SHALL describe this boundary with **filtering**, **filter results**, **included**, **excluded**, and **generalized tests**. They SHALL use `Accept`, `Defer`, and `Reject` only for individual filter verdicts. They SHALL NOT use `retained` as a filtering outcome or introduce a new reader-facing process term.

#### Scenario: Filtering comparison artifacts are rendered

- **WHEN** the registered report publishes the comparison and its aggregate macros
- **THEN** reader-facing labels use the established filtering vocabulary
- **AND** producer symbols and metric identities use the corresponding included outcome
- **AND** unrelated retention and test-suite-reduction terms remain unchanged