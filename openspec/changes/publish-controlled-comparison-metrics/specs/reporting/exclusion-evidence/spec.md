## ADDED Requirements

### Requirement: Cross-corpus comparisons declare a normalized evidence mapping

A retained controlled-versus-real-world comparison SHALL define one normalized measure before it emits
values. For each corpus, the mapping SHALL identify the source relation and fields, writer semantics,
variant, eligibility rule, entity population, numerator predicate, denominator predicate, lifecycle
boundary, and query provenance.

A mapping SHALL be classified as:

- **exact** when both source shapes directly encode the normalized measure;
- **qualified** when the sources support the same bounded measure but a documented population or
  instrumentation difference limits interpretation; or
- **unmappable** when more than one source interpretation remains plausible or required evidence is
  absent.

A qualified mapping SHALL publish its qualification with the comparison. An unmappable mapping SHALL
emit no comparison value and SHALL stop a declared downstream publication rather than selecting a
convenient field or inferring missing lifecycle state.

#### Scenario: A legacy inclusion flag has several possible RQ6 counterparts

- **WHEN** the controlled schema records one terminal `generalization.is_included` flag while the
  real-world schema records attempted, emitted, filter-passed, validated, and final-usable states
- **THEN** matching names or current equal counts do not establish equivalence
- **AND** executable writer semantics and corpus invariants must identify one normalized lifecycle
  boundary before the mapping is exact or qualified
- **AND** the unresolved case is presented for operator decision rather than silently mapped

#### Scenario: The corpora use different eligibility rules

- **WHEN** controlled and real-world denominators select projects or attempts under different eligibility
  conditions
- **THEN** each denominator retains its own generated identity and population description
- **AND** the comparison is descriptive and qualified unless evidence proves a stronger paired design

### Requirement: Approved comparison metrics come from one registered implementation

After the mapping is approved, one registered comparison implementation SHALL read both declared corpus
inputs and emit numerator, denominator, and share metrics for each side with stable semantic keys. Each
metric SHALL identify its corpus, `Generalization` population, value kind, denominator where applicable,
normalized mapping identity, and resolvable query provenance.

The implementation SHALL validate mapping invariants before emitting metrics. Existing RQ5 and RQ6
presentation tables MAY use different source shapes; agreement with a table SHALL be checked only when
that table represents the approved normalized measure.

#### Scenario: A mapped comparison is emitted

- **WHEN** controlled and real-world source signals have an approved exact or qualified mapping
- **THEN** the registered report emits numerator, denominator, and share metrics for both corpora
- **AND** both shares name their own denominator keys
- **AND** the generated comparison records the mapping classification and interpretation bound

#### Scenario: A mapping invariant fails

- **WHEN** a corpus contains a source state outside the approved mapping or the mapped populations do not
  conserve their denominators
- **THEN** report generation fails naming the corpus, mapping identity, and contradictory evidence
- **AND** no comparison macro or provenance entry is published

### Requirement: Aggregate comparison macros preserve mapping identity

The aggregate macro artifact SHALL render only approved comparison metrics. Downstream consumers SHALL
be able to resolve each value to its normalized measure, corpus-specific mapping, denominator, and
provenance without relying on a table ordinal, displayed row text, or handwritten value.

#### Scenario: Aggregate macros are published

- **WHEN** a complete registered report run renders an approved cross-corpus comparison
- **THEN** it includes macros for both numerators, denominators, and shares
- **AND** a downstream consumer can state the mapping qualification without reconstructing it from the
  database or report presentation
