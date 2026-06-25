---
title: "Paper Repo Agent Setup Implementation Plan"
type: plan
status: implemented
created: 2026-06-24
parent: 2026-06-24-agent-instruction-files-normalization-design
archived: 2026-06-25
---

# Paper Repo Agent Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan **inline** (batch execution with review checkpoints) — **no subagents, no worktrees**. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consolidate `test-generalization-paper` to one canonical `AGENTS.md` (symlinked `CLAUDE.md`/`GEMINI.md`), delete the 28 KB ceremony-laden `CLAUDE.md` and its backup, and add the OMP-native mechanisms from the design spec: path-scoped rules, paper skills, slash commands, `WATCHDOG.md`, and the shared `writing-skills` plugin (built by `2026-06-24-shared-writing-skills-plugin.md`) installed at project scope.

**Architecture:** Source of truth is `AGENTS.md` (facts + quality bars, <100 lines); `CLAUDE.md` and `GEMINI.md` are symlinks (OMP collapses byte-identical context files). Procedures live in `.omp/skills/`; subtree-only guidance in `.omp/rules/` with `globs`; triggered actions in `.omp/commands/`; reviewer priorities in `WATCHDOG.md` (advisor-only, optional at runtime). The shared bibliography skills (`searching-literature`, `retrieving-paper-pdfs`, `formatting-bibtex-entries`) come from the `writing-skills` plugin, installed at **project scope** so they never load in non-writing repos.

**Tech Stack:** OMP (oh-my-pi) agent harness; LaTeX (`acmart`, `latexmk`, `chktex`, `texcount`); OMP marketplace/plugin system; Bash.

**Source spec:** `docs/plans/archive/2026-06-24-agent-instruction-files-normalization-design.md` (§8.2, §8.4, §9). This plan is scratch — do not commit the plan/spec docs; the paper-repo changes themselves are committed normally.

**Repo paths used below:**
- Paper repo: `/Users/joaichberger/Projects/test-generalization-paper` (`$PAPER`)
- Shared plugin repo: `/Users/joaichberger/Projects/omp-writing-skills` (`$WS`)

---

## Phase 0: Install the shared writing-skills plugin (prerequisite)

**Prerequisite:** the `omp-writing-skills` marketplace + `writing-skills` plugin must already exist
(built by `2026-06-24-shared-writing-skills-plugin.md`). This phase only installs it into the paper
repo at **project scope**, making the bibliography skills (`searching-literature`,
`retrieving-paper-pdfs`, `formatting-bibtex-entries`) available here while never loading in
non-writing repos.

### Task 0.1: Install the plugin at project scope

**Files:** `.omp/plugins/installed_plugins.json` (written by the install command).

- [ ] **Step 1: Register the marketplace (idempotent) and install**

