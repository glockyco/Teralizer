## Context

See `proposal.md` for motivation. The current `Table` model combines evidence structure with one consumer's LaTeX float presentation. It carries `label`, `short_caption`, `body_style`, `float_spec`, `floating`, and `latex_resize_to_width`. The LaTeX renderer uses those fields to emit a complete float. It also renders `full_width` tables and band rows against `\textwidth`.

The target-neutral `caption` and `note` fields have separate report responsibilities. Markdown displays them, and the manifest records the caption with provenance. They must not force that prose into a paper or thesis float.

`run.execute` currently validates staged artifacts, promotes them into generator-owned final paths, and then delivers consumer files from the promoted set. A full publication therefore rewrites tracked report and aggregate-provenance snapshots before it copies declared assets. The publication script checks the consumer for uncommitted outputs but does not require the generator worktree to remain clean.

The paper and thesis are separate consumers. Their table environments, captions, labels, placement, and sizing can differ even when their cells and row identities come from one report artifact.

## Goals / Non-Goals

**Goals:**

- Publish one byte-identical LaTeX table-body fragment to every consumer that declares the artifact.
- Keep all evidence-bearing table structure generated and unedited.
- Let each consumer own its float wrapper and document-specific prose.
- Make width-sensitive fragment content relative to its immediate container.
- Deliver consumer assets from a validated staged run without changing the generator worktree.
- Preserve existing report construction, provenance validation, artifact identity, and consumer declaration checks.

**Non-Goals:**

- Change a table value, population, aggregation, column order, grouping, row identity, or precision.
- Change CSV, markdown, figure, metric-macro, or evidence-authority contracts.
- Edit the paper or thesis in this change.
- Keep a deprecated complete-float artifact, compatibility alias, or second table renderer.
- Start or rerun a corpus, project, sentinel, hotspot, JARVIS, or benchmark experiment.
- Make publication-specific captions authoritative evidence.

## Decisions

### 1. Render the complete tabular body and no float shell

The LaTeX artifact starts with fragment-local counter setup when the table has citable rows. It then emits the tabular environment, headers, groups, bands, rows, rules, and semantic row anchors. It ends after the tabular body.

The renderer does not emit `table`, `table*`, `sidewaystable`, `\caption`, `\captionof`, a float `\label`, placement options, local font or spacing commands, or an outer resize command. The same rule applies to tables that previously used `floating=False`; those tables become ordinary fragments instead of a special renderer mode.

Keep `full_width` as a body-layout distinction because it selects `tabular*` and distributed inter-column space. Render its width as `\linewidth`. Render table-spanning band rows against `\linewidth` for the same reason. A consumer that needs scaling or rotation applies it around `\input{...}` in its wrapper.

*Alternative rejected: generate a command that accepts caption, label, and placement arguments.* This adds a fragile LaTeX API and still constrains specialized wrappers.

*Alternative rejected: store consumer overrides in `publish.toml` and continue generating complete floats.* This moves manuscript prose into configuration and keeps consumer composition inside the generator.

### 2. Separate report description from LaTeX float metadata

Retain target-neutral table description and note data for markdown and manifest provenance. The LaTeX fragment renderer does not emit them.

Remove the model fields used only by a generated float shell: the LaTeX table label, short caption, body style, float placement, floating mode, and resize-to-page-width flag. Migrate every report declaration in one clean cutover. Keep body-structure fields such as `full_width`, grouped headers, composite-cell alignment, bands, and semantic row keys.

A distinct population, column set, aggregation, or semantic row structure remains a distinct report artifact. Consumers cannot create such differences in wrappers.

### 3. Keep row identity generated and float identity local

The fragment retains row labels derived from the artifact key and semantic row key. Visible ordinals remain presentation of row order and do not become identity.

Each manuscript supplies its own float label. This separates document-level table references from generated row references. The generated row-label interface remains stable when consumers choose different float labels or environments.

### 4. Add a publication-only promotion policy

Use one build, render, and validation path for ordinary regeneration and publication. The presence of a validated consumer declaration selects publication-only promotion policy.

In publication-only mode:

1. Build every selected report from registered preserved inputs.
2. Render every requested target beneath the run staging root.
3. Validate artifacts, provenance, identities, paths, aggregate outputs, and the consumer declaration against the staged set.
4. Deliver declared consumer artifacts directly from that staged set while the staging directory exists.
5. Discard staging without promoting generator-owned reports, aggregate provenance, or build outputs.

Ordinary report regeneration keeps the existing generator promotion path. This is one runner with two explicit promotion policies, not two report engines.

