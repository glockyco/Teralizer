# reporting/figure-publication Specification

## Purpose
Governs the print and screen formats emitted for each report figure and the metadata those formats
carry.

## Requirements

### Requirement: Every declared figure is emitted in a print format and a screen format

For each figure a report declares, a report run with the figure target SHALL write a vector PDF and a
raster PNG. Neither format SHALL be conditional on a publish destination being supplied.

The PDF SHALL be written with the print settings the paper style declares, and SHALL NOT override
them with values that degrade print quality.

Both formats SHALL carry metadata naming the report and the commit that produced them, expressed in
the keys the target format defines.

#### Scenario: A report run emits both formats

- **WHEN** a report with figures runs with the figure target
- **THEN** a PDF and a PNG exist for every figure the report declares
- **AND** each carries the report id and the producing commit

#### Scenario: A format rejects a metadata key

- **WHEN** a metadata key is not defined for the output format
- **THEN** the renderer uses the key that format defines for the same purpose
- **AND** the run does not warn or fail on an unknown key
