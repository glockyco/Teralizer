---
title: "Thesis Repo Agent Setup Implementation Plan"
type: plan
status: implemented
created: 2026-06-24
parent: 2026-06-24-agent-instruction-files-normalization-design
archived: 2026-06-25
---

# Thesis Repo Agent Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan **inline** (batch execution with review checkpoints) — **no subagents, no worktrees**. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring `phd-thesis` to full parity with the consistent layout and add the missing mechanisms: a `GEMINI.md` symlink, the "Contribution Framing" essay relocated to a path-scoped rule (shrinking root `AGENTS.md` under 200 lines), a `WATCHDOG.md`, and the shared `dblp-bib-hygiene` skill via the project-scoped plugin.

**Architecture:** The thesis is already the model — tracked `AGENTS.md` source, `CLAUDE.md` symlink, nested `bibliography/`/`papers/` `AGENTS.md`, `.omp/skills/`, pre-commit + CI. This plan only closes gaps: add `GEMINI.md` symlink (consistency), move the always-on contribution-framing essay into `.omp/rules/` scoped to `chapters/**`+`frontmatter/**` (loads when writing those, not every session), ship `WATCHDOG.md`, and install the shared writing-skills plugin.

**Tech Stack:** OMP harness; LuaLaTeX + biber (`./scripts/thesis-build`); pre-commit + GitHub Actions; the `omp-writing-skills` marketplace/plugin.

**Source spec:** `docs/plans/archive/2026-06-24-agent-instruction-files-normalization-design.md` (§8.3, §8.4, §9; consistency principle §3/§4). Scratch plan — do not commit the plan/spec docs; thesis-repo changes are committed normally (follow `skill://commit`; the thesis uses the `commit`/`commit-guidelines` skills).

**Prerequisite:** the `omp-writing-skills` marketplace must exist (created by the **paper plan, Phase 0, Tasks 0.1–0.3**). Phase 3 below installs from it; if running the thesis before the paper, create that marketplace first per the paper plan.

**Repo path:** `/Users/joaichberger/Projects/phd-thesis` (`$THESIS`).

---

## Phase 1: File parity + size trim

### Task 1.1: Add the `GEMINI.md` symlink (consistency)

**Files:**
- Create: `GEMINI.md` (tracked symlink → `AGENTS.md`)

- [ ] **Step 1: Confirm current state**

Run:
```bash
cd /Users/joaichberger/Projects/phd-thesis
readlink CLAUDE.md; test -e GEMINI.md && echo "GEMINI exists" || echo "no GEMINI"
```
Expected: `AGENTS.md`; `no GEMINI`.

- [ ] **Step 2: Add the symlink (only if absent)**

```bash
cd /Users/joaichberger/Projects/phd-thesis
ln -s AGENTS.md GEMINI.md
```

- [ ] **Step 3: Verify it resolves and is not ignored**

Run:
```bash
cd /Users/joaichberger/Projects/phd-thesis
readlink GEMINI.md; test -f GEMINI.md && echo "resolves"; git check-ignore GEMINI.md || echo "tracked-eligible"
```
Expected:
```
AGENTS.md
resolves
tracked-eligible
```

### Task 1.2: Relocate the "Contribution Framing" essay to a path-scoped rule

**Files:**
- Create: `.omp/rules/contribution-framing.md`
- Modify: `AGENTS.md` (remove the `## Contribution Framing (precise)` section, ~lines 22–101; replace with a one-line pointer)

- [ ] **Step 1: Read the current section boundaries**

Run:
```bash
cd /Users/joaichberger/Projects/phd-thesis
grep -nE '^## ' AGENTS.md | sed -n '1,8p'
```
Expected: shows `## Contribution Framing (precise)` and the next heading `## Thesis Structure` — the section to move is between them.

- [ ] **Step 2: Create the rule with the framing content moved verbatim**

Create `.omp/rules/contribution-framing.md`. Write the frontmatter below, then **paste the entire
`## Contribution Framing (precise)` section body from `AGENTS.md` verbatim** beneath it (the four
PASDA/Teralizer/applicability/synthesis bullets — read them from `AGENTS.md` as they currently
stand; do not paraphrase):

