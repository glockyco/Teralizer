## 1. Establish Paper-Local Execution and the Baseline

- [x] 1.1 Clone `glockyco/Teralizer-Paper` outside `projects/`, or verify that the standalone clone is clean and points to the canonical GitHub paper repository
- [x] 1.2 Read the current paper repository guidance, identify the canonical GitHub and GitLab remotes, inspect both CI workflows, and record their effective build, bibliography, style, and PDF-inspection commands
- [x] 1.3 Scaffold `revise-for-resubmission` through OpenSpec in the standalone paper clone before any manuscript edit
- [x] 1.4 Transfer the approved proposal, specification, design, and tasks through the paper-local OpenSpec workflow and store all later traceability controls under that change workspace
- [x] 1.5 Validate the paper-local change, mark tasks 1.1--1.5 complete in its task artifact, and designate it as the only owner of manuscript execution progress
- [x] 1.6 Stop implementation tracking in this source change and resume application from the validated paper-local change without marking later source tasks complete
- [ ] 1.7 Create a dedicated resubmission branch and verify the rejected manuscript commit is reachable from the branch history
- [ ] 1.8 Run the effective local commands for both CI workflows on the unmodified branch and record the successful baseline PDF checksum and page count
- [ ] 1.9 Verify `glockyco/Teralizer`, the thesis paper submodule, and all repositories under `projects/` are unchanged before any manuscript edit
- [ ] 1.10 Record that no remote will be pushed and no remote CI result will be claimed without explicit operator authorization

## 2. Build the Traceability Controls

- [ ] 2.1 Create the section-level backport manifest under the paper-local change workspace with `PORT`, `ADAPT`, `KEEP-PAPER`, `DROP`, and `VERIFY` decisions for all 54 audited changed paths
- [ ] 2.2 Add every Senior Associate Editor and reviewer concern to a response matrix under the paper-local change workspace and verify each decision-letter paragraph has a row
- [ ] 2.3 Map each empirical paper claim to a registered report, provenance entry, generated artifact, or preserved run source in the paper-local evidence map and flag every evidence gap
- [ ] 2.4 Record the section correspondence under the paper-local change workspace and verify every rejected-paper section has an intended source strategy
- [ ] 2.5 Add each design reference formulation to the paper-local formulation register with its manuscript, cover-letter, or prior-review-response destination and a `USE`, `ADAPT`, or `OMIT` disposition
- [ ] 2.6 Add every required RQ0--RQ6 artifact key and destination to the paper's `publish.toml`, run Teralizer's complete-report publication command against preserved evidence, and verify all dependencies, labels, presentation commands, and consumer paths without starting an experiment or changing an evidence repository
- [ ] 2.7 Import the generated aggregate macro file unchanged, verify that the ACM manuscript builds without a hand-maintained metric alias, value mapping, or manual artifact substitute, and record any missing artifact as a blocker
- [ ] 2.8 Commit the verified baseline, paper-local controls, and publication interface with no scientific manuscript changes

## 3. Revise Concepts and the End-to-End Approach

- [ ] 3.1 Add the assertion-to-MUT unit, MUT-call, specification, partition, oracle, and failure-result definitions and verify all later terms resolve to these definitions
- [ ] 3.2 Port and adapt the end-to-end generalization algorithm and verify its stages, inputs, outputs, and retention rules against the evaluated implementation
- [ ] 3.3 Update test and assertion analysis for current JUnit support, filters, collection rounds, and assertion forms and verify the described support against executable declarations
- [ ] 3.4 Update tested-method identification for normalization, composite expressions, zero-or-multiple producers, ranked selection, and unresolved cases and verify each mechanism against code or focused tests
- [ ] 3.5 Update specification extraction for current symbolic execution, type handling, string support, resource limits, and explicit failure outcomes and verify each limit has an implementation source
- [ ] 3.6 Update generalized-test creation for generators, path filters, symbolic oracles, original-input checks, widening refusals, and exception evidence and verify the description against current stage behavior
- [ ] 3.7 Update test-suite reduction for mutant-based retention and source-assertion-based original-test removal and verify the formal rules match the implementation
- [ ] 3.8 Port the new algorithm listing and examples, consume generated table artifacts and figure PDFs directly, adapt manuscript-owned figure captions, labels, placement, sizing, and accessibility descriptions to ACM conventions, and verify the manuscript builds without undefined controls or references
- [ ] 3.9 Review the full approach as a standalone account and verify that it does not depend on thesis chapters or thesis-only cross-references
- [ ] 3.10 Run the recorded manuscript checks, inspect the rendered approach pages, and commit the verified approach revision as one atomic unit

