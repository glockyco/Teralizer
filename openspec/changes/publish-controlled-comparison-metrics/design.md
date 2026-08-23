## Context

See `proposal.md` - Why.

The controlled RQ5 report fetches one breakdown frame and renders it as
`tab-exclusions-breakdown`. The retained comparison uses the `Improved (200 tries)` / `Generalization`
row, but the report currently returns no metrics. Aggregate LaTeX macros and provenance metric entries
already derive automatically from `RQReport.metrics`.

The metric model already validates count/share kind, population compatibility, numerator and denominator
references, arithmetic consistency, and provenance. The controlled report input role and semantic corpus
id are both `controlled`.

## Goals / Non-Goals

**Goals:**

- Give the retained controlled result three stable metric identities: total generalizations, included
  generalizations, and included share.
- Fetch and calculate the table and metrics once from the same controlled breakdown frame.
- Reuse existing metric validation, provenance, manifest, and aggregate-macro paths.
- Keep the existing table output and controlled query semantics unchanged.

**Non-Goals:**

- Adding metrics for every strategy, level, filtering count, or failure count in RQ5.
- Changing the controlled database, query, variant registry, table ordering, or rendered table schema.
- Adding a comparison report or calculating the real-world side in RQ5.
- Hand-editing generated reports, provenance, or thesis artifacts.

## Decisions

### 1. Select the retained row from the existing fetched frame

`build` stores `_fetch_breakdown(conn)` once, passes that frame to the existing table builder, and selects
exactly one row by semantic strategy reference plus `Generalization` level. Selection must fail unless
there is exactly one match. This avoids a second database query and makes table/metric drift impossible
within one report build.

**Alternative:** Query the three values separately. Rejected because it duplicates evidence acquisition
and can diverge from the rendered table.

### 2. Use controlled, variant-specific metric keys

The report emits:

- `controlled.improved_200.generalizations_total`;
- `controlled.improved_200.generalizations_included`; and
- `controlled.improved_200.generalizations_included_pct`.

The names identify corpus role, variant, entity population, and measure without depending on the RQ
number or presentation label. The count populations use entity level `Generalization` and input role
`controlled`. The share uses the included metric as numerator and the total metric as denominator.

**Alternative:** Key metrics by table row or displayed `Improved (200 tries)` text. Rejected because
presentation changes would become consumer API changes.

### 3. Reuse the report metric and provenance infrastructure

Both counts carry `ValueKind.COUNT`; the rate carries `ValueKind.SHARE` and `pct1` formatting. All three
use the captured provenance of `_fetch_breakdown` and `BREAKDOWN_SQL`. The report calls
`validate_metric_relations(require_metadata=True)` before return. Existing manifest and macro renderers
then publish the metric records and LaTeX commands without new output code.

### 4. Verify identities and table agreement as observable contracts

The focused RQ5 test asserts exact keys, kinds, populations, operand references, and values. It also
asserts that the three metric values equal the selected table row and that metric relation validation
passes. Report rendering tests or a focused report build verify aggregate macros and provenance contain
all three keys.

## Risks / Trade-offs

- **The strategy display label changes.** -> Select with the variant registry's semantic reference, not
  a duplicated display literal.
- **The query emits zero or duplicate retained rows.** -> Fail report construction with the expected
  single-row identity rather than publishing a plausible value.
- **The percentage is rounded before validation.** -> Store the exact included/total share and apply
  `pct1` only at rendering.
- **Adding three metrics changes generated macro and provenance artifacts.** -> This is the intended API
  extension; regenerate through the complete report command and update focused expected outputs.
- **The thesis requests more controlled quantities later.** -> Add only retained quantities through a
  separate evidence review; do not expose the full table as an accidental metric API now.
