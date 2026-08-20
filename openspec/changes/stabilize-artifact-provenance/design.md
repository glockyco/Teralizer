## Context

See proposal.md — Why. The defect is demonstrable rather than inferred: `provenance.json` at `HEAD`
records `57d235ce` for every metric, the file itself lives in `3a7a9f63`, and the attributed report
source last changed in `2a4d4ba3`.

## Goals / Non-Goals

**Goals:**

- A recorded commit identifies the code that produced the artifact.
- Regenerating unchanged inputs yields no diff, so publishing does not dirty the tree it just read.
- Uncertainty is attributed to the file that is uncertain.

**Non-Goals:**

- Removing the clean-tree guard.
- Making committed reports into build output.
- Changing which artifacts carry provenance.

## Decisions

### 1. The commit is the source file's last change, resolved per file and cached

`git log -1 --format=%H -- <file>` is the identity of the code that produced the artifact: whatever
that file contains now, it has contained since that commit.

*Why not `HEAD`:* `HEAD` answers a different question — where the checkout stands — and the two
coincide only immediately after committing that file. Every other moment records a commit whose
relationship to the producing code is accidental.

*Why not the commit containing the artifact:* it cannot be known at write time, which is the root of
the defect rather than a fix for it.

*Caching:* one resolution per distinct file, memoised. Reports capture provenance per metric, so a
report module with forty metrics resolves once.

### 2. The source file comes from the function, not from its module name

`capture` already receives the function and calls `inspect.getsourcelines` on it. It also gets
`inspect.getsourcefile`, which is the real path. The repository-relative path is that path relative to
the repository root.

*Why this replaces rebuilding the path from `__module__`:* the current `rel_path` assumes every
provenance-carrying function lives under `analysis/src/` and that its module name maps to a file path
by replacing dots. Both hold today and neither is checked. Using the file the interpreter loaded
removes the assumption instead of documenting it.

### 3. Dirty is a property of the producing file

`git status --porcelain -- <file>` for the same resolved file.

*Why per file:* a tree-wide flag makes every artifact uncertain whenever anything in the repository is
edited, including a document. That is both wrong and a churn source, since the flag is embedded in the
artifact. Per-file uncertainty says what a reader needs: whether *this* value came from code that is
committed.

*Why the publish guard stays tree-wide:* publishing is an act of attribution across a repository
boundary. A consumer receives a table whose provenance points into this repository, and a reviewer
cannot tell which of this repository's files mattered. Refusing the whole act is the honest guard, and
it is orthogonal to what an individual artifact records.

### 4. A file with no commit records `HEAD` and is dirty

A new report module that has never been committed has no last-changing commit. Recording an empty
string would produce a broken permalink, so it records `HEAD` and sets dirty, which is exactly the
statement "this came from code you cannot look up".

*Alternative considered:* fail the run. Rejected — writing a report from new code is the normal state
during development, and the dirty flag already carries the warning.

### 5. Figure metadata resolves through the figure's build function

`materialize` embeds a provenance string in each image. It currently calls `git_commit()`. A `Figure`
carries its `build` callable, and `inspect.getsourcefile(build)` is the report module that declares it,
so the figure resolves by the same rule as every other artifact.

*Consequence:* the PNG and PDF for a figure stop changing on unrelated commits, which is what makes
the committed PNGs stable.

## Risks / Trade-offs

- **A one-time rewrite of every committed report output.** → Accepted and expected. The first
  regeneration writes correct commits; after that the outputs are stable. Reviewing that commit means
  checking that only commit fields moved, which is mechanical.

- **A report reading a helper module gets the commit of its own module, not the helper's.** → Correct
  for the artifact, since provenance points at the function that produced the value, and a reader
  following the permalink lands on the call site. Recording a set of commits per artifact would be
  more complete and less usable, and no consumer asks for it.

- **`git log` per file adds subprocess calls.** → Bounded by the number of distinct producing files,
  memoised, and dwarfed by the queries. Measured rather than assumed as part of the task list.

- **Two changes touch `provenance.py` and `render/figures.py`.** → `publish-figures-to-consumers` has
  already landed its edits to both. This change is sequenced after it and rebases onto it rather than
  running beside it.

## Open Questions

- **Should `provenance.json` record the artifact's own path relative to the consuming repository when
  published?** It would let a consumer trace a delivered file back without consulting the manifest.
  Out of scope here, and it changes no decision above.
