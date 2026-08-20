## Purpose

A report table is defined once and rendered to LaTeX, markdown, and CSV. This capability fixes what
the definition may hold and what each target must produce, so that no target receives markup meant
for another.

## ADDED Requirements

### Requirement: A table model holds values, never presentation

A table column MUST declare the kind of value it holds. A cell MUST hold the value itself, and MUST
NOT hold a rendered string, a markup fragment, or a macro.

#### Scenario: A column is declared

- **WHEN** a report declares a column
- **THEN** it states the kind of value the column holds
- **AND** it does not state how any target should display it

#### Scenario: A cell would carry markup

- **WHEN** a cell value contains markup for a specific target
- **THEN** the model is wrong, and the markup belongs in that target's renderer

#### Scenario: One value, three targets

- **WHEN** the same table is rendered to LaTeX, markdown, and CSV
- **THEN** every target derives its own text from the same stored value
- **AND** no target needs a second column to recover the value

### Requirement: A named entity is stored as a reference and rendered per target

A tool, a dataset, or a generalization variant MUST be stored as an entity reference. Each target MUST
render it in its own vocabulary, from one shared definition per entity.

#### Scenario: An entity appears in a cell

- **WHEN** a cell refers to a tool, a dataset, or a variant
- **THEN** LaTeX renders the thesis macro for it
- **AND** markdown and CSV render its plain name

#### Scenario: An entity appears in a caption or note

- **WHEN** prose refers to an entity
- **THEN** it refers to it by the same placeholder mechanism the prose uses for metrics
- **AND** each target substitutes its own rendering

#### Scenario: An entity gains a target

- **WHEN** a new render target is added
- **THEN** each entity's rendering for it is defined in one place

### Requirement: CSV output is machine-readable data

CSV MUST contain values a consumer can parse without knowing how the thesis displays them. A numeric
column MUST hold a bare number with no digit grouping and no unit suffix. A share MUST hold its
numeric value. A missing value MUST be an empty field.

#### Scenario: A count is exported

- **WHEN** a count is written to CSV
- **THEN** the field holds the digits alone, with no grouping separator
- **AND** the field needs no quoting

#### Scenario: A share is exported

- **WHEN** a share is written to CSV
- **THEN** the field holds its numeric value
- **AND** it carries no percent sign

#### Scenario: A missing value is exported

- **WHEN** a value is absent
- **THEN** the CSV field is empty
- **AND** it holds no dash or placeholder character

#### Scenario: Two numeric columns sit in one row

- **WHEN** a row holds more than one numeric column
- **THEN** all of them are formatted the same way

#### Scenario: A consumer reads a file

- **WHEN** a CSV file is read by a plotting or analysis tool
- **THEN** every numeric column parses as a number without preprocessing

### Requirement: Markdown output is readable plain text

Markdown MUST be readable as text. It MUST contain no LaTeX macro, no escaped LaTeX character, and no
markup that only a typesetter resolves.

#### Scenario: A report is rendered to markdown

- **WHEN** any report is rendered
- **THEN** its markdown contains no backslash-prefixed macro in a header, a cell, a caption, or a note

#### Scenario: A number is displayed

- **WHEN** a count or a share is displayed in markdown
- **THEN** it is grouped and suffixed for a human reader
- **AND** a missing value is shown as a dash

#### Scenario: An identifier is displayed

- **WHEN** a cell holds a code identifier
- **THEN** markdown marks it as code in markdown's own syntax

### Requirement: Separating values from presentation changes no output

Moving presentation out of the model MUST leave every target's output byte-identical for the same
reviewed source revision and corpus inputs, so the refactor is provably inert and a later format change
is reviewable on its own. The comparison MUST use complete runs in clean output roots, not a mutable
build directory whose origin is unknown.

#### Scenario: The refactor is verified

- **WHEN** every table is rendered after values and presentation are separated
- **THEN** each file is byte-identical to the file produced before it

#### Scenario: A target's presentation is later adjusted

- **WHEN** a renderer's presentation rules change
- **THEN** the change is visible as a diff in that target's output alone

### Requirement: A generated LaTeX table typesets correctly in the consuming document

A generated table MUST reach the consuming document through its declared publication path without a
hand edit, preserve the maintained structural features of the consumer's committed generated source,
and produce the intended typeset result on the page. Source comparison detects structural regressions;
the rendered page is authoritative for visual layout.

#### Scenario: Numbers align within a column

- **WHEN** a column holds numbers whose digit counts differ
- **THEN** they align on the page

#### Scenario: A value is qualified by a share

- **WHEN** a count is presented together with the share it represents
- **THEN** both stay in one cell so the pair reads as one value
- **AND** the pair aligns with the other cells of its column

#### Scenario: Alignment is computed rather than stated

- **WHEN** a cell needs padding to align with its column
- **THEN** the padding is derived from the column's widest value
- **AND** no report states padding for an individual cell

#### Scenario: A numeric column carries a header

- **WHEN** a numeric column carries a header
- **THEN** the header is centred over the column while the values stay right-aligned

#### Scenario: A typeset result is claimed

- **WHEN** a table's appearance is asserted to be correct
- **THEN** the claim rests on the rendered page rather than on the source text

#### Scenario: Presentation reaches another target

- **WHEN** the same table is rendered to markdown or CSV
- **THEN** no alignment artefact, spacing command, or column-pairing appears: each value is one field

### Requirement: A table may summarise groups and identify rows semantically

A table MUST be able to carry a row that spans every column and summarises the rows beneath it. A table
that makes data rows citable MUST give each such row a stable semantic key distinct from its visible
ordinal. LaTeX labels MUST derive from the table key and row key, not from the row's current position.

#### Scenario: A grouped table states each group's totals

- **WHEN** a table groups rows and declares a band summary
- **THEN** each group is preceded by a row spanning every column that carries that group's summary

#### Scenario: A table closes with an overall band

- **WHEN** a grouped table declares an overall summary
- **THEN** a final band row carries it

#### Scenario: Rows are numbered and labeled

- **WHEN** a table declares citable row numbering
- **THEN** each data row carries a visible ordinal and a label derived from its semantic key
- **AND** band rows consume neither an ordinal nor a row key

#### Scenario: Rows are reordered

- **WHEN** a later report run reorders rows without changing their semantic keys
- **THEN** visible ordinals follow the new order
- **AND** each reference still resolves to the same semantic row

#### Scenario: A row key is duplicated

- **WHEN** two data rows in one table declare the same semantic key
- **THEN** rendering fails naming the duplicate key

#### Scenario: A referenced row is removed

- **WHEN** a generated table no longer emits a row that the consuming document references
- **THEN** the document's strict build fails with an undefined reference

#### Scenario: A band row reaches another target

- **WHEN** the same table is rendered to markdown or CSV
- **THEN** the group summary appears as data rather than as a spanning typeset row