## 4. Reconcile the Evaluation Framework and RQ1--RQ5

- [ ] 4.1 Add RQ0 to the research-question sequence and verify numbering, labels, cross-references, and roadmap text remain consistent through RQ6
- [ ] 4.2 Update the evaluation framework for current variants, sampling budgets, project selection, datasets, timeouts, and measurement terminology and verify that RQ1--RQ5 reuse the same controlled population
- [ ] 4.3 Add the controlled-study table and figure keys to the paper's `publish.toml`, publish them through Teralizer, use generated table labels and manuscript-owned figure labels, and verify the delivered files remain unedited
- [ ] 4.4 Preserve unchanged controlled-study measurements while removing stale terminology and verify no paper value changed without an evidence source
- [ ] 4.5 Run the recorded manuscript checks, inspect the evaluation framework and generated assets, and commit the verified framework revision
- [ ] 4.6 Audit RQ1 and RQ2 against the thesis and generated evidence, verify all mutation-score values and denominators, run the focused manuscript checks, inspect the rendered result, and commit the RQ1--RQ2 revision
- [ ] 4.7 Audit RQ3 test counts, lines of code, retained-suite runtimes, reduction explanations, and filtering terminology against generated evidence, run the focused manuscript checks, inspect the rendered result, and commit the RQ3 revision
- [ ] 4.8 Audit RQ4 processing-time totals, mutation-testing costs, Pareto comparisons, and generalization-efficiency claims against registered reports and figures, run the focused manuscript checks, inspect the rendered result, and commit the RQ4 revision
- [ ] 4.9 Audit RQ5 exclusion terms, percentages, references, and controlled-condition interpretations, verify that RQ5 analyzes failures in the RQ1--RQ4 population, run the focused manuscript checks, inspect the rendered result, and commit the RQ5 revision

## 5. Add RQ0 and the JARVIS Comparison

- [ ] 5.1 Add the JARVIS background and state that RQ0 reuses all twelve projects named by JARVIS without manual Teralizer project selection, document that JARVIS omits its selection procedure and exact revisions, explain the revision-reconstruction method, and verify the ten-scenario protocol and 100-sample configuration against RQ0 provenance
- [ ] 5.2 Add the RQ0 table keys to the paper's `publish.toml`, publish the project-breadth, scenario-PVC, and sampling-budget tables through Teralizer, import them unchanged, and verify every cell and label against the registered RQ0 report
- [ ] 5.3 Report 9 of 12 versus 2 of 12 projects and 337 versus 9 distinct MUTs and verify the manuscript distinguishes measured Teralizer results from published JARVIS results
- [ ] 5.4 Report higher, lower, and missing Teralizer PVC outcomes for the ten scenarios and verify the category counts and individual scenarios
- [ ] 5.5 Explain the PVC-budget result and verify that unchanged mutation detection is not presented as an executed JARVIS fault-effectiveness comparison
- [ ] 5.6 Add JARVIS subject-selection opacity, revision-reconstruction, unavailable-implementation, comparability, PVC, and external-validity limits beside the RQ0 interpretation and verify no universal state-of-the-art claim remains
- [ ] 5.7 Run the recorded manuscript checks, inspect all RQ0 tables, and commit the complete RQ0 package as one atomic unit

## 6. Replace RQ6 with RepoReapers Version 7

