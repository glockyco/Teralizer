## Purpose

Defines the evidence, scientific content, claim boundaries, reviewer response, and publication checks for a revised Teralizer paper and TOSEM resubmission.

## ADDED Requirements

### Requirement: Standalone revision source
The revision MUST occur in an editable standalone clone of the paper repository. The thesis paper submodule and all repositories under `projects/` MUST remain unchanged.

#### Scenario: Revision workspace is prepared
- **WHEN** manuscript revision starts
- **THEN** the editable files exist in a standalone `test-generalization-paper` clone outside `projects/`
- **THEN** the thesis submodule remains unchanged

### Requirement: Selective scientific backport
The revised paper MUST incorporate substantive scientific changes from the thesis chapter. It MUST preserve standalone explanations that the thesis omits because other chapters provide them.

#### Scenario: Thesis and paper passages differ
- **WHEN** a thesis passage changes evidence, method, implementation behavior, interpretation, or validity
- **THEN** the paper incorporates the scientific change with standalone wording
- **THEN** it does not copy thesis-only framing, chapter bridges, or cosmetic reflow edits

### Requirement: Current approach account
The revised approach MUST define the assertion-to-MUT pair, MUT calls, input-output specifications, input partitions, generalized oracles, failure results, retention rules, and original-test removal rules. It MUST include the end-to-end algorithm and current stage behavior.

#### Scenario: Reader follows the transformation
- **WHEN** a reader examines the approach without consulting the implementation
- **THEN** the paper identifies every pipeline stage, its inputs, its outputs, and its failure outcome
- **THEN** the algorithm agrees with the evaluated implementation

### Requirement: Approach, prototype, and evaluation scope
The paper MUST distinguish the general approach from the Java prototype and from the evaluated population. It MUST NOT claim that the evaluation includes only pure methods or only supported data types.

#### Scenario: Evaluation scope is summarized
- **WHEN** the abstract, contributions, evaluation design, or conclusion describes empirical scope
- **THEN** it states that eligible projects, tests, assertions, and attempts include both successful and excluded cases
- **THEN** it identifies prototype restrictions without presenting them as inherent limits of the general approach

#### Scenario: Detailed prototype limits are needed
- **WHEN** the paper explains implementation or validity boundaries
- **THEN** it states the applicable Java, build-system, test-framework, type, purity, symbolic-model, and project-structure limits in the relevant detailed section

### Requirement: JARVIS comparison
The revised paper MUST include RQ0 for all twelve Apache Commons projects used in the JARVIS evaluation. It MUST separate project applicability, the ten published JARVIS scenarios, and mutation-effectiveness evidence.

#### Scenario: Applicability is compared
- **WHEN** RQ0 reports benchmark applicability
- **THEN** it reports generalized tests for 9 of 12 projects for Teralizer and 2 of 12 projects for JARVIS
- **THEN** it reports PBTs for 337 distinct MUTs for Teralizer and 9 distinct MUTs for JARVIS

#### Scenario: Published scenarios are compared
- **WHEN** RQ0 reports PVC for the ten published JARVIS scenarios
- **THEN** it reports where Teralizer has higher PVC, lower PVC, or no generated PBT
- **THEN** it does not imply that the unavailable JARVIS implementation was executed

#### Scenario: JARVIS validity is discussed
- **WHEN** the paper interprets RQ0
- **THEN** it states the project-reconstruction, unavailable-implementation, scenario-comparability, and PVC construct limitations
- **THEN** it characterizes the result as benchmark evidence rather than a universal state-of-the-art ranking

### Requirement: RepoReapers version 7 evaluation
The revised paper MUST replace the rejected manuscript's RepoReapers account with the preserved version 7 run. It MUST use current population definitions, lifecycle boundaries, outcomes, causes, and provenance.

#### Scenario: Headline applicability is reported
- **WHEN** the paper reports end-to-end RepoReapers applicability
- **THEN** it reports completion for 85 of 584 eligible projects, or 14.6%
- **THEN** it does not describe this as a paired increase from the old 632-project population

#### Scenario: Exclusions are part of the evaluation
- **WHEN** the paper reports test, assertion, generalization, or project outcomes
- **THEN** the denominator includes the applicable eligible or attempted population
- **THEN** unsupported cases and failures remain measured outcomes rather than being removed from the evaluation scope

#### Scenario: Causes are interpreted
- **WHEN** RQ6 explains unsuccessful attempts
- **THEN** it uses the evidence-backed three-part cause taxonomy and current generated tables
- **THEN** it distinguishes filtering decisions, processing failures, widening refusals, and lifecycle outcomes

