## Context

See proposal.md - Why. Three facts shape the approach.

**The declaration already exists, for one kind.** A destination carries `publish.toml` at its root with
a `[figures]` table mapping a figure key to a repository-relative path. Delivery for figures already
resolves that declaration against everything the run emitted, fails on a declared key nothing produced,
detects duplicate keys, and refuses when a declared path has uncommitted changes. None of that has to
be invented; it has to stop being figure-shaped.

**Names repeat across kinds, and must not collide.** `tab-pareto-commons` names both a LaTeX table and
a CSV file. A single flat namespace would report a collision for every table that also has a data file,
so a name identifies one artifact *within* a kind.

**The consumer keeps its own files in the delivery directories.** `tab-pit-mutators.tex` sits in the
thesis's `tables/` directory and no report emits it. Anything that treats a delivery directory as
generator-owned would delete maintained work.

## Goals / Non-Goals

**Goals**

- One delivery mechanism for every artifact kind.
- A consumer's tracked generated files and its declaration say the same thing, checkably.

**Non-Goals**

- Choosing what the thesis retains. That belongs to the thesis. This change only requires the choice
  to be explicit and checkable.
- Pruning. Publishing writes; it never removes. See Decision 6.
- The form of a delivered artifact. `separate-report-values-from-presentation` owns that.
- Renderer return types, artifact accumulation, generator staging, manifest assembly, and promotion.
  `make-report-runs-explicit` owns those and passes the completed `ArtifactSet` to publication.

## Decisions

### 1. Declaration sections are render targets, not artifact categories

`publish.toml` gains a section per render target: `[figures]`, `[latex]`, `[csv]`. Within a section,
a key is the artifact's generated name and the value is the path in the consuming repository.

*Why targets rather than reader-facing categories such as "tables" and "data":* the run already takes
`--targets`, and the requirement that a run must fail when it cannot produce a declared kind then maps
onto one comparison instead of a translation table. It also gives the macro file an honest home: the
aggregate `macros.tex` is a LaTeX artifact with no table behind it, so `[latex]` holds it naturally
while a `[tables]` section would have had to lie about it.

*Alternative rejected:* one flat `[artifacts]` table with the kind inferred from the file extension.
Extension does not determine kind — `.tex` covers both a table and the macro file — and inference turns
a declaration mistake into a silently different outcome.

### 2. Delivery consumes the validated run artifact set once

`make-report-runs-explicit` removes copying from renderers, builds every selected report, renders below
a staging root, validates and promotes generator output, and returns one complete `ArtifactSet`.
Publication receives that already validated set and performs consumer delivery once, after generator
promotion succeeds. This change does not modify `_build_and_render`, invent another emitted map, or
orchestrate report construction and staging.

*Why:* the current per-report copy writes into the consumer while the run is still going, so a report
that fails at report five leaves four reports' tables behind. The common runner fixes that defect for
every target and gives publication one stable input. Reimplementing the boundary here would restore
two run orchestrators that can disagree.

### 3. Artifact identity and collision detection come from `ArtifactSet`

`ArtifactSet` keys each output by render target and generated key, carries its owner and staged or
promoted path, and rejects same-target collisions while allowing a LaTeX table and CSV file to share a
key. Publication compares consumer declarations with those typed identities. It does not accumulate
paths or repeat collision and containment checks that the run already performed.

### 4. One guard call over every declared path

The uncommitted-change guard takes the union of declared paths across kinds and asks the consuming
repository once. It already shells out to git a single time for figures; widening the path list keeps
that shape and keeps the refusal ahead of the first write.

### 5. The figure capability loses its delivery requirements rather than keeping a copy

Four of the five accepted figure requirements describe delivery, not figures. They are removed there
and stated once in the new capability.

*Why not leave them:* two capabilities asserting the same rule is the failure mode this change exists
to fix, one level up. A figure-shaped copy would also keep the weaker form of two rules: name
uniqueness could not be expressed per kind, so a table and its data file would read as a collision,
and the guard requirement pointed at a table's guards as though a figure's were derivative.

*Known limit of the delta form:* a requirement delta cannot restate a capability's Purpose, and the
accepted figure Purpose still describes declaration and delivery. It has to be narrowed to formats when
this change is archived and its specs are merged. That is a task, not an assumption.

### 6. Publishing never removes a file

An undeclared file in a delivery directory is left exactly as it is, whether a person wrote it or an
earlier publish deposited it.

*Why not prune undeclared files:* the thesis keeps a hand-authored table in the same directory as
generated ones. Pruning would delete it. The generator cannot distinguish a file it should never have
written from a file the consumer maintains, so it must not try. Cleaning up what earlier publishes
deposited is a one-time act in the consuming repository, and it is safe there because the person doing
it can read the declaration.

*Consequence, accepted:* files that earlier publishes deposited stay until someone removes them. This
change stops the accumulation; it does not undo it.

## Risks / Trade-offs

- **A consumer omits an entry and silently stops receiving that artifact.** The failure modes are
  asymmetric on purpose: a declared artifact that nothing emits fails loudly, because it names a
  disagreement, while an emitted artifact nobody declares is the ordinary case and cannot fail. →
  Mitigate when the declaration is written by classifying every tracked generated consumer file: each
  file is either a document input or explicitly retained evidence, and each resulting declaration key
  must be emitted by the reviewed full run. Re-run this audit whenever the consumer adds or removes a
  maintained generated artifact.
- **The thesis receives nothing until its declaration exists.** → The declaration lands in the same
  step, and the verification above is what proves it complete.
- **Two in-flight changes touch the same publish module.** `publish-figures-to-consumers` introduced it
  and is not archived. → This change follows it, and corrects the one scenario there that sanctions
  undeclared delivery before that change archives.

## Migration Plan

1. Finish and archive `publish-figures-to-consumers`, correcting its *Publishing without a declaration*
   scenario, so the accepted spec set does not hold two contradictory rules.
2. Land `make-report-runs-explicit` through the `ArtifactSet`, staged generator promotion, and
   post-promotion delivery boundary.
3. Generalise declaration parsing, declared-set validation, consumer guards, and delivery over the
   supplied `ArtifactSet`. Keep figure behavior unchanged: the existing figure tests are the
   regression check.
4. Write the thesis declaration from an inventory of document inputs and explicitly retained evidence.
   Publish the reviewed full run into a clean scratch checkout, then verify that every declaration entry
   is emitted and that the delivered set equals the maintained generated-artifact inventory.

Rollback is the revert. A declaration file left behind is inert to the previous code, which reads only
its `[figures]` section.

## Open Questions

- **Should the pinned paper submodule gain a declaration?** Carried unanswered from
  `publish-figures-to-consumers`. It is not a publish target today, so the answer changes no decision
  here.
