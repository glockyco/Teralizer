## Why

Every generated artifact records the wrong commit, and the error is unsatisfiable rather than
accidental.

Provenance is captured from `HEAD`. The committed `analysis/reports/provenance.json` records
`57d235ce` for every metric it describes. That file lives in commit `3a7a9f63`, and the report source
it attributes was last changed in `2a4d4ba3`. So the recorded commit is neither the commit that
contains the artifact nor the commit of the code that produced it. It is whatever `HEAD` happened to
be when someone last ran the reports.

An artifact committed into the same repository as its generator can never carry the hash of the commit
that contains it, because that hash does not exist until the artifact is written. `HEAD` is a proxy
for a value that cannot be known, so the permalinks in every Markdown report point into an unrelated
commit.

The same defect blocks the publish path. `HEAD` moves on every commit, so regenerating rewrites all
fourteen committed report outputs even when no source and no data changed. Publishing therefore
dirties the generator tree, and the clean-tree guard refuses the next publish until the churn is
committed — which moves `HEAD` again. Documenting that loop as a step in the regeneration procedure
would be documenting a defect as a workflow.

## What Changes

- Provenance names the last commit that touched the source file which produced the artifact, rather
  than `HEAD`. That commit is the identity of the code that ran, so the permalink resolves to the
  lines that produced the value.
- The dirty flag becomes a property of that file rather than of the whole tree, so an unrelated edited
  file no longer marks every artifact as uncertain.
- The repository-relative source path is derived from the file the function was defined in, rather than
  rebuilt from its module name.
- A source file with no commit yet is recorded as dirty against `HEAD`, which is the one case where no
  better answer exists.
- **BREAKING** for `analysis/reports/`: every committed report output is rewritten once, with correct
  commits. After that they are stable, and regeneration without a source change produces no diff.

## Capabilities

### New Capabilities

- `reporting/artifact-provenance`: what a generated artifact must record about the code that produced
  it — which commit, when it is marked uncertain, and the requirement that regenerating unchanged
  inputs reproduces the artifact byte for byte.

### Modified Capabilities

None. `reporting/figure-publication` is added by `publish-figures-to-consumers` and is not modified
here.

## Impact

- `analysis/src/teralizer/eval/provenance.py`: per-file commit and dirty resolution.
- `analysis/src/teralizer/eval/render/figures.py`: figure metadata takes the commit of the file
  defining the figure's build function.
- `analysis/src/teralizer/eval/render/manifest.py` and `render/markdown.py`: unchanged in shape; they
  consume the corrected values.
- `analysis/reports/**`: rewritten once by the first regeneration after this change.
- `analysis/tests/eval/test_provenance.py`: covers per-file resolution, the uncommitted-file case, and
  reproducibility.
- No metric, query, table, CSV, or figure geometry. This change moves no measured value.

## Non-Goals

- Removing the clean-tree guard on publishing. It stays: a stale hash must never be attributed to
  uncommitted code.
- Making `analysis/reports/` a build artifact. The Markdown reports stay committed and readable in the
  repository.
- Changing what provenance is attached to, or adding provenance to artifacts that carry none.
- Corpus provenance, meaning which tool commit produced which database rows. That belongs to
  `consolidate-evaluation-databases` and shares only the word.