Run from the paper repo root:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
omp plugin marketplace add /Users/joaichberger/Projects/omp-writing-skills 2>/dev/null || true
omp plugin install --scope project writing-skills@omp-writing-skills
```
Expected: plugin installed at project scope.

- [ ] **Step 2: Verify the project-scoped install**

Run:
```bash
test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/plugins/installed_plugins.json && \
grep -q writing-skills /Users/joaichberger/Projects/test-generalization-paper/.omp/plugins/installed_plugins.json && echo OK
```
Expected: `OK` (recorded in the project `.omp/`; non-writing repos never load it).

- [ ] **Step 3: Commit**

```bash
cd /Users/joaichberger/Projects/test-generalization-paper
git add .omp/plugins/installed_plugins.json && git commit -q -m "chore: install shared writing-skills plugin (project scope)" && echo committed
```
Expected: `committed`

---

## Phase 1: Instruction-file consolidation (the biggest win)

### Task 1.1: Write the consolidated `AGENTS.md`

**Files:**
- Modify (overwrite): `/Users/joaichberger/Projects/test-generalization-paper/AGENTS.md`

- [ ] **Step 1: Overwrite `AGENTS.md` with the consolidated, facts-only version**

Replace the entire file contents with:

````markdown
# Repository Guidelines — Teralizer Paper (ACM TOSEM)

LaTeX sources for "Teralizer: Semantics-Based Test Generalization from Conventional Unit Tests to
Property-Based Tests" (ACM `acmart`, TOSEM, options `[manuscript,screen,review]`).

## Structure
- `main.tex` — entry point; sections live in `sections/` (`\input{sections/<name>}`).
- `figures/`, `tables/` — assets (`\includegraphics{figures/<f>}`, `\input{tables/<f>}`); vector/PDF preferred.
- `data/` — CSV sources backing every number in the paper.
- `main.bib` → `main.bbl`. Tool/dataset macros (`\ToolTeralizer`, `\ToolSPF`, `\ToolJqwik`,
  `\DatasetEqBench`, `\DatasetCommonsDev`, …) are defined in `main.tex`.

## Commands
| Task | Command |
|---|---|
| Build PDF | `latexmk -pdf -interaction=nonstopmode -file-line-error main.tex` |
| Watch build | `latexmk -pdf -pvc main.tex` |
| Lint | `chktex main.tex` (or a single `sections/<f>.tex`) |
| Word count | `texcount -1 main.tex` |
| Clean aux | `latexmk -c` (`-C` also removes the PDF) |

CI runs `latexmk` on **both** remotes — GitLab (`.gitlab-ci.yml`, internal AAU) and GitHub Actions
(`.github/workflows`, public). They must hold the same state; both must be green.

## Writing rules (paper-specific)
- **Every number has a data source.** Take quantitative claims from `data/*.csv`; add a
  `% Data source: <file>.csv` comment next to the value. Never fabricate or estimate numbers.
- Use the **macros** (`\ToolTeralizer`, `\DatasetEqBench`, …), never raw tool/dataset names.
- **Non-breaking spaces (`~`)**: before citations (`word~\cite{x}`), in inline enumerations
  (`(i)~...`), and between a number and its unit (`3~hours`).
- Run `chktex` after editing a `.tex` file; confirm cross-references resolve (build twice if refs changed).
- Abstract: 200–250 words.
- Don't open a sentence/paragraph with a bare `Table X`/`Figure Y`; integrate the reference.

## Quality bars (self-check before yielding written prose)
- Every contribution claimed in the introduction maps to a results/evaluation subsection.
- A citation must support the *specific* claim it is attached to — not just the topic.
- No "proves"/"demonstrates" beyond what the data shows; numbers consistent across sections.

## Boundaries
- Never commit build artifacts (`*.aux`, `*.log`, `*.bbl`, `*.synctex.gz`, `*.out`, `*.fls`, `*.fdb_latexmk`).
  `main.pdf` only on releases, not routine commits.
- Use repo-relative paths under `figures/`/`tables/`; no absolute paths.

## Commits
Follow `skill://commit`. Short, imperative subject (e.g. "Rewrite sec:test-suite-reduction").
````

- [ ] **Step 2: Verify no stale references remain**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
grep -nE 'PAPER_SUMMARY|TECHNICAL_DOC|TODO\.md' AGENTS.md || echo "no stale refs"
```
Expected: `no stale refs`

- [ ] **Step 3: Verify length is within target**

Run:
```bash
wc -l /Users/joaichberger/Projects/test-generalization-paper/AGENTS.md
```
Expected: under 100 lines.

### Task 1.2: Delete the backup and replace `CLAUDE.md`/`GEMINI.md` with symlinks

**Files:**
- Delete: `/Users/joaichberger/Projects/test-generalization-paper/CLAUDE.md.backup`
- Replace with symlink: `/Users/joaichberger/Projects/test-generalization-paper/CLAUDE.md`
- Replace with symlink: `/Users/joaichberger/Projects/test-generalization-paper/GEMINI.md`

- [ ] **Step 1: Remove backup and the old divergent files, then symlink to `AGENTS.md`**

```bash
cd /Users/joaichberger/Projects/test-generalization-paper
git rm -q CLAUDE.md.backup CLAUDE.md GEMINI.md
ln -s AGENTS.md CLAUDE.md
ln -s AGENTS.md GEMINI.md
```

- [ ] **Step 2: Verify the symlinks resolve to `AGENTS.md`**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
readlink CLAUDE.md; readlink GEMINI.md; test -f CLAUDE.md && test -f GEMINI.md && echo "resolve OK"
```
Expected:
```
AGENTS.md
AGENTS.md
resolve OK
```

- [ ] **Step 3: Verify backup is gone**

Run: `test ! -e /Users/joaichberger/Projects/test-generalization-paper/CLAUDE.md.backup && echo "backup gone"`
Expected: `backup gone`

- [ ] **Step 4: Commit Phase 1**

```bash
cd /Users/joaichberger/Projects/test-generalization-paper
git add -A && git commit -q -m "docs: consolidate agent instructions into single AGENTS.md

Replace the 583-line CLAUDE.md and divergent GEMINI.md with symlinks to a
facts-only AGENTS.md; delete CLAUDE.md.backup; drop stale file references and
harness-duplicating scaffolding." && echo committed
```
Expected: `committed`

---

## Phase 2: Path-scoped rules

### Task 2.1: Prose rule scoped to section sources

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/rules/prose.md`

- [ ] **Step 1: Write the rule**

Create `.omp/rules/prose.md`:

```markdown
---
description: Academic prose conventions for the TOSEM paper sections
globs:
  - "sections/**/*.tex"
  - "main.tex"
