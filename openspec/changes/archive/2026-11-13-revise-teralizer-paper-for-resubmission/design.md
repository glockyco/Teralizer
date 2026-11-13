## Context

See `proposal.md` for motivation and the delta spec for publication requirements. The rejected paper is a standalone manuscript with explanations that the thesis intentionally omits. The thesis chapter contains 161 later commits and 54 changed paths, but many changes are thesis framing, layout work, or generated presentation changes.

`glockyco/Teralizer` and `glockyco/Teralizer-Paper` are separate repositories with different responsibilities. Executable declarations, tests, registered reports, provenance, and generated evidence belong to `Teralizer`. The editable manuscript and its publication assets belong to `Teralizer-Paper`. The thesis chapter is the preferred scientific narrative for changes after the paper import. The rejected paper remains authoritative for standalone framing and material that other thesis chapters replace.

The paper submodule under `../phd-thesis/projects/` is read-only. Revision work must use a standalone clone. The thesis and Teralizer repositories remain read-only sources for this change. Corpus and project reruns require explicit operator approval and are outside this change.

## Goals / Non-Goals

**Goals:**

- Produce one coherent standalone paper from the rejected manuscript and selected thesis revisions.
- Preserve traceability from each empirical claim to authoritative evidence.
- Separate approach scope, prototype support, evaluation population, and measured exclusions.
- Resolve reviewer concerns through manuscript changes or explicit, evidence-based rebuttals.
- Keep the manuscript buildable after each coherent revision unit.
- Make generated metrics, complete table artifacts, and figure PDFs flow directly from Teralizer reports into the paper.

**Non-Goals:**

- Synchronize every textual difference between the thesis and paper.
- Make the paper text identical to the thesis chapter.
- Change Teralizer, the analysis publication interface, the thesis, or any measurement.
- Add held-out-mutant, real-bug, LLM-integration, or new JARVIS experiments.
- Shorten or combine research questions only to reduce page count.

## Decisions

### 1. Revise an independent paper clone

Use `gh repo clone glockyco/Teralizer-Paper` under `/Users/glockyco/src/github.com/glockyco/`. Verify that `origin` resolves to `glockyco/Teralizer-Paper`, not `glockyco/Teralizer`. Create a dedicated branch before manuscript edits.

The current OpenSpec change authorizes edits only inside `glockyco/Teralizer`. After the clone is ready, scaffold the same approved change in `Teralizer-Paper`. Copy the approved planning content through the OpenSpec workflow. Use the paper-local change for task progress and manuscript execution. Retain this change as the planning and evidence record. Do not mark its implementation tasks complete for work performed in another repository.

Store the work log, backport manifest, reviewer matrix, evidence map, section correspondence, and formulation register under the paper-local change workspace. Do not place these controls in the manuscript root or submission bundle.

This preserves the thesis submodule and gives the paper an independent, reviewable history. Editing the submodule was rejected because repository rules make `projects/` read-only. This repository boundary is an editing safeguard, not manuscript framing. The paper identifies the public replication package through its Zenodo record.

The paper repository guidance requires equivalent GitHub and GitLab states and green CI on both remotes. Record both canonical remote URLs and inspect both build workflows before editing. Verify each revision locally against their effective commands. Pushing and remote CI execution require explicit operator authorization. Until that authorization is given, describe the branch as locally verified and push-ready, not remotely synchronized or green.

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
 submission correspondence