### Requirement: Evidence authority and provenance
Every empirical value MUST trace to a registered report, generated publication artifact, preserved run record, or `analysis/reports/provenance.json`. The revision MUST NOT rerun any corpus or project experiment.

#### Scenario: A manuscript value changes
- **WHEN** a revised paragraph, macro, table, or figure contains an empirical value
- **THEN** the value and denominator agree with its authoritative generated evidence
- **THEN** the manuscript does not substitute a thesis transcription or rejected-paper value

#### Scenario: Evidence is unavailable
- **WHEN** preserved sources cannot establish a requested claim
- **THEN** the manuscript records the evidence gap or removes the claim
- **THEN** it does not infer a value or authorize a rerun

### Requirement: Mutation-analysis semantics
The paper MUST state that mutation results do not influence specification extraction or generalized-test creation. It MUST describe Stage 5 as post-generation filtering against mutant detections beyond the original suite.

#### Scenario: Effectiveness and reduction use the same mutants
- **WHEN** the paper addresses the reviewer's mutation-overfitting concern
- **THEN** it distinguishes mutation-independent generation from mutation-based retention
- **THEN** it explains that removing mutation-redundant generated tests cannot increase the score over the unreduced generated suite
- **THEN** it retains the ordinary construct-validity limitation of mutation score as a proxy for real faults

#### Scenario: Existing evidence supports a reduction comparison
- **WHEN** preserved results contain mutation scores before and after Stage 5
- **THEN** the paper reports the comparison with test-count or runtime effects
- **THEN** it does not launch a new experiment to produce the comparison

### Requirement: Oracle evidence and exactness
The paper MUST describe generalized oracles as developer-anchored and implementation-derived. It MUST distinguish path fidelity from proof of unstated developer intent.

#### Scenario: Exactness is claimed
- **WHEN** the abstract, contributions, approach, discussion, related work, or conclusion uses an exactness claim
- **THEN** the claim is bounded by the analyzed implementation path, symbolic model, purity assumptions, and accepted widening evidence
- **THEN** it states that developer-written assertions identify and validate the seed behavior

#### Scenario: Intent is discussed
- **WHEN** the paper compares Teralizer with implementation-only oracle generation
- **THEN** it explains why developer-written assertions provide stronger intent evidence
- **THEN** it does not claim that one concrete assertion proves intended behavior for every generalized input

### Requirement: Paper-wide consistency
All paper sections, generated assets, and bibliography entries MUST use the same research-question numbering, variant names, populations, terminology, and current artifact identity.

#### Scenario: Scientific content is updated
- **WHEN** RQ0, RQ6, implementation behavior, or replication metadata changes
- **THEN** the abstract, introduction, framework, discussion, conclusion, tables, figures, macros, citations, and artifact statement are audited for dependent changes
- **THEN** no stale headline value or superseded replication-package DOI remains

### Requirement: Focused LLM comparison
The related-work discussion MUST compare Teralizer with LLM-based specification and oracle inference. It MUST present unimplemented combinations only as future work.

#### Scenario: LLM work is positioned
- **WHEN** the paper discusses SpecGen, ClassInvGen, or inferred test oracles
- **THEN** it compares semantic breadth, path evidence, soundness limits, and implementation-overfitting risk
- **THEN** it identifies a bounded complementary role without claiming evaluated integration

### Requirement: Reviewer response
The resubmission package MUST include a response letter that maps each editorial and reviewer concern to a manuscript change, existing evidence, remaining limitation, or reasoned rebuttal.

#### Scenario: A concern is not accepted as stated
- **WHEN** the authors reject the reviewer's causal interpretation or requested experiment
- **THEN** the letter explains the mechanism and supporting evidence respectfully
- **THEN** it states any narrower limitation that remains

#### Scenario: Applicability improvement is summarized
- **WHEN** the letter compares rejected and revised RepoReapers results
- **THEN** it gives both measures and denominators separately
- **THEN** it discloses the changed implementation, eligibility, and lifecycle semantics

### Requirement: Publication verification
The revised manuscript and response letter MUST pass the paper repository's focused publication checks before the change is complete.

#### Scenario: Revision is ready for submission
- **WHEN** all scientific revisions are complete
- **THEN** the manuscript builds through its repository wrapper
- **THEN** bibliography, labels, references, generated assets, and style checks pass
- **THEN** the rendered PDF is inspected for content and layout defects