---

# Paper prose conventions

- Active voice for contributions ("We show ..."); passive acceptable for methodology.
- Never "we believe/think" — use "evidence suggests" / "data indicates".
- Avoid "clearly"/"obviously" and marketing adjectives; let evidence speak.
- Every percentage/number must trace to `data/*.csv` with a `% Data source:` comment.
- Use macros for tools/datasets; non-breaking space before `\cite`.
- Structure results as Evidence → Mechanism → Implication.
```

- [ ] **Step 2: Verify**

Run: `test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/rules/prose.md && head -5 "$_"`
Expected: the frontmatter with `description:` and `globs:`.

### Task 2.2: Bibliography rule scoped to `main.bib`

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/rules/bib.md`

- [ ] **Step 1: Write the rule**

Create `.omp/rules/bib.md`:

```markdown
---
description: BibTeX conventions for main.bib (keys, titles, fields)
globs:
  - "main.bib"
---

# main.bib conventions

- Target file: `main.bib`.
- Keys: `author_year_keyword` (first-author surname lowercase, 4-digit year, one keyword); never
  rename an existing key without updating every `\cite{...}`.
- Format every entry per `skill://formatting-bibtex-entries` (title-casing, brace-protected
  acronyms, expanded venues, lowercase DOIs, en-dash pages, stripped DBLP cruft).
- To find/acquire a paper and its metadata, use `skill://searching-literature` (OpenAlex/Crossref
  lead) then `skill://retrieving-paper-pdfs`.
```

- [ ] **Step 2: Verify and commit Phase 2**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
test -f .omp/rules/prose.md && test -f .omp/rules/bib.md && \
git add .omp/rules && git commit -q -m "feat: add path-scoped prose and bib rules under .omp/rules" && echo committed
```
Expected: `committed`

---

## Phase 3: Paper skills, slash commands, and WATCHDOG.md

### Task 3.1: `latex-build-triage` skill

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/skills/latex-build-triage/SKILL.md`
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/skills/latex-build-triage/references/errors.md`

- [ ] **Step 1: Write the skill body**

Create `.omp/skills/latex-build-triage/SKILL.md`:

````markdown
---
name: latex-build-triage
description: Build the paper with latexmk and triage compile errors/warnings. Use when a LaTeX build fails, references/citations are undefined, or the user mentions latexmk/chktex/overfull boxes.
---

# LaTeX build triage

## Build
```bash
latexmk -pdf -interaction=nonstopmode -file-line-error main.tex
```
Run twice if cross-references or citations changed (latexmk usually handles this, but undefined
refs on a single pass clear on the second).

## Triage
1. Read `main.log`; match the first error against `references/errors.md`.
2. "Undefined references"/"Citation undefined" → rebuild once; if still undefined, the label/key is
   missing or misspelled — grep `sections/` and `main.bib`.
3. "Missing $ inserted" / "Runaway argument" → find the file:line in the log, fix the math/brace.
4. Overfull/underfull `\hbox` → report only (do not auto-rewrite prose) unless asked.
5. After fixing, rebuild and confirm zero "Undefined" and zero new errors.

See `references/errors.md` for the error catalogue.
````

- [ ] **Step 2: Write the error reference**