A publication-only result reports delivered paths and no promoted generator paths. Consumer delivery still occurs only after the complete selected report set passes validation.

*Alternative rejected: publish from committed generated reports.* A tracked snapshot can be stale and cannot prove that its declared consumer set matches the current report definitions.

*Alternative rejected: run publication in a temporary Git worktree and discard its changes.* This hides unnecessary writes instead of giving publication a correct read-only generator boundary.

### 5. Enforce clean generator and consumer preconditions

The publication entry point checks the generator repository before report construction. Tracked or untracked changes outside ignored build output block publication and name the paths. The existing consumer-path guard continues to block overwriting uncommitted declared outputs.

After successful publication, compare the generator worktree state with its pre-run state. A change to tracked source, reports, provenance, preserved inputs, or generated datasets fails the integration check.

The command can use ignored temporary staging. It cannot select such output as a replacement for registered preserved evidence.

### 6. Verify structure, behavior, and consumer responsibility separately

Focused renderer checks assert that every LaTeX table artifact:

- contains the expected tabular body and row anchors;
- contains no float environment, caption, float label, placement, local style, rotation, or outer resize command;
- uses `\linewidth` for full-width and spanning constructs;
- leaves CSV and markdown renderings unchanged.

Publication checks exercise success, dirty-generator refusal, validation failure, and staged delivery without generator promotion. They compare generator status and tracked bytes before and after the command.

A minimal consumer fixture wraps representative natural-width, full-width, citable-row, banded, and formerly resized fragments. This verifies that the fragment interface compiles without embedding one manuscript's layout. Separate paper and thesis changes remain responsible for their complete document builds and visual inspection.

## Risks / Trade-offs

- **[Risk] Consumer captions drift from report evidence.** -> Keep captions outside generated artifacts, but verify every factual claim against the registered report and provenance.
- **[Risk] A natural-width fragment overflows after generated resizing is removed.** -> Make sizing a wrapper responsibility and include formerly resized tables in the consumer-fixture checks.
- **[Risk] `\linewidth` changes spacing in existing full-page tables.** -> Compare representative rendered fragments and let each consumer inspect its final document.
- **[Risk] Publication-only execution diverges from normal regeneration.** -> Share report construction, rendering, and validation. Branch only at the promotion policy.
- **[Risk] Delivery from staging loses an artifact after the temporary directory closes.** -> Complete delivery inside the staging context and return only durable consumer paths.
- **[Risk] Existing consumers break on their next publication run.** -> Mark the interface as breaking, retain no shim, and coordinate separate paper and thesis wrapper migrations.
- **[Risk] Removing presentation fields touches every report declaration.** -> Migrate all call sites atomically and search for every removed field before verification.

## Migration Plan

1. Capture source and rendered baselines for representative table shapes and the current publication worktree mutation.
2. Change the table model and LaTeX renderer to emit body-only fragments with `\linewidth`.
3. Migrate every report table declaration and remove all obsolete float-presentation fields.
4. Update focused renderer fixtures and verify non-LaTeX targets and empirical values remain unchanged.
5. Add publication-only delivery from the validated staged set and enforce a clean generator precondition.
6. Verify successful and failed publication leave tracked generator state unchanged.
7. Publish representative fragments into a clean fixture consumer and compile its wrappers.
8. Record the breaking artifact contract for the separate paper and thesis migrations.
9. Commit the verified renderer cutover and publication-only execution as separate atomic units.

### Consumer migration prerequisites

The paper and thesis require separate clean-cut migrations before either runs publication:

1. Move every `[latex]` destination in `publish.toml` to a generated-fragment-only path, such as `tables/generated/<key>.tex`. Publication must not overwrite the manuscript-owned wrapper at `tables/<key>.tex`.
2. Keep each existing `\input{tables/<key>}` call stable by converting `tables/<key>.tex` into a wrapper. The wrapper owns the float environment, caption, label, placement, font size, column spacing, rotation, and any outer resize, then inputs `tables/generated/<key>.tex`.
3. Preserve the consumer definitions required inside fragments: tool and dataset macros, `booktabs`, `hyperref`, and the `reporttablerow` counter used by citable row anchors.
4. Add paper wrappers and declaration entries for the new RQ0 artifacts. Migrate the thesis's existing declaration entries without changing artifact keys. Keep structurally distinct tables as distinct generated fragments.
5. Commit the wrapper and declaration migration before the first publication-only run. Then publish the generated fragments, inspect all affected pages, resolve cross-references, and run each consumer's complete document build.

Rollback reverts the renderer/model unit or the publication-only execution unit independently. Consumer repositories must not republish changed fragments until their manuscript-owned wrappers are ready.
