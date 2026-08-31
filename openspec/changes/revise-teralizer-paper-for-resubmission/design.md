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

This preserves the thesis submodule and gives the paper an independent, reviewable history. Editing the submodule was rejected because repository rules make `projects/` read-only. This repository boundary is an editing safeguard, not manuscript framing. The paper identifies the public replication package through its Zenodo record.

### 2. Use an authority hierarchy instead of treating either manuscript as ground truth

Resolve claims in this order:

1. executable declarations and focused behavior checks.
2. registered reports, provenance, and generated artifacts.
3. the revised thesis narrative and its relevant Git history.
4. the rejected manuscript.

Use the thesis for scientific changes, not for empirical authority. This prevents stale transcriptions and protects standalone explanations that are absent from the thesis.

A direct recursive merge was rejected. The two manuscripts have different macros, section boundaries, cross-references, and narrative responsibilities.

### 3. Maintain a section-level backport manifest

Classify each changed paragraph, table, figure, listing, macro, or citation as one of these actions:

- `PORT`: new or corrected scientific content.
- `ADAPT`: scientific content that needs standalone-paper wording.
- `KEEP-PAPER`: material still needed outside the thesis context.
- `DROP`: thesis framing, bridge text, cosmetic reflow, or unchanged meaning.
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

1. project-level applicability across all twelve JARVIS projects.
2. PVC for the ten scenarios that JARVIS reported successfully.
3. the relation between PVC and mutation detection in Teralizer.

Report JARVIS values as published and Teralizer values as measured. State that JARVIS was not executed because its implementation is unavailable. Preserve reconstruction and comparability threats.

Use “substantially broader measured applicability on the shared benchmark.” Do not use statistical significance or a universal state-of-the-art claim.

### 6. Replace RQ6 as one atomic evidence package

Replace its population, selection method, lifecycle model, tables, generated macros, result narrative, and causal interpretation together. Audit every old `632`, `11`, `1.7%`, and related denominator outside the RQ6 section.

The revised account includes eligible cases that later fail or are excluded. It must not describe the evaluation as restricted to successful pure methods or supported data types. Prototype limits explain outcomes. They do not define away measured exclusions.

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

- developer-written assertions identify the observable behavior and validate the seed execution.
- symbolic analysis derives a path-conditioned relation from the implementation.

Call the result developer-anchored and implementation-derived. Limit path-exactness to the analyzed implementation, symbolic model, purity assumptions, and widening evidence. Do not say that one seed assertion proves intent for every generalized input.

This wording preserves the stronger intent evidence relative to implementation-only oracle generation without claiming an external formal specification.

### 9. Scope claims by layer

Use separate language for:

- the general semantics-based approach.
- the Java prototype and its supported operations.
- the evaluation population, including successful and excluded cases.
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

- accepted and revised.
- addressed by new evidence.
- partially addressed with a stated boundary.
- rebutted with mechanism and evidence.
- not implemented, with rationale.

Draft the final letter after manuscript claims stabilize. This prevents the letter from promising changes that the paper does not contain.

### 13. Commit coherent revision units

Use separate commits for the source baseline, approach, RQ0, RQ6, cross-cutting claims, related work, and response letter. Each commit must include the affected generated assets and focused verification.

This structure permits rollback and reviewer-oriented inspection. A single manuscript-wide commit was rejected because it would hide causality and make stale-value failures harder to locate.

## Reference Formulations

These formulations are starting points for the manuscript and response letter. They are not mandatory verbatim text. Adapt tense, notation, and local context without weakening the stated distinction. Record each material adaptation in the backport manifest.

### Mutation-independent generation and mutation-based reduction

Candidate manuscript formulation:

> Mutation analysis does not guide specification extraction or generalized-test creation. Teralizer first produces generalized tests from the implementation and developer-written tests. Stage 5 subsequently removes generalized tests that do not kill any mutant beyond those already killed by the original suite. Therefore, reduction cannot increase the mutation score relative to the unreduced generalized suite. It only removes tests that are redundant under this effectiveness criterion.

Candidate response-letter formulation:

> We agree that mutation score remains a proxy for real-fault detection. However, mutants do not influence the generalized tests that Teralizer creates. They are used only after generation to remove tests that add no mutant detection beyond the original suite. Held-out mutants would therefore evaluate whether the reduction criterion transfers to another fault sample. They would not remedy overfitting of generalized-test creation because no such feedback occurs.

Optional formal statement:

> Let `T` be the original suite, `G` the generated suite, and `G'` the retained suite. Every test in `G \ G'` kills only mutants already killed by `T`. Thus, for the reduction mutant set `M`, `K_M(T ∪ G') = K_M(T ∪ G)`.

Retain a separate threat statement:

> Mutation score is an established but imperfect proxy for real-fault detection. The evaluation therefore establishes effectiveness against the selected mutation operators, not equivalence to detection of every real fault.

### Developer-anchored, implementation-derived oracles

Candidate core formulation:

> Teralizer constructs developer-anchored, implementation-derived oracles. A developer-written assertion identifies and validates the relevant behavior at the original input, while symbolic analysis generalizes the corresponding implementation relation over the analyzed path.

Candidate exactness qualification:

> When the symbolic model, purity assumptions, and widening checks apply, the generated oracle is path-exact with respect to the analyzed implementation. It is more strongly grounded in developer intent than an implementation-only generated oracle, but it does not prove that every extrapolated input-output pair matches an unstated external specification.

Candidate comparison formulation:

> Developer-written assertions provide trusted oracle evidence for the seed executions. Teralizer combines this evidence with implementation semantics to generalize beyond those seeds, rather than inferring an oracle from the implementation alone.

Do not reduce this framing to the standalone sentence “the result does not establish intended behavior.” That wording omits the developer-written oracle evidence.

### Approach, prototype, and evaluation population

Candidate approach/prototype distinction:

> The semantics-based approach is not inherently restricted to Java, JUnit, or the models supported by the current symbolic backend. Teralizer is a Java prototype of this approach, and its present implementation inherits concrete framework, type, purity, and project-structure restrictions.

Candidate evaluation-population distinction:

> The evaluation does not preselect only methods and data types that Teralizer can generalize. It follows eligible projects, tests, assertions, and generalization attempts through the pipeline and retains unsupported cases, filtering decisions, processing failures, and widening refusals as measured outcomes.

Candidate compact abstract sentence:

> We evaluate the Java prototype in controlled and real-world settings, including unsupported and unsuccessful cases as measured exclusions and failures.

Candidate contribution split:

> We contribute a semantics-based test-generalization approach, a Java prototype that implements the approach, and an empirical evaluation that quantifies both successful generalizations and the barriers that limit current applicability.

Candidate real-world conclusion:

> Across the complete eligible RepoReapers population, including projects that later encounter unsupported constructs or processing failures, the current prototype completes all pipeline stages for 85 of 584 projects.

Do not use “we evaluate the approach on the pure-method and data-type fragment supported by the current analyses.” That sentence falsely narrows the evaluated population to successful cases.

### JARVIS applicability

Candidate result formulation:

> On the shared twelve-project benchmark, Teralizer produces generalized tests for 9 projects and 337 distinct MUTs, compared with the published JARVIS results of 2 projects and 9 distinct MUTs.

Candidate interpretation:

> These results show substantially broader measured applicability than JARVIS on their shared benchmark. They do not constitute an executable head-to-head reproduction because the JARVIS implementation is unavailable.

Candidate PVC qualification:

> Among the ten scenarios for which JARVIS reports successful generalization, Teralizer achieves higher PVC in four, lower PVC in three, and no generated PBT in three. PVC measures exercised input diversity and does not by itself establish fault-detection effectiveness.

Avoid “statistically significantly better” unless a statistical test supports it. Avoid a universal state-of-the-art ranking beyond the shared benchmark.

### RepoReapers version 7 applicability

Candidate historical comparison:

> The rejected manuscript reported completion for 11 of 632 projects (1.7%). After extending the implementation and collecting a new complete RepoReapers run, the revised pipeline completed all stages for 85 of 584 eligible projects (14.6%). The revised pipeline also introduces conservative widening checks that reject specifications without sufficient evidence for sound input generalization.