Create `.omp/skills/latex-build-triage/references/errors.md`:

```markdown
# Common LaTeX build errors

| Log message | Cause | Fix |
|---|---|---|
| `LaTeX Warning: There were undefined references.` | Label/cite defined later or missing | Rebuild once; if persists, grep the `\label`/cite key |
| `LaTeX Warning: Citation 'X' undefined` | Key not in `main.bib` or bib not rebuilt | Add/clean the entry (`skill://dblp-bib-hygiene`); rerun bibtex/latexmk |
| `! Missing $ inserted.` | Math char (`_`, `^`, `\alpha`) in text mode | Wrap in `$...$` or escape `\_` |
| `! Runaway argument?` | Unbalanced `{}` or missing `}` | Find the file:line; balance braces |
| `! Undefined control sequence.` | Unknown macro / missing package | Define the macro in `main.tex` or add `\usepackage` |
| `Overfull \hbox (NNpt too wide)` | Line too long / unbreakable | Rephrase, add `\-`, or accept if small; report only |
| `! File 'X' not found.` | Wrong `\input`/`\includegraphics` path | Use repo-relative path under `sections/`/`figures/` |
```

- [ ] **Step 3: Verify**

Run: `test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/skills/latex-build-triage/SKILL.md && test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/skills/latex-build-triage/references/errors.md && echo OK`
Expected: `OK`

### Task 3.2: `acmart-conventions` skill

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/skills/acmart-conventions/SKILL.md`

- [ ] **Step 1: Write the skill**

Create `.omp/skills/acmart-conventions/SKILL.md`:

````markdown
---
name: acmart-conventions
description: ACM acmart/TOSEM formatting conventions. Use when setting document class options, toggling review vs camera-ready, adding CCS concepts, the ACM Reference Format, or the copyright block.
---

# acmart / TOSEM conventions

- Document class: `\documentclass[manuscript,screen,review]{acmart}` for submission/review.
  For camera-ready, drop `review` and set the journal/volume/copyright per ACM instructions.
- Anonymous review: `acmart` `[anonymous,review]` strips author/affiliation; keep author macros
  intact and let the option control visibility — do not delete author blocks manually.
- CCS concepts: include `\begin{CCSXML}...\end{CCSXML}` + `\ccsdesc` blocks generated from the
  ACM CCS tool.
- ACM Reference Format and copyright block are emitted by the class — do not hand-format them.
- Use numbered citation style (ACM default).
````

- [ ] **Step 2: Verify**

Run: `test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/skills/acmart-conventions/SKILL.md && echo OK`
Expected: `OK`

### Task 3.3: Slash commands

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/commands/build-paper.md`
- Create: `/Users/joaichberger/Projects/test-generalization-paper/.omp/commands/add-citation.md`

- [ ] **Step 1: Write `/build-paper`**

Create `.omp/commands/build-paper.md`:

```markdown
---
description: Build the paper with latexmk and report only actionable diagnostics
---

Build the paper:

Run `latexmk -pdf -interaction=nonstopmode -file-line-error main.tex` (rerun once if cross-refs
changed). Then summarize from `main.log`: undefined references, multiply-defined labels, undefined
citations, and overfull `\hbox` over 10pt. Do NOT edit sources — report the actionable diagnostics
only. If a build error appears, follow `skill://latex-build-triage`.
```

- [ ] **Step 2: Write `/add-citation`**

Create `.omp/commands/add-citation.md`:

```markdown
---
description: Fetch a BibTeX entry from DBLP, clean it, and append to main.bib
---

Add a citation for: $ARGUMENTS

Follow `skill://dblp-bib-hygiene`: look the work up on DBLP, fetch its BibTeX, clean it (key
`author_year_keyword`; strip timestamp/biburl/bibsource; expand venue; lowercase DOI; brace-protect
title), and append it to `main.bib` only if no entry with the same DOI/title already exists.
```

- [ ] **Step 3: Verify**

Run: `test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/commands/build-paper.md && test -f /Users/joaichberger/Projects/test-generalization-paper/.omp/commands/add-citation.md && echo OK`
Expected: `OK`

### Task 3.4: `WATCHDOG.md` (advisor-only review priorities)

**Files:**
- Create: `/Users/joaichberger/Projects/test-generalization-paper/WATCHDOG.md`

- [ ] **Step 1: Write the watchdog file**

Create `WATCHDOG.md` (restates the 2–3 most-violated bars for independent re-checking + review-only emphasis; the proactive bars also live in `AGENTS.md`):

```markdown
# Watchdog notes — TOSEM paper review priorities

