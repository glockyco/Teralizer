## Context

See proposal.md — Why.

Three facts constrain the approach.

**The intent is still in the code.** `plotting.py:setup_paper_style` sets `savefig.format` to `pdf`,
`savefig.dpi` to 300, `pdf.fonttype` to 42, and `ps.fonttype` to 42. Every one of those is a print
setting, and every one is overridden by `render/figures.py`, which hardcodes a `.png` suffix and
`dpi=200`. Its module docstring states the narrowed intent outright: "Materialize each Figure once to a
committed PNG." So this is a regression with a surviving witness, not a feature that was never built.

**The consumers disagree about names, and one figure has no consumer.** The generator emits five
figure keys. The thesis prints four of them, under names that are not a mechanical transform of the
key: `teralizer_efficiency` becomes `teralizer-efficiency`, but `mutation_detection_comparison`
becomes `teralizer-mutation-detection-comparison`, gaining a prefix the key does not carry. The paper
holds the same figures as `fig_<key>.pdf`. `evosuite_runtime_phases` is printed by neither. Any rule
that derives the consumer's name from the key gets at least one of these wrong, and delivers one
figure nobody wants.

**Figures do not live where tables live.** `--paper-out` names the directory holding `tables/` and
`data/`, which for the thesis is a chapter directory. The thesis keeps figures in a flat `figures/`
directory at its repository root, two levels up. So a figure destination cannot be derived from the
publish destination by appending a segment, the way `tables/` and `data/` are.

## Goals / Non-Goals

**Goals:**

- A report run produces a figure a print consumer can use, without a manual step.
- Publishing delivers each consumer exactly the figures it asked for, under the names it chose.
- A disagreement between what a consumer declares and what the generator emits stops the publish.
- The four figures the thesis prints regenerate to the content they show today.

**Non-Goals:**

- Changing figure appearance, data, or style. This change is about format and delivery.
- Deriving a consumer's names or figure set from anything in this repository.
- Editing a consuming repository, including removing its hand-imported figures.
- Making the PNG output a publishable artifact. It stays the Markdown form.

## Decisions

### 1. Emit both formats from one draw, PNG where it is now and PDF into the build tree

`materialize` draws each figure once and saves it twice. The PNG path is unchanged, so the Markdown
reports keep working with no edit. The PDF goes to `analysis/build/figures/<rq>/<key>.pdf`.

*Why the build tree for the PDF:* `build/` already holds what publishing delivers — the LaTeX tables
at its root and the CSVs under `build/<rq>/`. `reports/` holds what a human reads in the repository.
A print artifact belongs with the other publishable output, and separating the two formats by
directory means the publish step never has to filter a directory by suffix.

*Why not one directory with two suffixes:* the Markdown renderer builds relative image links into
`reports/figures/<rq>/`. Moving the PNG to satisfy tidiness would edit the Markdown output, which is a
consumer of its own.

The PDF drops the explicit `dpi` argument so the style's 300 applies, and keeps `bbox_inches="tight"`,
which governs the crop rather than the raster resolution.

### 2. Metadata is written in each format's own vocabulary

The PNG carries the provenance string under `Comment`, a free-form key PNG text chunks allow, exactly
as today. PDF has a fixed information dictionary, and matplotlib warns on a key outside it, so the PDF
carries the same string under `Subject`. `Creator` is left to matplotlib, which uses it to record its
own version — information worth keeping, since a figure's appearance depends on it.

*Alternative considered:* one metadata mapping for both formats, chosen by intersecting what each
supports. The intersection is empty for a free-form comment, so the mapping would have to be
per-format anyway.

### 3. The consumer declares its figures in a file at the publish destination

The publish destination holds `publish.toml`:

```toml
[figures]
mutation_detection_comparison = "figures/teralizer-mutation-detection-comparison.pdf"
test_runtime_differences      = "figures/teralizer-test-runtime-differences.pdf"
teralizer_efficiency          = "figures/teralizer-efficiency.pdf"
teralizer_runtimes            = "figures/teralizer-runtimes.pdf"
```

