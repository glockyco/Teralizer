## ADDED Requirements

### Requirement: Retained controlled comparisons expose semantic metrics

A controlled result retained for comparison with real-world evidence SHALL expose its numerator,
denominator, and share as registered metrics with stable semantic keys. Each metric SHALL identify the
controlled corpus, compatible entity population, value kind, denominator where applicable, and
resolvable query provenance.

The metrics and any rendered table presentation of the same result SHALL derive from one fetched result.
The aggregate macro artifact SHALL render the registered metrics so consumers do not depend on a table
ordinal, displayed row text, or handwritten value.

#### Scenario: A consumer compares controlled and real-world inclusion

- **WHEN** the retained comparison uses `Improved (200 tries)` generalization inclusion in both corpora
- **THEN** the controlled report emits the included count, total count, and inclusion share as stable
  metrics for the `Generalization` population
- **AND** the share names the total-count metric as its denominator
- **AND** all three metrics resolve to the controlled corpus and the report query provenance

#### Scenario: The controlled table is rendered

- **WHEN** the RQ5 breakdown table includes the retained controlled generalization row
- **THEN** its total, included count, and included share agree with the registered comparison metrics
- **AND** the values are not fetched or calculated independently for the metric and table paths

#### Scenario: Aggregate macros are published

- **WHEN** a complete registered report run renders the aggregate macro artifact
- **THEN** it includes macros for the controlled numerator, denominator, and share metric keys
- **AND** a downstream consumer can cite the comparison without a table-position lookup or prose literal
