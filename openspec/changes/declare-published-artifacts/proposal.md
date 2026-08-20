## Why

Publishing copies every table and every CSV the report set renders into the consuming repository,
whether or not that repository prints them. One measured run against the thesis deposited 24 files the
thesis does not carry: 5 LaTeX tables and 19 CSV files. The thesis prints 18 generated tables and 3
CSV files.

The consequence is not cosmetic. A publish leaves the consumer with untracked files that a person has
to recognise and delete, and the deletion has already happened once by hand: thesis commit `3b21b53`
removed three deposited tables and their CSV files after a sync. The reader of a published tree cannot
tell a file the repository prints from a file the generator happened to render.

Figures already work the other way. A consumer declares which figures it takes and the path each one
lands under, publishing delivers exactly that set, and a declared key that no report emitted fails the
run. Tables and data have no such declaration, so the same publish step is declaration-driven for one
artifact kind and unconditional for the other two. That asymmetry is the defect, and it was introduced
when figure delivery was added: the delivery rule was written for figures instead of for artifacts.

It also blocks documentation. A consuming repository cannot be told to publish and commit while a
publish deposits files it must then delete.

## What Changes

- **A consumer declares every artifact kind it takes**, not only figures. The declaration states the
  tables and the data files the repository prints, each with the path it lands under.
- **Publishing delivers the declared set and nothing else.** No generated file reaches a consuming
  repository that the consumer has not named.
- **A declared artifact that no report emitted fails the publish**, naming the artifact, exactly as a
  declared figure key already does. A renamed table therefore fails loudly instead of leaving the
  consumer's copy stale.
- **A name that two reports emit fails the publish**, exactly as a duplicate figure key already does,
  because a declaration names an artifact and cannot express which report it came from.
- **The consumer's uncommitted-change guard covers every delivered path.** Today it protects declared
  figure paths only, so an edited table is overwritten without warning.
- **Delivery happens once, after the complete generator run is promoted.** Tables and CSV files are
  currently copied per report from inside the render step, so a run that fails partway leaves a
  partial set in the consumer. `make-report-runs-explicit` owns construction, staging, artifact
  accumulation, manifest validation, and generator promotion. This change receives its validated
  `ArtifactSet` and performs declaration-driven consumer delivery once.
- **BREAKING** for a consuming repository with no declaration: it receives nothing. The thesis
  declaration must gain its tables and data in the same step, or the thesis stops receiving artifacts.

## Capabilities

### New Capabilities

- `reporting/artifact-delivery`: which generated artifacts reach a consuming repository, how that
  repository states what it takes, and what happens when the declaration and the emitted set
  disagree.

### Modified Capabilities

- `reporting/figure-publication`: loses the four requirements that were never about figures — the
  consumer's declaration, the declaration-versus-emitted-set failure, name uniqueness, and the
  publish guards. `reporting/artifact-delivery` states all four for every artifact kind. What remains
  is the one genuinely figure-shaped requirement: that a declared figure is emitted in a print format
  and a screen format.

The outcome that directly contradicted this change has already been retired at source. That spec
stated that a destination supplying no declaration still receives the tables and data it expects; it
now states only that an absent declaration is not an error. The correction landed before
`publish-figures-to-consumers` was archived, so the contradiction never entered the accepted spec set.

## Impact

- `analysis/src/teralizer/eval/publish.py`: declaration parsing, declared-set validation, consumer
  guards, and delivery generalise from figures to every artifact target and consume the common
  `ArtifactSet`. Publication does not merge renderer output itself.
- `make-report-runs-explicit` removes the unconditional copies from `cli.py`, replaces renderer return
  shapes with `ArtifactSet`, stages and promotes generator output, and invokes publication only after
  promotion. This change adds no second run orchestrator. Its only CLI-facing check validates that the
  requested targets cover every kind declared by a destination.
- Consuming repositories: the thesis declaration at `chapters/05-teralizer/publish.toml` gains its
  tables and data. This change and that declaration land together.
- `analysis/scripts/publish-analysis.sh` keeps its interface: it already builds the whole report set
  with every render target.
- Not in scope: the form of a delivered table. Four report modules emit raw database identifiers where
  the thesis prints dataset macros, and digit padding is missing.
  `separate-report-values-from-presentation` owns that, and this change neither fixes nor worsens it.
