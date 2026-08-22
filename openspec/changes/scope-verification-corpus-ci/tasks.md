## 1. Establish the Owner-Path Contract

- [x] 1.1 Trace every executable, sourced helper, configuration file, fixture root, golden root, schema input, build declaration, and submodule read by `scripts/verify-pipeline.sh` and record the complete owner-path inventory in `design.md`.
- [x] 1.2 Compare the inventory with recent corpus-regression commits and add any path whose omission would have skipped a known useful failure.
- [x] 1.3 Define representative positive path cases for every owner group and negative cases for OpenSpec, documentation, and analysis report-renderer changes.

## 2. Add Declaration-Level Verification

- [x] 2.1 Add a focused repository check that parses `.github/workflows/verification-corpus.yml` and evaluates its push path patterns against the positive and negative path table.
- [x] 2.2 Make the check assert retained `workflow_dispatch` and weekly schedule triggers, branch-scoped cancellation, and the 35-minute job timeout.
- [x] 2.3 Run the check against deliberate missing-owner, over-broad-owner, absent-trigger, global-concurrency, and missing-timeout fixtures; confirm that each fails with the violated contract named.

## 3. Scope and Bound the Hosted Workflow

- [x] 3.1 Add native push `paths` filters covering the completed owner-path inventory without adding a changed-files action or runtime dispatch job.
- [x] 3.2 Set the corpus job timeout to 35 minutes and scope its concurrency group by workflow and ref while retaining `cancel-in-progress: true`.
- [x] 3.3 Update workflow comments to describe the executable ownership boundary, weekly drift run, manual dispatch, timeout, and same-ref supersession without copying the full path inventory into prose.

## 4. Verify Repository Behavior

- [x] 4.1 Validate the workflow declaration and run the focused trigger-contract check, including every positive and negative case.
- [x] 4.2 Run `nix develop --command lefthook run pre-commit --all-files` and `openspec validate scope-verification-corpus-ci --strict` plus `openspec validate --all --strict`.
- [x] 4.3 Review the final diff against the owner-path inventory and confirm that the change affects only corpus scheduling, timeout control, declaration verification, and planning state.

## 5. Prove Hosted Scheduling

- [x] 5.1 Obtain explicit operator approval for the corpus-starting push, commit the implementation as one causal change, and push it.
- [x] 5.2 Confirm that the workflow-file change schedules exactly one full verification-corpus run, that the run completes within 35 minutes, and that its checked-out revision equals the pushed revision; run `https://github.com/glockyco/Teralizer/actions/runs/32595768441` succeeded in 21m15s at revision `c696378491e8b0ad01d74c37975fb2c7b39dffbd`.
- [x] 5.3 Push the OpenSpec-only evidence update and confirm that no verification-corpus run is created for revision `0355f1a2b095fb49b8329f6b82df205004ffb6c0`; the adjacent verification-corpus run range remained `32594956181`–`32595768441`, while ordinary CI run `32596866499` succeeded independently.
- [ ] 5.4 Re-run strict OpenSpec validation and archive only after the relevant-path run and unrelated-path non-run are both recorded.
