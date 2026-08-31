## ADDED Requirements

### Requirement: A LaTeX table artifact is a reusable body fragment

A generated LaTeX table artifact MUST contain the complete tabular body needed to present its report data. It MUST preserve generated columns, headers, cells, grouping, rules, composite-cell layout, stable row anchors, and visible row ordinals.

The artifact MUST NOT open or close a float environment. It MUST NOT emit a caption, short caption, float label, placement option, manuscript-local spacing, font size, rotation, or page-level sizing command.

Width-sensitive constructs inside the fragment MUST use `\linewidth` rather than `\textwidth`. This requirement applies to full-width tabular layouts and table-spanning rows.

#### Scenario: A LaTeX table is rendered

- **WHEN** a report emits a LaTeX table artifact
- **THEN** the artifact starts and ends at the table-body boundary
- **THEN** it contains no `table`, `table*`, `sidewaystable`, or other float environment
- **THEN** it contains no float caption, short caption, float label, or placement option

#### Scenario: A body uses the available container width

- **WHEN** a generated body stretches or measures content against its container
- **THEN** it uses `\linewidth`
- **THEN** the same fragment can render inside different manuscript containers without a generated page-width assumption

#### Scenario: A table has citable rows

- **WHEN** a generated fragment declares citable row numbering
- **THEN** its visible ordinals and semantic row labels remain in the fragment
- **THEN** each row label continues to derive from the table key and semantic row key
- **THEN** no row label depends on the manuscript-owned float label

### Requirement: Each consuming document owns table float presentation

A consuming document MUST provide the wrapper for each generated table fragment that it includes. The wrapper owns the float environment, caption, short caption, float label, placement, sizing, rotation, local style, and surrounding composition.

A wrapper MUST NOT copy, replace, or alter generated headers, cells, grouping, rules, or row identities. If a consumer needs a different population, column set, aggregation, or semantic row structure, the report MUST emit a separate generated artifact.

#### Scenario: Paper and thesis present one table differently

- **WHEN** the paper and thesis declare the same generated table artifact
- **THEN** both receive byte-identical fragments
- **THEN** each document can use a different wrapper, caption, label, placement, and local style
- **THEN** neither consumer edits the delivered fragment

#### Scenario: A consumer needs different evidence content

- **WHEN** a consumer needs different rows, columns, populations, or aggregations
- **THEN** the report defines a distinct generated artifact
- **THEN** the consumer does not implement the difference in its float wrapper

#### Scenario: A manuscript references a generated row

- **WHEN** a manuscript wrapper gives the float a document-specific label and its prose references a generated semantic row label
- **THEN** the float reference resolves through the manuscript label
- **THEN** the row reference resolves through the unchanged generated row anchor

### Requirement: Report prose does not become consumer float prose

A report MAY retain target-neutral prose that describes a table for generated reports and provenance. The LaTeX body-fragment renderer MUST NOT emit that prose as a consumer caption or note outside the tabular body.

#### Scenario: A report describes a table

- **WHEN** a table has target-neutral descriptive prose
- **THEN** report targets can present that prose according to their own contracts
- **THEN** the consumer LaTeX fragment contains only the generated table body

#### Scenario: A manuscript caption states a fact

- **WHEN** a manuscript supplies a publication-specific caption
- **THEN** the caption is outside the generated fragment
- **THEN** its factual claims remain subject to the report and provenance evidence
