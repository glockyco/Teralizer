## Why

The report generator cannot deliver a figure to the repositories that print its figures.

Four figures in the thesis are declared by this generator. Each `Figure` carries the LaTeX label the
thesis cites — `fig:mutation-detection-comparison`, `fig:test-runtime-differences`,
`fig:teralizer-efficiency`, `fig:teralizer-runtimes`. The thesis prints all four. No documented path
regenerates any of them.

Two independent causes. The figure renderer writes PNG at 200 dpi and nothing else, although the
paper style it applies sets `savefig.format` to `pdf`, `savefig.dpi` to 300, and `pdf.fonttype` to 42
— the settings a print consumer needs. So the style still encodes the pre-migration intent while the
renderer overrides all three. And the publish path copies LaTeX tables and CSV data into a consuming
repository, never figures, so even a correct PDF would stay in the analysis tree.

The consequence is already in the thesis: its four data figures are byte-identical copies of the
pinned paper's, imported once by hand. Their underlying data belongs to the controlled corpus, so
they are not currently wrong. They are unreproducible, which is the defect. A reader following the
documented procedure cannot obtain them, and a change to any of those three reports cannot reach the
document that prints its result.

## What Changes

- The figure renderer emits a print-quality PDF for every figure, alongside the PNG the Markdown
  reports embed. The PNG remains the online-readable form and is unchanged.
- Publishing delivers figures to a consuming repository. Which figures a consumer takes, and the file
  name each one lands under, are declared by the consumer rather than derived from the figure key, so
  the thesis and the paper can hold different names for the same figure and a figure with no consumer
  is not delivered anywhere.
- A declared figure that the generator does not emit, and an emitted figure a declaration maps to a
  path outside the consuming repository, both fail the publish rather than passing silently.
- **BREAKING** for consumers of `figures/`: the PNG output path is unchanged, but the `figures`
  render target now also writes PDFs under the build tree. Nothing reads that location yet.

## Capabilities

### New Capabilities

- `reporting/figure-publication`: how a generated figure reaches a consuming repository — the formats
  the generator emits and why each exists, the consumer's declaration of which figures it takes and
  under which names, and the failure behaviour when a declaration and the emitted set disagree.

### Modified Capabilities

None. `reporting/` has no accepted spec in this repository yet.

## Impact

- `analysis/src/teralizer/eval/render/figures.py`: emits PDF as well as PNG, with per-format
  metadata.
- `analysis/src/teralizer/eval/cli.py`: passes the publish destination to the figure renderer and
  resolves the consumer declaration.
- `scripts/publish-analysis.sh`: unchanged in its interface. Its existing guard against publishing
  over uncommitted consumer files now covers figure paths too.
- `analysis/tests/eval/`: cover the declaration parser, the mismatch failures, and that both formats
  are written.
- No report content, no metric, no query, no table, and no CSV. This change moves no measured value.

## Non-Goals

- Changing any figure's appearance, data, size, or style, **except** to restore what the thesis
  already prints. The four thesis figures must regenerate to the content they show today, and the
  check for that is a task. That check found the stage-band divider in the runtime figure drawn inside
  Stage 4 rather than between Stage 3 and Stage 4, so the published figure would have contradicted the
  committed one the first time publishing worked. Fixed here, because enabling delivery is what would
  have shipped it.
- Adding a render target. PDF is a second format within the existing `figures` target.
- Restyling figures to match the thesis body font. The figures use the paper's serif stack today and
  continue to.
- Emitting a figure the reports do not currently declare. `evosuite_runtime_phases` has no consumer
  and gains none here.
- Editing the consuming repositories. The thesis's declaration file and the removal of its
  hand-imported figures belong to the thesis repository's own change.
