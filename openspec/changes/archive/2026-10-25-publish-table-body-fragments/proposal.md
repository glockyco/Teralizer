## Why

The LaTeX table renderer emits complete floats with thesis-specific captions, labels, placement, width, and local style. This prevents the paper and thesis from presenting the same generated evidence with layouts appropriate to each document.

Publication also rewrites tracked report and provenance files while delivering consumer assets. A publication-only run therefore dirties the evidence worktree even when its preserved inputs are unchanged.

## What Changes

- **BREAKING**: Change each generated LaTeX table artifact from a complete float to a body-only fragment.
- Keep generated columns, headers, cells, grouping, rules, stable row anchors, and evidence relationships unchanged.
- Remove float environments, captions, short captions, table labels, placement, sizing, and manuscript-local style from generated fragments.
- Use `\linewidth` for width-sensitive generated table constructs so one fragment can fit different consumer containers.
- Make each consumer own its table wrapper, caption, label, placement, sizing, and local style.
- Treat a different population, column set, or aggregation as a separate generated artifact rather than a consumer override.
- Add a publication-only execution boundary that validates and delivers staged consumer artifacts without rewriting tracked generator reports or provenance.
- Update focused renderer, publication, and integration checks for the fragment interface and clean-worktree invariant.
- Require coordinated paper and thesis wrapper migrations before each consumer republishes the changed table artifacts.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `report-output/table-rendering`: Define body-only LaTeX table artifacts, portable width behavior, generated row-anchor ownership, and consumer-owned float presentation.
- `reporting/report-execution`: Define publication-only delivery that leaves tracked generator reports, provenance, source, and preserved inputs unchanged.

## Impact

- Affected implementation: the render-agnostic table model, LaTeX table renderer, report table declarations, publication runner, and `scripts/publish-analysis.sh`.
- Affected checks: renderer unit tests, publication transaction tests, generated-source fixtures, complete-report checks, and clean-worktree integration checks.
- External consumers: `glockyco/Teralizer-Paper` and the Teralizer thesis chapter must add or retain manuscript-owned wrappers around delivered fragments.
- Compatibility: existing consumers that directly input complete generated floats must migrate before republishing. No compatibility alias or duplicate full-float artifact is retained.
- Unchanged evidence: table values, populations, metrics, provenance authority, figure artifacts, CSV artifacts, and preserved experiment data.
