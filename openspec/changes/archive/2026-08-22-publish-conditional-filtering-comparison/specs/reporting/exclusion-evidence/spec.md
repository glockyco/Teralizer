## ADDED Requirements

### Requirement: Filtering retention is comparable only at the shared filtering boundary

The reporting system SHALL publish controlled and RepoReapers filtering-retention evidence as two corpus-local observations with the same entity type and boundary. Each observation SHALL count generalized tests that have a filtering result, partition that count into retained and excluded tests, and publish the retained share with the filtering total as its denominator.

The controlled observation SHALL derive its boundary from explicit filtering and generation-task evidence rather than from `generalization.is_included` alone. The RepoReapers observation SHALL use the accepted real-world generalization relation and its filtering result. For each corpus, retained plus excluded SHALL equal the filtering total.

The report SHALL preserve separate corpus identities, inputs, numerators, denominators, and provenance. It SHALL NOT combine the two observations into one effect size or treat either filtering denominator as the corpus's complete generalization-attempt or project population.

#### Scenario: Both corpus-local filtering observations are complete

- **WHEN** the controlled and RepoReapers inputs contain supported generalized tests with retained and excluded filtering results
- **THEN** the report emits filtering total, retained count, excluded count, and retained share for each corpus
- **AND** each retained share names its own corpus-local filtering total as denominator
- **AND** retained plus excluded equals that filtering total

#### Scenario: A controlled generated test lacks a filtering result

- **WHEN** controlled evidence shows that generalized test creation or its task failed before filtering produced a result
- **THEN** that test is excluded from the controlled filtering denominator
- **AND** it is not inferred to be retained or excluded from `generalization.is_included`

#### Scenario: Corpus-local results are interpreted together

- **WHEN** downstream RQ6 analysis compares the two retained shares
- **THEN** the evidence supports only the bounded finding that filtering retains a similar proportion in both settings
- **AND** it does not represent overall generalization success, project applicability, a paired-project effect, or a causal estimate

### Requirement: Filtering comparison artifacts use established thesis terms

Generated tables, metric labels, macro documentation, and handoff records SHALL describe this boundary using the established terms **filtering**, **filter results**, **retained**, **excluded**, and **generalized tests**. They SHALL NOT introduce a new reader-facing lifecycle term for the filtering step.

#### Scenario: Filtering comparison artifacts are rendered

- **WHEN** the registered report publishes the comparison and its aggregate macros
- **THEN** reader-facing labels use the established filtering vocabulary
- **AND** internal storage or implementation terms do not appear as replacement thesis terminology
