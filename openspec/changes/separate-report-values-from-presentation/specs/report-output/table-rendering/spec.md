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

Moving presentation out of the model MUST leave every target's output byte-identical, so the refactor
is provably inert and a later format change is reviewable on its own.

#### Scenario: The refactor is verified

- **WHEN** every table is rendered after values and presentation are separated
- **THEN** each file is byte-identical to the file produced before it

#### Scenario: A target's presentation is later adjusted

- **WHEN** a renderer's presentation rules change
- **THEN** the change is visible as a diff in that target's output alone

### Requirement: A generated LaTeX table typesets correctly in the consuming document

A generated table MUST drop into the consuming document without a hand edit, and MUST produce the
intended typeset result on the page. The contract is the rendered outcome, not the source text: a
document's committed table MUST NOT be treated as the specification, because it may itself contain
workarounds.

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

### Requirement: A table may summarise a group in a band row

A table MUST be able to carry a row that spans every column and summarises the rows beneath it, and to
number its rows, so a grouped table states each group's totals and makes a row citable.

#### Scenario: A grouped table states each group's totals

- **WHEN** a table groups rows and declares a band summary
- **THEN** each group is preceded by a row spanning every column that carries that group's summary

#### Scenario: A table closes with an overall band

- **WHEN** a grouped table declares an overall summary
- **THEN** a final band row carries it

#### Scenario: Rows are numbered

- **WHEN** a table declares row numbering
- **THEN** each data row carries its number and band rows do not consume one

#### Scenario: A band row reaches another target

- **WHEN** the same table is rendered to markdown or CSV
- **THEN** the group summary appears as data rather than as a spanning typeset row
