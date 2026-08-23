## Purpose

Defines stable, provenance-bearing evidence for a small set of thesis-level Teralizer result dimensions without turning internal report stages into reader-facing claims.

## ADDED Requirements

### Requirement: Headline evidence has four distinct dimensions

The registered report set SHALL expose headline evidence for effectiveness, applicability, demonstrated real-world output, and mechanism-level applicability insight. Effectiveness and applicability SHALL be identified as primary dimensions. Real-world output and mechanism insight SHALL remain supporting dimensions.

The producer SHALL NOT combine the dimensions into one score or select thesis wording on behalf of the downstream thesis repository.

#### Scenario: A downstream consumer discovers headline evidence
- **WHEN** a complete registered report run publishes aggregate metrics
- **THEN** each of the four dimensions resolves to stable semantic metric keys
- **AND** effectiveness and applicability are distinguishable from supporting output and mechanism evidence
- **AND** no composite headline score is emitted

### Requirement: Effectiveness evidence preserves cohort and selection semantics

Mutation-score headline metrics SHALL expose the minimum and maximum absolute improvement for each declared evaluation cohort from the same RQ1 result used by the generated figure. The cohorts SHALL distinguish EvoSuite-generated EqBench tests, EvoSuite-generated Apache Commons tests, and developer-written Apache Commons tests.

The range SHALL be computed over the declared generalized variants and evaluated budgets after the report validates the expected project, variant, and baseline rows. The producer SHALL expose the developer-written baseline mutation score separately. It SHALL fail rather than publish a range from a missing, duplicate, or silently narrowed cohort.

#### Scenario: RQ1 headline ranges are published
- **WHEN** the RQ1 report receives its complete expected result frame
- **THEN** it emits typed minimum and maximum absolute-improvement metrics for every declared cohort
- **AND** the metrics share the RQ1 query provenance and identify the cohort selection
- **AND** aggregate macros expose the same values

#### Scenario: A required RQ1 row is absent
- **WHEN** a cohort lacks an expected baseline, project, budget, or generalized variant row
- **THEN** report construction fails before publishing headline metrics
- **AND** it does not compute a plausible range from the remaining rows

### Requirement: Applicability evidence reuses explicit project populations

Applicability headline evidence SHALL reuse the existing RQ0 comparison populations and the existing RQ6 end-to-end project population. It SHALL expose the intended JARVIS benchmark project denominator, the project counts for which each approach reports or produces a generalized test, the eligible RepoReapers project denominator, and the projects completing the full pipeline through reduction.

The producer SHALL NOT derive a composite applicability percentage across the JARVIS and RepoReapers evaluations.

#### Scenario: Applicability metrics are published
- **WHEN** RQ0 and RQ6 complete in one registered report run
- **THEN** their existing project counts, denominators, shares, corpus inputs, and provenance remain available as aggregate metrics and macros
- **AND** each rate identifies its own project population

### Requirement: Real-world output evidence names completed generalized tests

The RQ6 report SHALL expose the count of `IMPROVED_200_TRIES` generalizations that satisfy the accepted final-usable lifecycle predicate and the count of projects containing those generalizations. Both metrics SHALL come from the same eligible corpus snapshot and SHALL agree with the end-to-end applicable project population.

The producer SHALL preserve `final_usable` as the semantic evidence identity. Reader-facing terminology is outside this capability.

#### Scenario: Real-world output is published
- **WHEN** RQ6 lifecycle evidence is complete and internally consistent
- **THEN** aggregate metrics and macros expose final-usable generalizations and their distinct project count
- **AND** the project count equals the accepted end-to-end applicable project count

#### Scenario: Lifecycle and project populations disagree
- **WHEN** final-usable generalizations occur in a project outside the accepted end-to-end population or an applicable project has no final-usable generalization
- **THEN** report construction fails before publication

### Requirement: Mechanism insight remains denominator explicit

The RQ6 report SHALL expose assertion survival into the accepted assertion population and widening refusals among generalization attempts as separate mechanism-level observations. Each count and share SHALL name its entity population and denominator. Widening-refusal share SHALL use all eligible `IMPROVED_200_TRIES` generalization attempts as its denominator; branch shares SHALL continue to use total widening refusals.

The producer SHALL NOT turn these observations into a causal ranking or reader-facing explanation.

#### Scenario: Mechanism evidence is published
- **WHEN** RQ6 completes with a conserved assertion partition and typed widening-refusal rows
- **THEN** aggregate metrics and macros expose assertion survival count/share and widening-refusal count/share
- **AND** widening-refusal count plus all other attempt outcomes conserves the generalization-attempt denominator

### Requirement: Headline evidence is provenance bearing

Every headline metric SHALL carry value kind, population, input role, source provenance, and numerator/denominator relations where applicable. Aggregate macros and the provenance manifest SHALL preserve the same semantic keys and values.

#### Scenario: Headline artifacts are generated
- **WHEN** a complete registered report set publishes LaTeX macros and provenance
- **THEN** every headline metric appears exactly once in the manifest
- **AND** every metric approved for thesis consumption appears exactly once in the aggregate macro artifact
- **AND** values are computed from report results rather than copied as literals

### Requirement: Cross-corpus generalization rates are not headline evidence

The producer SHALL NOT publish a controlled-versus-RepoReapers generalization-success comparison as a headline metric. Controlled RQ5 predates the widening license and lacks the telemetry needed to reconstruct a current-policy verdict. Attempt-level and post-emission rates MAY remain corpus-local diagnostic evidence.

#### Scenario: Headline evidence is selected
- **WHEN** aggregate headline metrics are assembled
- **THEN** no metric pairs controlled `is_included` with a RepoReapers lifecycle state
- **AND** no metric presents 83.8%/30.2% or 84.0%/78.5% as a cross-condition effect

### Requirement: Reader-facing wording requires a downstream review gate

Producer publication SHALL record that the four dimensions are evidence inputs, not approved abstract prose. A downstream thesis change SHALL conduct a separate planning and wording-review session before modifying abstract or repeated summary passages, and SHALL preserve the producer metric identities and denominators during that review.

#### Scenario: Producer evidence is handed to the thesis
- **WHEN** the headline metrics and generated artifacts have been validated and archived
- **THEN** the handoff identifies all four dimensions and their stable metric keys
- **AND** it requires a separate thesis wording decision before prose cutover