- [ ] 6.1 Replace the old selection account with the version 7 population and verify the 584 selected projects, 550 initial-gate exclusions, and 27 projects without executed tests against provenance
- [ ] 6.2 Replace the project lifecycle and stage-entry account and verify each stage population against the generated project-funnel evidence
- [ ] 6.3 Add the current RQ6 artifact keys to the paper's `publish.toml`, publish the dataset, project-funnel, filtering, exclusion, widening-refusal, and processing-failure tables through Teralizer, import them unchanged, and verify every cell and label against the registered reports
- [ ] 6.4 Replace the test-level account and verify included tests, excluded tests, and failures form the reported population
- [ ] 6.5 Replace the assertion-level account and verify 6,905 included, 166,602 filtered, and 7,041 failed assertions total 180,548
- [ ] 6.6 Replace the generalization-level account and verify attempts, widening refusals, emissions, validation, reduction, and final tests use their correct lifecycle denominators
- [ ] 6.7 Report 85 of 584 projects, 14.6%, and 1,435 final generalized tests and verify these values use the version 7 definitions
- [ ] 6.8 Replace the failure interpretation with the evidence-backed three-part cause taxonomy and verify filtering, processing, and widening mechanisms remain distinct
- [ ] 6.9 Add the version 7 provenance, manual-review boundaries, evidence gaps, and validity discussion and verify no reconstructed claim lacks a source
- [ ] 6.10 Search the complete paper for `632`, `11`, `1.7%`, old assertion percentages, retired cause labels, and old lifecycle terms and resolve every stale or intentionally historical occurrence
- [ ] 6.11 Run the recorded manuscript checks, inspect every RQ6 table and page, and commit the complete version 7 replacement as one atomic unit

## 7. Reconcile Cross-Cutting Scientific Claims

- [ ] 7.1 Explain that Stages 1-4 do not consume mutants and verify the statement against the algorithm and implementation account
- [ ] 7.2 Explain that Stage 5 preserves mutation effectiveness only to reduce retained-suite size and execution runtime, then verify that RQ3 and RQ4 distinguish this benefit from mutation-testing cost in the generalization process
- [ ] 7.3 Inspect preserved evidence for unreduced and reduced mutation scores, report an existing comparison if available, and otherwise record the evidence gap without running an experiment
- [ ] 7.4 Revise exactness and intent language to use developer-anchored, implementation-derived, path-bounded claims and verify all prominent exactness phrases follow this contract
- [ ] 7.5 Distinguish the general approach, Java prototype, inherited RQ0 benchmark, shared RQ1--RQ5 controlled evaluation, and RQ6 real-world evaluation in the approach, setup, validity, and discussion and verify that the paper does not present RQ0 as a Teralizer-selected project population or apply RQ6 complete-population claims to another study
- [ ] 7.6 Update the discussion with RQ0 applicability, version 7 applicability, widening as a safety boundary, current failure mechanisms, and remaining prototype restrictions and verify each conclusion cites its evidence
- [ ] 7.7 Position identified LLM techniques under their adjacent specification-extraction, test-generation, or oracle-inference tasks, describe a Teralizer hybrid only as a separate future contribution, and verify the paper does not imply an existing LLM test-generalization approach or evaluated integration
- [ ] 7.8 Preserve the standard mutation-proxy threat, remove the unsupported mutation-overfitting concession, and verify the remaining limitation concerns construct validity rather than test creation
- [ ] 7.9 Review paper length only for genuine repetition and verify that no distinct method, result, research question, or validity condition was removed solely to reduce pages
- [ ] 7.10 Reconcile the formulation register against the revised manuscript and verify every `ADAPT` or `OMIT` decision preserves the design's scientific distinctions
- [ ] 7.11 Run the recorded manuscript checks, inspect the revised discussion and related-work pages, and commit the cross-cutting scientific revision atomically

## 8. Update Paper-Level Framing and Publication Assets