Especially watch for:

- **Overclaiming:** "proves"/"demonstrates"/"significant" beyond what the data in `data/*.csv`
  supports. Effects are modest (1–4 pp); flag any inflated framing.
- **Citation–claim alignment:** a `\cite` must support the *specific* claim it is attached to, not
  just the surrounding topic. Flag misattributions.
- **Number consistency:** the same metric must read identically across sections, tables, and the
  abstract. Flag any mismatch with file:line.
- **Contribution coverage:** every contribution claimed in the introduction must have a
  corresponding results/evaluation subsection.
- **Unsupported quantitative claims:** any percentage without a `% Data source:` comment.
```

- [ ] **Step 2: Verify and commit Phase 3**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
test -f WATCHDOG.md && \
git add .omp/skills .omp/commands WATCHDOG.md && \
git commit -q -m "feat: add latex/acmart skills, build/citation commands, and WATCHDOG.md" && echo committed
```
Expected: `committed`

---

## Phase 4: Verification

### Task 4.1: Confirm the consolidated context surface

- [ ] **Step 1: Verify single canonical file + symlinks + structure**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-paper
echo "--- context files ---"; ls -l AGENTS.md CLAUDE.md GEMINI.md
echo "--- none ignored ---"; git check-ignore AGENTS.md CLAUDE.md GEMINI.md || echo "none ignored"
echo "--- no backup ---"; test ! -e CLAUDE.md.backup && echo ok
echo "--- omp tree ---"; find .omp -type f | sort
echo "--- no stale refs in any instruction file ---"; grep -rnE 'PAPER_SUMMARY|TECHNICAL_DOC|TODO\.md' AGENTS.md .omp 2>/dev/null || echo "clean"
```
Expected: `CLAUDE.md`/`GEMINI.md` are symlinks → `AGENTS.md`; `ok`; the `.omp/{rules,skills,commands}` files listed; `clean`.

- [ ] **Step 2 (optional smoke build — only if a TeX toolchain is present):**

Run: `cd /Users/joaichberger/Projects/test-generalization-paper && chktex -q main.tex | head -5`
Expected: chktex runs (warnings are fine; this only confirms the toolchain + file are intact). Skip if `chktex` is unavailable; do not run a full `latexmk` build just to verify instructions.

- [ ] **Step 3: Confirm git state is clean**

Run: `cd /Users/joaichberger/Projects/test-generalization-paper && git status --short`
Expected: empty (all changes committed across Phases 0–3).

---

## Self-Review (completed by plan author)

- **Spec coverage (§8.2/§8.4/§9):** consolidated `AGENTS.md` + symlinks + backup deletion (Task 1.1–1.2); salvaged the 6 paper rules into `AGENTS.md`; deleted ceremony; fixed stale refs (Task 1.1 Step 2). Path-scoped prose + bib rules (Phase 2). `latex-build-triage` + `acmart-conventions` skills (Phase 3). `/build-paper` + `/add-citation` commands (Phase 3). `WATCHDOG.md` with restated bars (Task 3.4). Shared `dblp-bib-hygiene` via project-scoped plugin (Phase 0). Quality bars kept in-context in `AGENTS.md` (§9). Covered.
- **Out of scope of this plan (separate plans):** dev repo and thesis repo (the thesis reuses the Phase 0 plugin via `omp plugin install`); Zotero MCP (optional, deferred).
- **Placeholder scan:** none — every file has complete content; `$ARGUMENTS` is the real OMP command-arg token, not a placeholder.
- **Consistency:** skill names (`dblp-bib-hygiene`, `latex-build-triage`, `acmart-conventions`) referenced identically in rules/commands and defined in the skill tasks; marketplace/plugin name `omp-writing-skills`/`writing-skills` consistent across Task 0.1 and 0.4.
- **Caveat:** OMP discovery of the new `.omp/...` files and the project-scoped plugin applies on the **next session start**; an in-flight session won't see them until reload/restart.
```