```markdown
---
description: Precise per-contribution framing and claim boundaries for the thesis (PASDA, Teralizer, applicability, synthesis)
globs:
  - "chapters/**/*.tex"
  - "frontmatter/**/*.tex"
---

# Contribution framing (precise)

<!-- PASTE the verbatim "## Contribution Framing (precise)" section body from AGENTS.md here -->
```

- [ ] **Step 3: Replace the section in `AGENTS.md` with a pointer**

In `AGENTS.md`, delete the `## Contribution Framing (precise)` heading and its body, and put in its place:

```markdown
## Contribution Framing

The precise per-contribution claim boundaries live in `.omp/rules/contribution-framing.md`
(auto-loaded when editing `chapters/**` or `frontmatter/**`). Read `rule://contribution-framing`
before drafting or revising contribution claims.
```

- [ ] **Step 4: Verify the move (no content lost, root file shrunk)**

Run:
```bash
cd /Users/joaichberger/Projects/phd-thesis
echo "rule has the framing:"; grep -c 'PASDA' .omp/rules/contribution-framing.md
echo "AGENTS.md no longer holds the essay:"; grep -c 'maybe-eq' AGENTS.md
echo "AGENTS.md length:"; wc -l AGENTS.md
```
Expected: the rule contains the PASDA framing (count ≥ 1); `AGENTS.md` no longer contains the moved detail (e.g. `maybe-eq` count `0`); `AGENTS.md` now well under 200 lines.

- [ ] **Step 5: Commit Phase 1**

```bash
cd /Users/joaichberger/Projects/phd-thesis
git add AGENTS.md GEMINI.md .omp/rules/contribution-framing.md
git commit -q -m "docs: add GEMINI.md symlink; move contribution framing to a path-scoped rule

Add GEMINI.md -> AGENTS.md for parity with the other repos. Relocate the
~80-line precise contribution-framing essay into .omp/rules/contribution-framing.md
(scoped to chapters/** and frontmatter/**) so it loads only when writing those,
bringing root AGENTS.md back under 200 lines." && echo committed
```
Expected: `committed`

---

## Phase 2: WATCHDOG.md

### Task 2.1: Author the thesis review priorities

**Files:**
- Create: `WATCHDOG.md`

- [ ] **Step 1: Write the watchdog file (review-only; the proactive bars stay in `AGENTS.md`/rule)**

Create `WATCHDOG.md`:

```markdown
# Watchdog notes — thesis review priorities

Especially watch for (review priorities; do not relax the in-context rules):

- **Overclaiming:** any "proves"/"shows"/"demonstrates" beyond what the evaluation supports;
  claims about formal guarantees not backed by results. Push back with evidence.
- **Citation–claim alignment:** every `\cite` must support the *specific* claim it is attached to,
  not just the topic; an author's other paper is not interchangeable. Flag misattributions.
- **Cross-chapter consistency:** terminology, notation, and numbers must match across chapters and
  the abstract; the thesis must read as one work, not stitched papers.
- **Specific framing traps (from `rule://contribution-framing`):**
  - PASDA RQ1 gaps are 2.8–6.4 pp — avoid the rounded "3–7 pp"; do not claim prior tools "discard"
    partial proofs.
  - Teralizer runtime: present BOTH directions (≈32% lower runtime at matched mutation score AND the
    extended Pareto frontier), not the one-sided "lower runtime".
  - Applicability: do not blame all barriers on solver-oriented tooling; do not claim generalization
    has no inherent limit.
  - Guarantees–applicability: phrase as constraining/shaping applicability, never deterministic
    ("fixes"/"determines where the analysis applies").
- **Specificity:** flag smooth prose that says nothing concrete a reader couldn't guess from a title.
```

- [ ] **Step 2: Verify and commit**

```bash
cd /Users/joaichberger/Projects/phd-thesis
test -f WATCHDOG.md && git add WATCHDOG.md && git commit -q -m "feat: add WATCHDOG.md with thesis review priorities" && echo committed
```
Expected: `committed`

---

## Phase 3: Migrate bibliography skills to the shared plugin

The thesis is the *origin* of `searching-literature` + `retrieving-paper-pdfs`. Now that the shared
`writing-skills` plugin (built by `2026-06-24-shared-writing-skills-plugin.md`) carries the
generalized versions, the thesis consumes them from the plugin and **drops its local copies** —
single source of truth, no duplication. `bibliography/AGENTS.md` and `papers/AGENTS.md` stay
(thesis-specific registration/storage); their shared cleanup rules move to
`skill://formatting-bibtex-entries`.

**Prerequisite:** the `omp-writing-skills` marketplace + plugin exist (shared-plugin plan).

### Task 3.1: Install the plugin and drop the now-duplicated local skills

**Files:**
- Remove: `.omp/skills/searching-literature/`, `.omp/skills/retrieving-paper-pdfs/`
- Create: `.omp/plugins/installed_plugins.json` (via install)
- Keep: `.omp/skills/{commit-guidelines,writing-chapter-prose}` (thesis-specific; not shared)

- [ ] **Step 1: Install the plugin at project scope**

```bash
cd /Users/joaichberger/Projects/phd-thesis
omp plugin marketplace add /Users/joaichberger/Projects/omp-writing-skills 2>/dev/null || true
omp plugin install --scope project writing-skills@omp-writing-skills
test -f .omp/plugins/installed_plugins.json && grep -q writing-skills .omp/plugins/installed_plugins.json && echo OK
```
Expected: `OK`.

- [ ] **Step 2: Remove the local copies (now provided by the plugin)**

```bash
cd /Users/joaichberger/Projects/phd-thesis
git rm -r -q .omp/skills/searching-literature .omp/skills/retrieving-paper-pdfs 2>/dev/null || rm -rf .omp/skills/searching-literature .omp/skills/retrieving-paper-pdfs
ls .omp/skills
```
Expected: only `commit-guidelines` and `writing-chapter-prose` remain.

### Task 3.2: Smoke-test the migrated fetch skill against the thesis layout

- [ ] **Step 1: Fetch a known preprint via the plugin script**

This proves the generalized skill works post-migration: `skill://` resolves to the plugin-bundled
script, and the chain writes a valid PDF to the thesis `papers/` store.
```bash
cd /Users/joaichberger/Projects/phd-thesis
uv run --no-project python skill://retrieving-paper-pdfs/scripts/fetch_pdf.py teralizer_smoke --arxiv 2512.14475 --out papers/_smoke_test.pdf
head -c5 papers/_smoke_test.pdf | grep -q '%PDF' && echo "fetch OK (%PDF)" || echo "CHECK fetch"
rm -f papers/_smoke_test.pdf
```
Expected: `OK teralizer_smoke: ... from arxiv ... -> papers/_smoke_test.pdf` then `fetch OK (%PDF)`.
(If `skill://` does not resolve in argument position on this OMP build, resolve the path first — see
the shared-plugin plan caveat. This is the one network-touching check; it validates the migration.)

### Task 3.3: Align `bibliography/AGENTS.md` with the shared conventions

**Files:**
- Modify: `bibliography/AGENTS.md`

- [ ] **Step 1: Lead with OpenAlex/Crossref; point cleanup at the shared skill**

In `bibliography/AGENTS.md`:
- In "Adding a New Entry", change step 1 ("Obtain the entry from dblp.org where possible") to:
  "Obtain metadata from OpenAlex or Crossref BibTeX (`api.crossref.org/works/<DOI>/transform/application/x-bibtex`); DBLP is a fallback. Acquire the PDF with `skill://retrieving-paper-pdfs`."
- Replace the "Entry Cleanup Rules (DBLP source)" and "Example: Before and After" sections with a
  single pointer: "Format every entry per `skill://formatting-bibtex-entries` (key style,
  title-casing, venue expansion, DOI/pages, DBLP-cruft stripping)."
- Keep "Key Convention", "Section Organization" (the thesis topic table), and the file-purpose note.

- [ ] **Step 2: Verify and commit Phase 3**

```bash
cd /Users/joaichberger/Projects/phd-thesis
grep -q 'formatting-bibtex-entries' bibliography/AGENTS.md && ! grep -qi 'obtain the entry from .*dblp' bibliography/AGENTS.md && echo aligned
git add .omp bibliography/AGENTS.md && git commit -q -m "refactor: consume shared writing-skills plugin; drop local bib skills

Install writing-skills (project scope) and remove the now-duplicated local
searching-literature/retrieving-paper-pdfs copies. Point bibliography/AGENTS.md
cleanup at skill://formatting-bibtex-entries; lead metadata with OpenAlex/Crossref." && echo committed
```
Expected: `aligned`; `committed`.

---

## Phase 4: Verification

### Task 4.1: Confirm parity and discovery

- [ ] **Step 1: Verify the three-file layout, rule, watchdog, and plugin**

Run:
```bash
cd /Users/joaichberger/Projects/phd-thesis
echo "--- context files (all symlinks tracked) ---"; ls -l AGENTS.md CLAUDE.md GEMINI.md
echo "--- none ignored ---"; git check-ignore AGENTS.md CLAUDE.md GEMINI.md || echo "none ignored"
echo "--- AGENTS.md size ---"; wc -l AGENTS.md
echo "--- new files ---"; ls .omp/rules/contribution-framing.md WATCHDOG.md .omp/plugins/installed_plugins.json
echo "--- git clean ---"; git status --short
```
Expected: `CLAUDE.md` and `GEMINI.md` → `AGENTS.md` symlinks; `none ignored`; `AGENTS.md` under 200 lines; all new files present; empty git status.

- [ ] **Step 2: Build sanity (thesis already has a working toolchain)**

Run: `cd /Users/joaichberger/Projects/phd-thesis && ./scripts/thesis-build draft`
Expected: exit `0` (a fast draft build; `75` = a concurrent build held the lock, retry). This confirms the AGENTS.md/rule edits did not touch buildable sources — they didn't, but it's a cheap guard. Skip if a build is already running.

- [ ] **Step 3: Live discovery check (next OMP session)**

In a fresh OMP session in the thesis repo: confirm `rule://contribution-framing` resolves, the
`searching-literature` / `retrieving-paper-pdfs` / `formatting-bibtex-entries` skills appear (from
the plugin), and (if you enable it) the advisor picks up `WATCHDOG.md`.

---

## Self-Review (completed by plan author)

- **Spec §8.3/§8.4/§9 + consistency principle (§3/§4) coverage:** `GEMINI.md` symlink for parity (Task 1.1); contribution-framing essay → path-scoped rule, root `AGENTS.md` < 200 lines (Task 1.2); `WATCHDOG.md` with review priorities incl. the specific framing traps (Phase 2); shared bibliography skills consumed from the `writing-skills` plugin, local copies dropped, and `bibliography/AGENTS.md` aligned (Phase 3). Covered.
- **Placeholder scan:** the only "paste verbatim" is a *move* of existing `AGENTS.md` content (Task 1.2 Step 2) — the content already exists and is read at execution; this is a relocation, not an unwritten placeholder. The bib-skill removal/alignment operates on known existing files. All other files have complete content.
- **Consistency:** three-file tracked layout matches the dev and paper plans; `rule://contribution-framing` used in both the `AGENTS.md` pointer (Task 1.2) and `WATCHDOG.md`; plugin id `writing-skills@omp-writing-skills` matches the shared-plugin and paper plans; the dropped skills are exactly the ones the shared-plugin plan generalized.
- **Dependency:** Phase 3 requires the `omp-writing-skills` plugin from `2026-06-24-shared-writing-skills-plugin.md` (run that first). The `marketplace add` is idempotent.
- **Caveat:** `bibliography/AGENTS.md` intentionally retained (thesis-specific section taxonomy); OMP discovers the new rule/plugin on next session start.
```