Candidate interpretation:

> The two measurements use different eligible populations and pipeline semantics and are therefore not a paired before-and-after comparison. They nevertheless show that the revised implementation completes the end-to-end process for a substantially larger share of its declared eligible population.

Candidate balanced applicability synthesis:

> Practical applicability remains limited across arbitrary real-world Java projects. However, the 9-of-12 JARVIS benchmark result and the 85-of-584 RepoReapers result show that Teralizer is materially more applicable than the rejected manuscript reported and more broadly applicable than the closest prior system on their shared benchmark.

Do not write “applicability improved from 1.7% to 14.6% on the same corpus.”

### LLM relationship

Candidate related-work formulation:

> LLM-based techniques can propose assertions and abstractions for semantic domains that the current symbolic backend cannot model, but their outputs can hallucinate or overfit implementation behavior. Teralizer provides complementary path-conditioned evidence and refuses generalization when its widening checks lack sufficient support.

Candidate future-work formulation:

> A hybrid could use LLMs to propose assertions, abstractions, or models and use developer tests, concrete executions, and symbolic path evidence to validate the parts that Teralizer can check. Such a combination remains future work and would not make unvalidated proposals sound by construction.

### Length and research-question structure

Candidate response-letter formulation:

> We reviewed the manuscript for redundancy and condensed local explanations where this did not remove methodological or validity information. We retained the separate research questions because they examine distinct properties through different experimental designs and populations.

Do not promise wholesale condensation or combined RQs when the revised structure remains clearer and within the venue's requirements.

### Independent validation boundary

Candidate response-letter formulation:

> We did not add a held-out-mutant or real-bug experiment to this revision. Held-out mutants would evaluate transfer of the mutation-based reduction criterion, whereas generalized-test creation itself does not consume mutant information. We instead clarify this stage boundary and retain the broader construct-validity limitation that mutation score is an imperfect proxy for real-fault detection.

Use this only if the final resubmission still contains no independent validation. If existing preserved evidence supports a stronger statement, update both the manuscript and response letter from that evidence.

## Risks / Trade-offs

- **[Risk] Thesis wording omits context required by a standalone paper.** → Keep the paper passage until the adapted replacement is independently understandable.
- **[Risk] An empirical value is copied from prose instead of its owner.** → Require a report, provenance, or generated-artifact source before changing it.
- **[Risk] RQ6 values remain stale outside the RQ6 section.** → Run a paper-wide stale-value and denominator audit after the atomic replacement.
- **[Risk] JARVIS results appear more comparable than they are.** → Separate published and measured values and retain reconstruction threats beside the result.
- **[Risk] Scope qualifiers falsely narrow the evaluated population.** → State that exclusions and unsupported cases are measured outcomes.
- **[Risk] Exactness qualifiers weaken the developer-oracle contribution.** → Lead with developer anchoring, then state the implementation-relative boundary.
- **[Risk] The response letter concedes an incorrect mutation-overfitting claim.** → Explain the stage boundary and reduction invariant before discussing construct validity.
- **[Risk] New content increases length.** → Remove actual redundancy only after scientific content stabilizes. Do not combine distinct RQs by default.
- **[Risk] Paper macros cannot consume thesis-generated assets directly.** → Adapt each asset at its publication boundary and verify the rendered result.
- **[Risk] An existing report cannot support a desired comparison.** → Record the gap and omit the claim. Do not rerun an experiment.

## Migration Plan

1. Clone the standalone paper repository and establish a clean baseline build.
2. Create the backport manifest and reviewer matrix without changing scientific content.
3. Apply the revision units in dependency order and commit each verified unit.
4. Run the repository's focused build, bibliography, reference, and style checks after each unit.
5. Run the full manuscript build and inspect the rendered PDF after all units.
6. Finalize the response letter against the completed manuscript.

Rollback uses the atomic commit history. Revert the smallest revision unit whose evidence or rendering is invalid. Do not reset unrelated manuscript work.