Keys are figure keys. Values are paths relative to the consuming repository's root, which is resolved
with `git -C <paper-out> rev-parse --show-toplevel`. A table, rather than a list, because the name is
the consumer's choice and a list would force a naming rule back into the generator.

*Why at the publish destination rather than in this repository:* a registry here would make the
generator the authority on what the thesis prints, and every thesis-side rename would become a commit
in this repository. The destination already carries the consumer's other expectations by being the
directory the consumer chose.

*Why paths relative to the repository root rather than to the file:* the thesis keeps figures two
levels above the publish destination, so file-relative paths would all begin `../../`, and a chapter
directory move would silently retarget them outside the repository. Root-relative paths state the
consumer's own layout, and the root resolution gives the containment check a boundary to test against.

*Why git for the root:* publishing already requires git in both repositories — for the generator's
provenance and for the consumer's uncommitted-change guard. Adding no new dependency is worth more
here than avoiding a subprocess.

*Why TOML:* `tomllib` is in the standard library, the repository already reads a TOML registry for
corpora, and the format needs no schema for a flat string table.

### 4. Validate the whole declaration before writing any figure

Publishing resolves the declaration, then checks that every declared key is emitted by some report in
the run and that every declared path stays inside the consumer's root. Both checks run to completion
and report every failure, and no figure is copied unless all pass.

*Why fail rather than warn on an undeclared-but-emitted figure:* it is not a failure. A figure with no
consumer is normal, and `evosuite_runtime_phases` is the standing example. Only the other direction is
an error, because a declared key that nothing emits means the consumer is printing a figure the
generator stopped producing — which is exactly the state this change exists to end.

*Why all failures rather than the first:* a figure-key rename breaks every consumer that names it, and
reporting one at a time turns one edit into several publish attempts.

### 5. Each layer guards what it copies

The generator's clean-tree requirement already covers this path, because it is checked once per run
when a publish destination is supplied. Nothing is added for it.

The consumer-side guard is split rather than extended. `publish-analysis.sh` keeps its check on
`tables` and `data`, the artifacts the shell layer knows the location of. The figure check moves into
the delivery code, which runs it against the paths the declaration resolved, immediately before
copying them.

*Why not extend the shell guard, as first planned:* it would have to parse TOML to learn the figure
paths, or be handed them, and either way the paths it guards are maintained separately from the paths
that get written. A guard that can drift from what it protects is worth less than no guard, because it
reports success in the case it exists to catch. Running the check where the paths are known makes
drift impossible.

*Consequence:* two guards, each complete for the artifacts its own layer copies. Consolidating them
means moving table and CSV delivery into the same code, which is a larger change than this one and
touches functions another change is restructuring.

### 6. A duplicate figure key fails the run, not just the publish

A key emitted by two reports is checked while accumulating, so it fails whether or not the run
publishes.

*Why not only when publishing:* the ambiguity is a defect in the report set itself. Surfacing it only
on a publish means it appears first to whoever is publishing, in a step that has already spent the
whole report run, rather than to whoever introduced it.

## Risks / Trade-offs

- **A regenerated PDF differs from the committed one because matplotlib changed.** → The four thesis
  figures draw from the controlled corpus, which is frozen, so the data is identical and any
  difference is rendering. The check is a visual comparison against the committed figure, recorded as
  a task. A pure rendering difference is acceptable and is the point at which the thesis stops holding
  an unreproducible asset; a data difference is a stop.

- **`git rev-parse` fails when the consumer is not a git repository.** → Then the containment check
  has no boundary and the publish fails with that reason. Publishing into a non-repository is already
  outside what the consumer-side guard supports.

- **The declaration is a second place a figure's name is written.** → It replaces a hand copy, which
  was a place a name was written with no record at all. The mismatch check makes the duplication
  self-correcting: a rename that misses the declaration fails the next publish.

- **`publish.toml` sits in a chapter directory of the consuming repository.** → Accepted. It is the
  directory the consumer named as its publish destination, and the alternative puts the consumer's
  layout in this repository.

## Open Questions

- **Should the paper repository gain a declaration too?** It holds the same four figures under
  `fig_<key>.pdf`. It is a pinned submodule of the thesis and not currently a publish target, so
  nothing breaks either way. Answering it changes no decision above.