```

This order keeps high-level claims dependent on stable methods and results. Updating the abstract first was rejected because it creates repeated rewrites and stale intermediate claims.

### 5. Treat RQ0 as three bounded comparisons

RQ0 inherits its subject projects from JARVIS's published benchmark. The Teralizer team did not manually choose project subjects or define a new project population. JARVIS names twelve Apache Commons projects, but it does not report their exact revisions, the evaluation date, or its project-selection procedure. State that the project set is reused and that only the revisions are reconstructed through the documented cutoff rule.

Separate these analyses:

1. project-level applicability across all twelve JARVIS projects.
2. PVC for the ten scenarios that JARVIS reported successfully.
3. the relation between PVC and mutation detection in Teralizer.

Report JARVIS values as published and Teralizer values as measured. State that JARVIS was not executed because its implementation is unavailable. Preserve subject-selection opacity, revision-reconstruction, and comparability threats.

Use “substantially broader measured applicability on the shared benchmark.” Do not use statistical significance or a universal state-of-the-art claim.

### 6. Replace RQ6 as one atomic evidence package

Replace its population, selection method, lifecycle model, tables, generated macros, result narrative, and causal interpretation together. Audit every old `632`, `11`, `1.7%`, and related denominator outside the RQ6 section.

The revised account includes selected cases that later fail or are excluded. It must not describe the evaluation as restricted to successful pure methods or supported data types. Prototype limits explain outcomes. They do not define away measured exclusions.

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

For mutant set `M`, original suite `T`, and generated suite `G`, every discarded test kills only mutants already killed by `T`. Therefore, `K_M(T ∪ G')` equals `K_M(T ∪ G)`.

The sole purpose of Stage 5 is to avoid bloat in the retained generalized test suite. Every candidate PBT and every detection capability it provides already exist before reduction. Stage 5 preserves the unreduced suite's mutation score while reducing the retained suite's test count and execution runtime. It must not receive credit for the effectiveness gain.

This benefit belongs to the resulting generalized test suite, not to the efficiency of generalization. Stage 5 invokes expensive mutation testing and increases Teralizer's processing cost. RQ3 must describe the resulting suite's size and runtime. RQ4 must account for mutation testing in end-to-end processing efficiency.

Dropping Stage 5 retains every generated PBT and therefore preserves the full pre-reduction suite capability at higher downstream execution cost. It also avoids the mutation-testing cost of reduction. Another retention criterion could replace mutation score if it defines and preserves the capability that the reduced suite must retain. An arbitrary criterion does not guarantee preservation under unrelated effectiveness metrics.

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
- the inherited JARVIS benchmark in RQ0.
- the shared controlled evaluation in RQ1--RQ5.
- the real-world evaluation in RQ6.
- conclusions supported by each experiment.

RQ0 reuses JARVIS's twelve named subject projects rather than defining a Teralizer-selected population. Because the published benchmark omits exact revisions, the reconstructed revisions are a validity limitation rather than a subject-selection decision.

RQ1--RQ5 reuse the same primary evaluation datasets to study effectiveness, constraint complexity, retained-suite size and runtime, processing efficiency, and failure causes under controlled conditions. EqBench supplies numeric-focused benchmark cases. Selected numeric utility methods from Apache Commons bridge toward real-world complexity while retaining the controlled focus. RQ5 does not own this subject selection. It analyzes unsuccessful attempts within the same population already used by RQ1--RQ4.

RQ6 measures practical applicability by following selected RepoReapers projects, tests, assertions, and attempts through successful, unsupported, excluded, and failed outcomes. Apply the complete-population exclusion framing to RQ6.

Keep implementation versions and detailed restrictions in the approach, setup, and validity sections. Use short qualifiers in the abstract and conclusion. Preserve the distinction among the inherited RQ0 benchmark, the shared RQ1--RQ5 controlled evaluation, and the RQ6 real-world evaluation whenever the paper summarizes evaluation scope.

### 10. Preserve the paper's standalone architecture

Keep the paper's background, motivation, contribution list, full related work, and conclusion structure unless scientific changes require revision. Adapt thesis cross-references to paper sections. Do not port the thesis bridge or references to other thesis chapters.

Before scientific edits, add a `publish.toml` declaration to `Teralizer-Paper`. Run `PAPER_OUT=<paper-clone> ./scripts/publish-analysis.sh` from a clean `Teralizer` worktree against preserved evidence. This preflight must resolve every required RQ0--RQ6 artifact key, consumer path, generated dependency, label, and ACM presentation command. It must leave `Teralizer`, the thesis, preserved data, and generated datasets unchanged. It must not start a corpus, project, or JARVIS experiment. If an artifact is unavailable, record the gap and revise the plan. Do not create a manual substitute.

Import the new algorithm listing and required concepts. Publish every analysis-owned macro, table, and figure through the verified command. Import the generated aggregate macro file unchanged. Replace hard-coded empirical values with its `Tz` macros and update cross-references to generated table labels.

Generated ownership stops at the figure PDF. The thesis and paper may require different figure environments, captions, labels, placement, sizing, and accessibility descriptions. Keep those structures in each manuscript and adapt them to its layout. Verify factual statements in each caption against the registered report and rendered figure.

Do not introduce paper-specific aliases for generated metric macros, hand-maintained value mappings, or edited generated macros, tables, or figure PDFs. Those layers would become stale when a registered report changes. The ACM document may define presentation-only commands such as the table body style expected by the generator. Such commands must not contain empirical values or rename the generated macro or table interface.

### 11. Add a bounded LLM synthesis

State that the identified LLM-based techniques address adjacent specification extraction, test generation, or oracle inference rather than test generalization. Discuss each technique in the related-work category that owns its task. Contrast LLM semantic breadth with Teralizer's path evidence, refusal behavior, and current model restrictions where the comparison is informative.

A hybrid could potentially strengthen results by proposing assertions, abstractions, or models. Treat that hybrid as a separate possible future contribution. Do not present it as part of the current approach or evaluation.

### 12. Separate the cover letter from the prior-review response

TOSEM treats a revised rejected manuscript as a new submission. Prepare a concise cover letter that identifies TOSEM-2025-1249 as the rejected predecessor and summarizes the substantive revision. Do not describe the new manuscript as an invited revision, a major revision, or a continuation of the previous review round.

Prepare a separate point-by-point prior-review response from a live reviewer matrix. Maintain one row for every editorial and reviewer concern. Each row records the response type, manuscript locations, evidence, and remaining limitation.

Use these response types:

- accepted and revised.
- addressed by new evidence.
- partially addressed with a stated boundary.
- rebutted with mechanism and evidence.
- not implemented, with rationale.

Draft both documents after manuscript claims stabilize. This prevents either document from promising changes that the paper does not contain. Render and inspect each document separately.

### 13. Commit coherent revision units

Use separate commits for the source baseline, publication preflight, approach, evaluation framework, RQ1--RQ2, RQ3, RQ4, RQ5, RQ0, RQ6, cross-cutting claims, related work, cover letter, and prior-review response. Each commit must include the affected generated assets and focused verification.

RQ0 and RQ6 remain atomic evidence packages. Their populations, tables, denominators, results, and validity arguments must change together. Do not combine all RQ1--RQ5 revisions into one commit. Their distinct measures and interpretations require smaller reviewable units.

This structure permits rollback and reviewer-oriented inspection. A single manuscript-wide commit was rejected because it would hide causality and make stale-value failures harder to locate.

## Reference Formulations

These formulations are starting points for the manuscript and prior-review response. They are not mandatory verbatim text. Adapt tense, notation, and local context without weakening the stated distinction. Record each material adaptation in the backport manifest.

### Mutation-independent generation and mutation-based reduction

Candidate manuscript formulation:

> Mutation analysis does not guide specification extraction or generalized-test creation. Teralizer first produces every candidate PBT from the implementation and developer-written tests. Stage 5 then removes tests that do not kill any mutant beyond those already killed by the original suite. This reduction preserves the unreduced suite's mutation score while reducing the retained suite's size and execution runtime. It does not improve generalization efficiency because mutation testing adds substantial processing cost.

Candidate prior-review-response formulation:

> We agree that mutation score remains a proxy for real-fault detection. However, mutants do not influence the generalized tests that Teralizer creates. Every candidate PBT exists before mutation testing begins. Mutation testing only selects a smaller retained suite with the same mutation score as the unreduced suite. This lowers the retained suite's size and execution runtime but makes Teralizer's processing more expensive. Held-out mutants would therefore evaluate whether the reduction criterion transfers to another fault sample. They would not remedy overfitting of generalized-test creation because no such feedback occurs.

Candidate no-reduction formulation:

> Teralizer could omit Stage 5 and retain every generated PBT. This would preserve the full detection capability of the unreduced suite and avoid the cost of mutation-based selection, but it would produce a larger generalized suite with higher downstream execution cost.

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

Candidate controlled-evaluation distinction:

> RQ1--RQ5 reuse the EqBench and Apache Commons datasets to evaluate complementary aspects of Teralizer under controlled conditions. RQ5 analyzes unsuccessful attempts within this shared population rather than defining a separate failure-oriented subject selection.

Candidate real-world-evaluation distinction:

> RQ6 does not preselect only methods and data types that Teralizer can generalize. It follows selected RepoReapers projects, tests, assertions, and generalization attempts through the pipeline and retains unsupported cases, filtering decisions, processing failures, and widening refusals as measured outcomes.

Candidate compact abstract sentence:

> We evaluate Teralizer's effectiveness, efficiency, and applicability. RQ1--RQ5 use shared controlled datasets, while RQ6 evaluates real-world applicability across selected RepoReapers projects.

Candidate contribution split:

> We contribute a semantics-based test-generalization approach, a Java prototype that implements the approach, and an empirical evaluation that quantifies both successful generalizations and the barriers that limit current applicability.

Candidate real-world conclusion:

> Across all 584 selected RepoReapers projects, including projects that later encounter unsupported constructs or processing failures, the current prototype completes all pipeline stages for 85 projects.

Do not use “we evaluate the approach on the pure-method and data-type fragment supported by the current analyses” as a summary of the complete evaluation or of RQ6. The controlled datasets deliberately emphasize cases suited to numeric symbolic analysis, but RQ6 measures exclusions and failures across the selected real-world population.

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

> The rejected manuscript reported completion for 11 of 632 projects (1.7%). After extending the implementation and collecting a new complete RepoReapers run, the revised pipeline completed all stages for 85 of 584 selected projects (14.6%). The revised pipeline also introduces conservative widening checks that reject specifications without sufficient evidence for sound input generalization.

Candidate interpretation:

> The two measurements use different project selections and pipeline semantics and are therefore not a paired before-and-after comparison. They nevertheless show that the revised implementation completes the end-to-end process for a substantially larger share of the selected projects.

Candidate balanced applicability synthesis:

> Practical applicability remains limited across arbitrary real-world Java projects. However, the 9-of-12 JARVIS benchmark result and the 85-of-584 RepoReapers result show that Teralizer is materially more applicable than the rejected manuscript reported and more broadly applicable than the closest prior system on their shared benchmark.

Do not write “applicability improved from 1.7% to 14.6% on the same corpus.”

### LLM relationship

Candidate related-work formulation:

> The identified LLM-based techniques address adjacent tasks such as specification extraction, test generation, and oracle inference rather than test generalization. Such techniques can propose assertions and abstractions for semantic domains that the current symbolic backend cannot model, but their outputs can hallucinate or overfit implementation behavior. Teralizer provides complementary path-conditioned evidence and refuses generalization when its widening checks lack sufficient support.

Candidate future-work formulation:

> A hybrid could use LLMs to propose assertions, abstractions, or models and use developer tests, concrete executions, and symbolic path evidence to validate the parts that Teralizer can check. Such a combination remains future work and would not make unvalidated proposals sound by construction.

### Length and research-question structure

Candidate prior-review-response formulation:

> We reviewed the manuscript for redundancy and condensed local explanations where this did not remove methodological or validity information. We retained the separate research questions because they examine distinct properties through different experimental designs and populations.

Do not promise wholesale condensation or combined RQs when the revised structure remains clearer and within the venue's requirements.

### Independent validation boundary

Candidate prior-review-response formulation:

> We did not add a held-out-mutant or real-bug experiment to this revision. Held-out mutants would evaluate transfer of the mutation-based reduction criterion, whereas generalized-test creation itself does not consume mutant information. We instead clarify this stage boundary and retain the broader construct-validity limitation that mutation score is an imperfect proxy for real-fault detection.

Use this response in the final prior-review response. Do not describe preserved mutation-score or PVC evidence as independent validation.

## Risks / Trade-offs

- **[Risk] Implementation is tracked from an OpenSpec change that does not authorize the paper clone.** → Scaffold the executable change in `Teralizer-Paper` before manuscript edits.
- **[Risk] Local success is reported as dual-remote success.** → Require explicit push authorization and observed CI results before claiming synchronization or green remote builds.
- **[Risk] Traceability controls leak into the submission bundle.** → Keep them under the paper-local OpenSpec change workspace.
- **[Risk] Thesis wording omits context required by a standalone paper.** → Keep the paper passage until the adapted replacement is independently understandable.
- **[Risk] An empirical value is copied from prose instead of its owner.** → Require a report, provenance, or generated-artifact source before changing it.
- **[Risk] RQ6 values remain stale outside the RQ6 section.** → Run a paper-wide stale-value and denominator audit after the atomic replacement.
- **[Risk] JARVIS results appear more comparable than they are.** → Separate published and measured values and retain reconstruction threats beside the result.
- **[Risk] Scope qualifiers collapse the controlled and real-world designs.** → State the shared RQ1--RQ5 controlled population separately. State that RQ6 exclusions and unsupported cases remain measured real-world outcomes.
- **[Risk] Exactness qualifiers weaken the developer-oracle contribution.** → Lead with developer anchoring, then state the implementation-relative boundary.
- **[Risk] The prior-review response concedes an incorrect mutation-overfitting claim.** → Explain the stage boundary and reduction invariant before discussing construct validity.
- **[Risk] New content increases length.** → Remove actual redundancy only after scientific content stabilizes. Do not combine distinct RQs by default.
- **[Risk] Generated LaTeX assumes thesis-only presentation commands.** → Make the ACM preamble provide the generator's presentation-only contract. Keep metric macros, complete generated tables, and figure PDFs unchanged and verify the rendered result.
- **[Risk] Publication-specific figure captions drift from the evidence.** → Permit layout-specific wording, labels, and accessibility text, but verify every factual caption claim against the registered report and rendered figure.
- **[Risk] An existing report cannot support a desired comparison.** → Record the gap and omit the claim. Do not rerun an experiment.

## Migration Plan

1. Clone the standalone paper repository and verify both canonical remotes.
2. Scaffold the paper-local OpenSpec change and transfer the approved planning artifacts through the OpenSpec workflow.
3. Establish the clean baseline build, PDF checksum, page count, and effective CI commands.
4. Create the traceability controls under the paper-local change workspace.
5. Add `publish.toml` and complete the publication-interface preflight without changing scientific content.
6. Apply the revision units in dependency order and commit each verified unit.
7. Run the focused build, bibliography, reference, and style checks after each unit.
8. Run the full manuscript build and inspect the rendered PDF after all units.
9. Finalize and inspect the cover letter and prior-review response against the completed manuscript.
10. With explicit operator authorization, synchronize both remotes and verify both CI workflows.

Rollback uses the atomic commit history. Revert the smallest revision unit whose evidence or rendering is invalid. Do not reset unrelated manuscript work.
