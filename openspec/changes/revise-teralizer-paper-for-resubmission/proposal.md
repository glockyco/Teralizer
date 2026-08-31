## Why

The rejected Teralizer paper no longer describes the implementation or the available evidence. The thesis chapter now contains the authoritative approach revision, the JARVIS comparison, and the complete RepoReapers version 7 evaluation needed for a TOSEM resubmission.

## What Changes

- Revise the manuscript in an editable clone of `glockyco/Teralizer-Paper`. Do not edit `glockyco/Teralizer`, the thesis repository, or the read-only thesis paper submodule.
- Backport substantive approach changes, including the concepts subsection, formal definitions, the end-to-end algorithm, current stage behavior, widening checks, and reduction semantics.
- Add RQ0 with the complete twelve-project JARVIS comparison and its reconstruction limitations.
- Replace the old RepoReapers experiment with version 7 evidence, including current populations, outcomes, exclusion causes, and provenance.
- Update the abstract, introduction, contributions, evaluation framework, discussion, related work, conclusion, tables, figures, macros, citations, artifact statement, and replication-package DOI when the revised evidence affects them.
- Add a `publish.toml` declaration to the paper repository and consume analysis-generated metrics, complete table artifacts, and figure PDFs directly through the Teralizer publication command. Adapt the ACM manuscript to the generated macro and table-label interface. Keep figure wrappers, captions, labels, placement, sizing, and accessibility descriptions publication-specific. Do not add hand-maintained metric aliases, value mappings, or edited copies of generated artifacts.
- State scope separately for RQ0, the shared RQ1--RQ5 controlled evaluation, and the RQ6 real-world evaluation. RQ0 inherits all twelve subject projects from JARVIS's published benchmark without manual Teralizer project selection, but reconstructs project revisions because JARVIS does not report them. RQ1--RQ5 reuse the EqBench and Apache Commons datasets to evaluate complementary aspects of the approach under controlled conditions. RQ5 analyzes failure causes within that shared controlled population. RQ6 follows the selected RepoReapers population and measures successful, unsupported, excluded, and failed cases.
- Describe generated oracles as developer-anchored and implementation-derived. Limit path-exactness claims to the analyzed implementation, symbolic model, purity assumptions, and accepted widening evidence.
- Clarify that mutation analysis does not guide generalized-test creation. Stage 5 removes tests that add no mutant detections beyond the original suite. Its purpose is to reduce the size and execution runtime of the retained generalized suite. It does not improve generalization efficiency because mutation testing adds substantial processing cost.
- State that the identified LLM-based work addresses adjacent specification extraction, test generation, or oracle inference rather than test generalization. Treat a Teralizer and LLM hybrid as a separate possible future contribution.
- Preserve useful standalone-paper material. Exclude thesis-only framing, chapter bridges, monograph cross-references, and cosmetic reflow edits.
- Prepare a TOSEM resubmission letter that maps each reviewer concern to evidence, manuscript revisions, remaining limits, or a reasoned rebuttal.
- Do not add a held-out-mutant, real-bug, or other independent-validation experiment. Do not rerun a corpus, sentinel, hotspot, JARVIS, or project experiment. Use the preserved reports, provenance, generated artifacts, and existing run evidence.

## Capabilities

### New Capabilities

- `publication/teralizer-paper-resubmission`: Defines the scientific-content, evidence, scope, reviewer-response, and verification requirements for the revised Teralizer paper.

### Modified Capabilities

None.

## Impact

- Manuscript repository: standalone clone of `git@github.com:glockyco/Teralizer-Paper.git` under `/Users/glockyco/src/github.com/glockyco/`.
- Implementation and evidence repository: `https://github.com/glockyco/Teralizer`.
- Read-only sources: `../phd-thesis/chapters/05-teralizer/`, the rejected paper submodule, the TOSEM decision letter, and `glockyco/Teralizer` Git history.
- Evidence sources: registered reports, `analysis/reports/provenance.json`, generated publication artifacts, and preserved RepoReapers version 7 data.
- Deliverables: revised ACM manuscript, updated bibliography and publication assets, verified PDF, and TOSEM resubmission letter.
- No Teralizer implementation, analysis publication, database schema, corpus identity, preserved run state, generated dataset, or thesis change is authorized.
