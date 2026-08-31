## Why

The rejected Teralizer paper no longer describes the implementation or the available evidence. The thesis chapter now contains the authoritative approach revision, the JARVIS comparison, and the complete RepoReapers version 7 evaluation needed for a TOSEM resubmission.

## What Changes

- Revise the manuscript in an editable clone of `glockyco/Teralizer-Paper`. Do not edit `glockyco/Teralizer` or the read-only thesis submodule.
- Backport substantive approach changes, including the concepts subsection, formal definitions, the end-to-end algorithm, current stage behavior, widening checks, and reduction semantics.
- Add RQ0 with the complete twelve-project JARVIS comparison and its reconstruction limitations.
- Replace the old RepoReapers experiment with version 7 evidence, including current populations, outcomes, exclusion causes, and provenance.
- Update the abstract, introduction, contributions, evaluation framework, discussion, related work, conclusion, tables, figures, macros, citations, artifact statement, and replication-package DOI when the revised evidence affects them.
- State scope precisely without falsely limiting the evaluation to supported cases. The evaluation includes eligible supported and unsupported cases and measures exclusions throughout the pipeline.
- Describe generated oracles as developer-anchored and implementation-derived. Limit path-exactness claims to the analyzed implementation, symbolic model, purity assumptions, and accepted widening evidence.
- Clarify that mutation analysis does not guide generalized-test creation. Stage 5 only removes generalized tests that add no mutant detections beyond the original suite.
- Add a focused comparison with LLM-based specification and oracle inference. Treat hybrid use as future work, not an evaluated contribution.
- Preserve useful standalone-paper material. Exclude thesis-only framing, chapter bridges, monograph cross-references, and cosmetic reflow edits.
- Prepare a TOSEM resubmission letter that maps each reviewer concern to evidence, manuscript revisions, remaining limits, or a reasoned rebuttal.
- Do not rerun a corpus, sentinel, hotspot, JARVIS, or project experiment. Use the preserved reports, provenance, generated artifacts, and existing run evidence.

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
- No Teralizer implementation, database schema, corpus identity, preserved run state, or generated dataset changes are authorized by this change.
