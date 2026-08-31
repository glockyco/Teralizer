## Context

See `proposal.md` for motivation and the delta spec for publication requirements. The rejected paper is a standalone manuscript with explanations that the thesis intentionally omits. The thesis chapter contains 161 later commits and 54 changed paths, but many changes are thesis framing, layout work, or generated presentation changes.

`glockyco/Teralizer` and `glockyco/Teralizer-Paper` are separate repositories with different responsibilities. Executable declarations, tests, registered reports, provenance, and generated evidence belong to `Teralizer`. The editable manuscript and its publication assets belong to `Teralizer-Paper`. The thesis chapter is the preferred scientific narrative for changes after the paper import. The rejected paper remains authoritative for standalone framing and material that other thesis chapters replace.

The paper submodule under `../phd-thesis/projects/` is read-only. Revision work must use a standalone clone. Corpus and project reruns require explicit operator approval and are outside this change.

## Goals / Non-Goals

**Goals:**

- Produce one coherent standalone paper from the rejected manuscript and selected thesis revisions.
- Preserve traceability from each empirical claim to authoritative evidence.
- Separate approach scope, prototype support, evaluation population, and measured exclusions.
- Resolve reviewer concerns through manuscript changes or explicit, evidence-based rebuttals.
- Keep the manuscript buildable after each coherent revision unit.

**Non-Goals:**

- Synchronize every textual difference between the thesis and paper.
- Make the paper text identical to the thesis chapter.
- Change Teralizer, rerun experiments, or regenerate measurements.
- Add held-out-mutant, real-bug, LLM-integration, or new JARVIS experiments.
- Shorten or combine research questions only to reduce page count.

## Decisions

### 1. Revise an independent paper clone

Use `gh repo clone glockyco/Teralizer-Paper` under `/Users/glockyco/src/github.com/glockyco/`. Verify that `origin` resolves to `glockyco/Teralizer-Paper`, not `glockyco/Teralizer`. Create a dedicated branch before manuscript edits.

This preserves the thesis submodule and gives the paper an independent, reviewable history. Editing the submodule was rejected because repository rules make `projects/` read-only.

### 2. Use an authority hierarchy instead of treating either manuscript as ground truth

Resolve claims in this order:

1. executable declarations and focused behavior checks;
2. registered reports, provenance, and generated artifacts;
3. the revised thesis narrative and its relevant Git history;
4. the rejected manuscript.

Use the thesis for scientific changes, not for empirical authority. This prevents stale transcriptions and protects standalone explanations that are absent from the thesis.

A direct recursive merge was rejected. The two manuscripts have different macros, section boundaries, cross-references, and narrative responsibilities.

### 3. Maintain a section-level backport manifest

Classify each changed paragraph, table, figure, listing, macro, or citation as one of these actions:

- `PORT`: new or corrected scientific content;
- `ADAPT`: scientific content that needs standalone-paper wording;
- `KEEP-PAPER`: material still needed outside the thesis context;
- `DROP`: thesis framing, bridge text, cosmetic reflow, or unchanged meaning;
- `VERIFY`: an apparent factual change that needs an authoritative source.

Record the source path, destination path, action, evidence owner, and reviewer concern. This manifest controls completeness and prevents cosmetic churn.

A raw line-by-line diff was rejected because it cannot distinguish scientific changes from thesis layout edits.

### 4. Backport in scientific dependency order

Use this order:

```text
source and evidence baseline
        |
        v
concepts and algorithm
        |
        v
current implementation account
        |
        v
evaluation framework
        |
        +----------+
        |          |
        v          v
      RQ0        RQ6 v7
        |          |
        +-----+----+
              v
 discussion, validity, related work
              |
              v
 introduction, contributions, abstract, conclusion
              |
              v
 TOSEM response letter
```

This order keeps high-level claims dependent on stable methods and results. Updating the abstract first was rejected because it creates repeated rewrites and stale intermediate claims.

### 5. Treat RQ0 as three bounded comparisons

Separate these analyses:

1. project-level applicability across all twelve JARVIS projects;
2. PVC for the ten scenarios that JARVIS reported successfully;
3. the relation between PVC and mutation detection in Teralizer.

Report JARVIS values as published and Teralizer values as measured. State that JARVIS was not executed because its implementation is unavailable. Preserve reconstruction and comparability threats.

Use “substantially broader measured applicability on the shared benchmark.” Do not use statistical significance or a universal state-of-the-art claim.

### 6. Replace RQ6 as one atomic evidence package

Replace its population, selection method, lifecycle model, tables, generated macros, result narrative, and causal interpretation together. Audit every old `632`, `11`, `1.7%`, and related denominator outside the RQ6 section.

The revised account includes eligible cases that later fail or are excluded. It must not describe the evaluation as restricted to successful pure methods or supported data types. Prototype limits explain outcomes; they do not define away measured exclusions.

Do not claim a paired improvement from 11 of 632 to 85 of 584. Report both measurements with their own denominators and changed pipeline semantics.

### 7. Separate mutation-independent generation from mutation-based reduction

Explain the causal boundary explicitly:

```text
implementation + developer tests
              |
              v
       Stages 1-4 create G
              |
              v
PIT results remove tests redundant to T
              |
              v
       Stage 5 retains G'
```

For mutant set `M`, original suite `T`, and generated suite `G`, every discarded test kills only mutants already killed by `T`. Therefore, reduction cannot increase `K_M(T ∪ G')` over `K_M(T ∪ G)`.

If preserved evidence exposes pre-reduction and post-reduction scores, report them. Otherwise, explain the invariant without creating new measurements. Retain the standard threat that mutation score is a proxy for real faults.

A held-out-mutant experiment was rejected for this revision. It would evaluate transfer of the reduction criterion, not overfitting of generalized-test creation.

### 8. Use a two-part oracle claim

Use this positive formulation throughout the paper:

- developer-written assertions identify the observable behavior and validate the seed execution;
- symbolic analysis derives a path-conditioned relation from the implementation.

Call the result developer-anchored and implementation-derived. Limit path-exactness to the analyzed implementation, symbolic model, purity assumptions, and widening evidence. Do not say that one seed assertion proves intent for every generalized input.

This wording preserves the stronger intent evidence relative to implementation-only oracle generation without claiming an external formal specification.

### 9. Scope claims by layer

Use separate language for:

- the general semantics-based approach;
- the Java prototype and its supported operations;
- the evaluation population, including successful and excluded cases;
- conclusions supported by each experiment.

Keep implementation versions and detailed restrictions in the approach, setup, and validity sections. Use short qualifiers in the abstract and conclusion. Never summarize the evaluation as covering only the prototype's successful fragment.

### 10. Preserve the paper's standalone architecture

Keep the paper's background, motivation, contribution list, full related work, and conclusion structure unless scientific changes require revision. Adapt thesis cross-references to paper sections. Do not port the thesis bridge or references to other thesis chapters.

Import the new algorithm listing and required concepts. Adapt macros and captions to paper conventions rather than importing the thesis macro environment wholesale.

### 11. Add a bounded LLM synthesis

Extend the related-work discussion with one focused comparison. Contrast LLM semantic breadth with Teralizer's path evidence, refusal behavior, and current model restrictions. Describe LLM-proposed assertions or abstractions as possible future inputs that still require validation.

Do not present an LLM hybrid as implemented or evaluated. A broader AI survey was rejected because it does not advance the paper's contribution.

### 12. Build the response letter from a live reviewer matrix

Maintain one row for every editorial and reviewer concern. Each row records the response type, manuscript locations, evidence, and remaining limitation.

Use these response types:

- accepted and revised;
- addressed by new evidence;
- partially addressed with a stated boundary;
- rebutted with mechanism and evidence;
- not implemented, with rationale.

Draft the final letter after manuscript claims stabilize. This prevents the letter from promising changes that the paper does not contain.

### 13. Commit coherent revision units

Use separate commits for the source baseline, approach, RQ0, RQ6, cross-cutting claims, related work, and response letter. Each commit must include the affected generated assets and focused verification.

This structure permits rollback and reviewer-oriented inspection. A single manuscript-wide commit was rejected because it would hide causality and make stale-value failures harder to locate.

## Risks / Trade-offs

- **[Risk] Thesis wording omits context required by a standalone paper.** → Keep the paper passage until the adapted replacement is independently understandable.
- **[Risk] An empirical value is copied from prose instead of its owner.** → Require a report, provenance, or generated-artifact source before changing it.
- **[Risk] RQ6 values remain stale outside the RQ6 section.** → Run a paper-wide stale-value and denominator audit after the atomic replacement.
- **[Risk] JARVIS results appear more comparable than they are.** → Separate published and measured values and retain reconstruction threats beside the result.
- **[Risk] Scope qualifiers falsely narrow the evaluated population.** → State that exclusions and unsupported cases are measured outcomes.
- **[Risk] Exactness qualifiers weaken the developer-oracle contribution.** → Lead with developer anchoring, then state the implementation-relative boundary.
- **[Risk] The response letter concedes an incorrect mutation-overfitting claim.** → Explain the stage boundary and reduction invariant before discussing construct validity.
- **[Risk] New content increases length.** → Remove actual redundancy only after scientific content stabilizes; do not combine distinct RQs by default.
- **[Risk] Paper macros cannot consume thesis-generated assets directly.** → Adapt each asset at its publication boundary and verify the rendered result.
- **[Risk] An existing report cannot support a desired comparison.** → Record the gap and omit the claim; do not rerun an experiment.

## Migration Plan

1. Clone the standalone paper repository and establish a clean baseline build.
2. Create the backport manifest and reviewer matrix without changing scientific content.
3. Apply the revision units in dependency order and commit each verified unit.
4. Run the repository's focused build, bibliography, reference, and style checks after each unit.
5. Run the full manuscript build and inspect the rendered PDF after all units.
6. Finalize the response letter against the completed manuscript.

Rollback uses the atomic commit history. Revert the smallest revision unit whose evidence or rendering is invalid; do not reset unrelated manuscript work.