- [ ] 8.1 Rewrite the contribution list to include concepts and algorithm, RQ0, current RQ6 evidence, and a separate Java prototype contribution and verify every contribution is delivered in the body
- [ ] 8.2 Revise the introduction and roadmap after all RQs stabilize and verify every headline result and section reference matches the body
- [ ] 8.3 Revise the abstract with current results and compact scope language and verify it does not falsely narrow the evaluated population or overstate implementation-relative exactness
- [ ] 8.4 Rewrite the standalone conclusion with the current contribution, JARVIS comparison, 85-of-584 result, reduction semantics, and remaining limits and verify it contains no thesis bridge
- [ ] 8.5 Update bibliography entries, citation keys, artifact URLs, and the replication-package DOI and verify all citations resolve in the built manuscript
- [ ] 8.6 Audit the complete paper `publish.toml`, aggregate macro import, generated tables and figure PDFs, listings, publication-specific figure captions and labels, variant names, and RQ identifiers and verify every analysis-owned artifact is direct and unedited while every factual caption claim matches its report and rendered figure
- [ ] 8.7 Run the recorded manuscript checks, inspect the abstract through conclusion in the rendered PDF, and commit the framing and publication-asset revision atomically

## 9. Prepare the TOSEM Submission Correspondence

- [ ] 9.1 Draft a concise new-submission cover letter that identifies TOSEM-2025-1249 as the rejected predecessor, summarizes the substantive revision, and does not imply an invited or requested revision
- [ ] 9.2 Draft the prior-review-response overview around the concepts and algorithm, complete JARVIS benchmark, version 7 RepoReapers evaluation, and claim clarifications and verify each headline maps to manuscript locations
- [ ] 9.3 Answer the mutation-selection concern with the stage boundary, reduction invariant, retained-suite benefit, generalization cost, mutation-proxy limitation, and any existing pre/post evidence and verify the response does not concede generation overfitting
- [ ] 9.4 Answer the applicability concerns with both the JARVIS and RepoReapers results and verify all denominators and comparison limits are explicit
- [ ] 9.5 Answer the exactness and intended-behavior concerns with the developer-anchored, implementation-derived account and verify the response matches paper terminology
- [ ] 9.6 Answer type, purity, project, and framework scope concerns and verify the response distinguishes approach limits from prototype and evaluation outcomes
- [ ] 9.7 Answer the LLM request with the expanded comparison and future direction and verify no unimplemented integration is promised
- [ ] 9.8 Answer the length request by identifying removed redundancy and retained distinct RQs and verify the tone remains factual and respectful
- [ ] 9.9 Mark independent real-bug or held-out-mutant validation as not added, explain the rationale and remaining construct limit, and verify the prior-review response does not imply such evidence exists
- [ ] 9.10 Complete the reviewer matrix with final page, section, figure, table, and listing references and verify every concern has one disposition
- [ ] 9.11 Build or render the cover letter through its declared workflow, inspect it, and commit it as a verified correspondence unit
- [ ] 9.12 Build or render the prior-review response through its declared workflow, inspect it, and commit it as a separate verified correspondence unit

## 10. Perform Submission Verification

- [ ] 10.1 Run the complete manuscript build from a clean generated state and verify the command succeeds without unresolved references or bibliography errors
- [ ] 10.2 Run the effective local commands for both paper CI workflows and the repository's complete style, spelling, hygiene, and pre-commit checks and resolve every task-owned failure
- [ ] 10.3 Compare all manuscript empirical values with the evidence map and verify each measure has its denominator and provenance owner
- [ ] 10.4 Recheck the backport manifest and verify every `PORT`, `ADAPT`, and `VERIFY` item has a final disposition and source location
- [ ] 10.5 Recheck the reviewer matrix and verify every promised revision exists in the final manuscript or prior-review response
- [ ] 10.6 Inspect the complete rendered manuscript, cover letter, and prior-review response in their actual PDF surfaces and resolve layout, clipping, reference, table, and figure defects
- [ ] 10.7 Verify `Teralizer-Paper` is clean except for intended manuscript, correspondence, generated publication, and paper-local OpenSpec files and verify `Teralizer`, the thesis submodule, preserved data, and generated datasets remain unchanged
- [ ] 10.8 Create the final atomic commit for submission-only corrections and verify the full local check set passes on the committed tree
- [ ] 10.9 Report the branch as locally verified and push-ready unless the operator explicitly authorizes synchronization of both canonical remotes
- [ ] 10.10 If synchronization is explicitly authorized, push both canonical remotes, observe both CI workflows, and report their exact results
