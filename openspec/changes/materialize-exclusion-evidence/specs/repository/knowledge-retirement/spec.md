## Purpose

Define the evidence required before a maintained knowledge source is retired so that cleanup cannot
silently remove a current contract, observation, or reproducible explanation.

## ADDED Requirements

### Requirement: Retirement uses claim-level disposition

Before a maintained knowledge source is removed, every substantive claim it contains SHALL receive
exactly one disposition: durable contract, executable fact, empirical result, qualitative evidence,
operator instruction, stale or disproven claim, or intentionally discarded material.

A file-level label or an inventory of inbound references SHALL NOT substitute for claim-level
disposition.

#### Scenario: One source mixes several knowledge classes
- **WHEN** a source contains behavioral rules, measured values, implementation observations, and an
  empirical audit
- **THEN** each claim is dispositioned independently
- **AND** removal does not rely on one disposition for the whole source

#### Scenario: A source has no current inbound reference
- **WHEN** a maintained source contains a unique current claim but no other file references it
- **THEN** the claim still requires a current owner or an explicit stale or discarded disposition

### Requirement: A replacement owner is verified before removal

A claim assigned to a replacement owner SHALL be retrievable from that owner and SHALL be shown to
produce or enforce the claimed behavior or evidence before the original source is removed. A path,
name, comment, proposal, or intended future task alone SHALL NOT count as a verified replacement.

#### Scenario: Empirical output is assigned to a report
- **WHEN** a measured count, rate, distribution, or funnel is assigned to a registered report
- **THEN** the report emits that result from its declared corpus with provenance
- **AND** a focused check demonstrates that the emitted result reconciles with its source population

#### Scenario: A contract is assigned to a capability
- **WHEN** a durable behavior is assigned to an accepted capability contract
- **THEN** the complete behavior appears as a normative requirement with a verifiable scenario

#### Scenario: A fact is assigned to executable behavior
- **WHEN** an implementation fact is assigned to source, configuration, or tests
- **THEN** the executable owner can be exercised or inspected without reconstructing the fact from
  historical narrative

### Requirement: Empirical audits retain reproducibility evidence

A qualitative or sampled empirical audit retained as current evidence SHALL identify its population,
selection procedure, selected entities, corpus identity, source revision, observations, labels, and
review provenance. An audit that lacks those fields SHALL NOT support a current empirical claim.

#### Scenario: A sampled causal audit is retained
- **WHEN** a report uses manually reviewed cases to explain an observed outcome
- **THEN** another reviewer can recover the same cases and the source state that was inspected
- **AND** the report distinguishes the sampled observation from a prevalence estimate

#### Scenario: A historical audit omitted sampled identities
- **WHEN** an earlier audit records only aggregate notes or an unseeded random selection
- **THEN** it is marked unreproducible
- **AND** it is either repeated with retained evidence or excluded from current claims

### Requirement: Stale and discarded claims are explicit

A claim that is not retained SHALL record whether current executable evidence disproves it, a corpus
change invalidates it, its evidence cannot be reproduced, or it has no current consumer. The
retirement SHALL NOT present an unverified claim as safely derivable.

#### Scenario: Current behavior contradicts retired prose
- **WHEN** a focused execution or current corpus observation disagrees with the source being retired
- **THEN** the disposition records the source claim as stale or disproven
- **AND** no replacement repeats that claim as current behavior

### Requirement: Retirement is guarded at the causal boundary

Repository validation SHALL reject final retirement while a claim lacks a verified disposition. Once
all dispositions are verified, the retirement and its active-reference removal SHALL land in a
causally scoped commit without requiring existing commit history to be rewritten.

#### Scenario: Replacement work follows an earlier deletion commit
- **WHEN** the deletion already exists in local or shared history
- **THEN** the repository restores knowledge through new causal commits
- **AND** validation does not require reset, rebase, amend, or force-push

#### Scenario: A replacement remains planned but absent
- **WHEN** a retirement names a future report, capability, or audit that does not yet exist
- **THEN** the retirement gate remains unsatisfied
