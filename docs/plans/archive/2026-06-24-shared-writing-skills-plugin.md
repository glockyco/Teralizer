---
title: "Shared writing-skills Plugin Implementation Plan"
type: plan
status: implemented
created: 2026-06-24
parent: 2026-06-24-agent-instruction-files-normalization-design
archived: 2026-06-25
---

# Shared writing-skills Plugin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan **inline** (batch execution with review checkpoints) — **no subagents, no worktrees**. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the single, canonical home for the writing repos' bibliography toolkit — a local OMP marketplace (`~/Projects/omp-writing-skills`) shipping a `writing-skills` plugin with three skills migrated/generalized from the thesis's proven setup: `searching-literature`, `retrieving-paper-pdfs` (+ `fetch_pdf.py`), and `formatting-bibtex-entries`. The paper and thesis (and future paper projects) install this plugin at project scope; the thesis drops its now-duplicated local copies.

**Architecture:** The thesis's `searching-literature` + `retrieving-paper-pdfs` skills are already correct and reliable (OpenAlex/Crossref-primary search; OA→Sci-Hub→repository→arXiv PDF fallback chain with a `%PDF`-validating `fetch_pdf.py`). This plan moves them into a shared plugin **generalized** so nothing is repo-specific: the bundled script is invoked via `skill://retrieving-paper-pdfs/scripts/fetch_pdf.py` (auto-resolves to a filesystem path in bash regardless of install location), the output path is passed with the existing `--out` flag, `UNPAYWALL_EMAIL` is env-overridable, and SKILL text refers to "the repo's bib file / PDF policy" instead of hardcoded `papers/`/`bibliography/references.bib`. A new `formatting-bibtex-entries` skill holds the shared cleanup conventions so paper and thesis don't duplicate them.

**Tech Stack:** OMP marketplace/plugin system; stdlib Python 3 (the fetch script); BibTeX.

**Source spec:** `docs/plans/archive/2026-06-24-agent-instruction-files-normalization-design.md` (§8.4). Scratch plan — do not commit the plan/spec docs; the plugin repo IS committed (it's a real new repo).

**Source of truth being generalized:** `/Users/joaichberger/Projects/phd-thesis/.omp/skills/{searching-literature,retrieving-paper-pdfs}/` and `/Users/joaichberger/Projects/phd-thesis/bibliography/AGENTS.md`.

**Repo path:** `/Users/joaichberger/Projects/omp-writing-skills` (`$WS`).

**Sequencing:** This plan runs **first** (paper Phase 0 and thesis Phase 3 install from it). It does not modify the thesis or paper repos — the thesis-local skill removal happens in the thesis plan.

---

## Phase 1: Scaffold the marketplace + plugin

### Task 1.1: Create directories + catalog + manifest

**Files:**
- Create: `$WS/.omp-plugin/marketplace.json`
- Create: `$WS/plugins/writing-skills/.claude-plugin/plugin.json`

- [ ] **Step 1: Make directories**

```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
mkdir -p "$WS/.omp-plugin" \
         "$WS/plugins/writing-skills/.claude-plugin" \
         "$WS/plugins/writing-skills/skills/searching-literature" \
         "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/scripts" \
         "$WS/plugins/writing-skills/skills/formatting-bibtex-entries"
```

- [ ] **Step 2: Write `$WS/.omp-plugin/marketplace.json`**

```json
{
  "$schema": "https://anthropic.com/claude-code/marketplace.schema.json",
  "name": "omp-writing-skills",
  "owner": { "name": "Johann Glock" },
  "metadata": {
    "description": "Skills shared across academic writing repos (papers, thesis)",
    "version": "0.1.0",
    "pluginRoot": "plugins"
  },
  "plugins": [
    {
      "name": "writing-skills",
      "description": "Bibliography toolkit for LaTeX writing repos: literature search, PDF acquisition, and BibTeX formatting",
      "source": "./writing-skills",
      "category": "productivity"
    }
  ]
}
```

- [ ] **Step 3: Write `$WS/plugins/writing-skills/.claude-plugin/plugin.json`**

```json
{
  "name": "writing-skills",
  "version": "0.1.0",
  "description": "Bibliography toolkit for LaTeX writing repos: literature search, PDF acquisition, and BibTeX formatting"
}
```

- [ ] **Step 4: Verify JSON**

Run:
```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
uv run --no-project python -m json.tool "$WS/.omp-plugin/marketplace.json" >/dev/null && \
uv run --no-project python -m json.tool "$WS/plugins/writing-skills/.claude-plugin/plugin.json" >/dev/null && echo OK
```
Expected: `OK`

---

## Phase 2: Migrate `retrieving-paper-pdfs` (generalized)

### Task 2.1: Copy the fetch script and make the email env-overridable

**Files:**
- Create: `$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/scripts/fetch_pdf.py` (copied from the thesis)

- [ ] **Step 1: Copy the proven script verbatim**

```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
cp /Users/joaichberger/Projects/phd-thesis/.omp/skills/retrieving-paper-pdfs/scripts/fetch_pdf.py \
   "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/scripts/fetch_pdf.py"
```

- [ ] **Step 2: Make `UNPAYWALL_EMAIL` env-overridable**

In the copied `fetch_pdf.py`, change the constant line:
```python
UNPAYWALL_EMAIL = "thesis-bib@users.noreply.github.com"
```
to:
```python
import os
UNPAYWALL_EMAIL = os.environ.get("UNPAYWALL_EMAIL", "writing-bib@users.noreply.github.com")
```
(Place the `import os` with the other imports near the top if not already present; keep it stdlib-only.)

- [ ] **Step 3: Verify the script still runs and parses args**

Run:
```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
uv run --no-project python "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/scripts/fetch_pdf.py" --help >/dev/null && echo "runs"
uv run --no-project python -c "import ast,sys; ast.parse(open(sys.argv[1]).read()); print('valid python')" \
   "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/scripts/fetch_pdf.py"
```
Expected: `runs`; `valid python`.

### Task 2.2: Write the generalized `retrieving-paper-pdfs` SKILL.md

**Files:**
- Create: `$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/SKILL.md`

- [ ] **Step 1: Write the SKILL.md (thesis content, repo-agnostic)**

````markdown
---
name: retrieving-paper-pdfs
description: Use when acquiring a paper's full-text PDF for a writing repo — the acquire step of the searching-literature workflow, before characterizing or citing a paper, or whenever a DOI or arXiv id is in hand and the PDF is not yet stored. Covers open-access, Sci-Hub, and arXiv retrieval, and why a plain curl of a Sci-Hub URL saves the wrong file.
---

# Retrieving Paper PDFs

## Overview

Goal: get the best canonical full-text PDF into the repo's PDF store (if it has one), then read it
before writing any characterization of it (the EVIDENCE rule). The trap: Sci-Hub and many publisher
pages return an HTML *viewer*, not the PDF. The real PDF URL lives in the page's
`<meta name="citation_pdf_url">` tag. Fetch that, and always validate that the first five bytes are
`%PDF-` before saving.

> **Never fabricate metadata.** Title, authors, venue, year, pages, and DOI for the bibliography
> entry MUST come from an authoritative source — OpenAlex, Crossref
> (`api.crossref.org/works/<DOI>/transform/application/x-bibtex`), or DBLP — and be verified against
> the paper. Reconstructing metadata from memory, a search snippet, or the PDF's own header is
> prohibited: a wrong year, venue, or normalized author name corrupts the citation graph and the
> bibliography.

## Source order

Prefer the most canonical copy that is actually retrievable. The published version beats a
repository preprint, so Sci-Hub comes before green OA.

| # | Source | What it gives | How |
|---|--------|---------------|-----|
| 1 | Open access, publisher | Official published PDF at the DOI (gold/hybrid OA) | Unpaywall `oa_locations` with `host_type=publisher` and a `url_for_pdf` |
| 2 | Sci-Hub | Published version for paywalled articles | `citation_pdf_url` from `sci-hub.st/<DOI>` (mirrors below) |
| 3 | Open access, repository | Author's accepted/submitted manuscript | Unpaywall `best_oa_location.url_for_pdf` |
| 4 | arXiv | Genuine preprints, or last resort | `arxiv.org/pdf/<id>` |
| 5 | Manual | Author copy / institutional library | not automated — flag it |

Mirrors: `sci-hub.st`, `sci-hub.box`, `sci-hub.ru` work; `sci-hub.se` is unreliable. arXiv is **not**
a substitute for a published version — use it only for true preprints or when 1–3 fail.

## Quick start

```bash
# The skill:// path auto-resolves to the bundled script wherever this plugin is installed.
uv run --no-project python skill://retrieving-paper-pdfs/scripts/fetch_pdf.py <key> --doi <DOI> [--arxiv <id>] --out <store>/<key>.pdf
```

- `--out` sets the destination. Use the repo's PDF store if it has one (see the repo's `AGENTS.md` /
  bib rule — e.g. the thesis stores gitignored copies under `papers/<key>.pdf`); for a repo that
  does not store PDFs, point `--out` at a scratch path you read and discard.
- The script runs the full source order, validates the `%PDF` header on every candidate, and prints
  `OK <key>: <size> from <source> -> <out>` or a `FAIL` with the next step.
- It is stdlib-only Python 3 (no install). Set `UNPAYWALL_EMAIL` in your environment to your address
  (Unpaywall requires a contact email; a default is used otherwise).

## Workflow

```
- [ ] 1. Verify the DOI (OpenAlex) — a wrong DOI fetches the wrong paper
- [ ] 2. Run fetch_pdf.py with --doi (add --arxiv only for a genuine preprint) and --out
- [ ] 3. Confirm OK + the saved file starts with %PDF + nonzero size
- [ ] 4. On FAIL: find an OA author copy or use institutional access; save it manually, or flag
- [ ] 5. Read the paper before characterizing it
- [ ] 6. Register it: pull metadata from an authoritative source (never invent it) and format the
         entry per skill://formatting-bibtex-entries, into the repo's bib file (see the repo's AGENTS.md)
```

## Why a plain curl fails

`curl -o x.pdf https://sci-hub.st/<DOI>` saves the HTML viewer, not the PDF. You must extract
`citation_pdf_url` from that HTML and download it, then check the header. A file that starts with
`<!DOCTYPE` or `<html` is a saved web page — delete it and extract the real URL.

## Failure modes

| Symptom | Cause / fix |
|---------|-------------|
| Saved file is HTML, not a PDF | Downloaded the viewer page. Extract `citation_pdf_url`; validate `%PDF-`. |
| Sci-Hub returns only metadata (no `citation_pdf_url`) | Mirror lacks the file (common for post-~2023 IEEE/ACM/Springer). Try OA (Unpaywall) or an author copy. |
| Unpaywall says OA but download fails | The OA location is a landing page, not a direct PDF. Open it and find the PDF link, or use Sci-Hub. |
| Direct publisher fetch returns 403 | Publishers (ACM/IEEE) block scripted fetches. Use OA/Sci-Hub. The `read` tool can sometimes fetch bytes even when a plain fetch 403s. |
| `read` cannot text-extract a fetched PDF | The URL lacked a `.pdf` extension. Save the bytes locally as `.pdf` first, then read the local file. |
| All mirrors dead | Update `SCIHUB_MIRRORS` in the script (and any repo mirror note). |

## After acquiring

1. **Read it** before characterizing its mechanism/findings/limitations.
2. **Register it**: metadata from an authoritative source (OpenAlex / Crossref BibTeX / DBLP),
   verified against the paper; format per `skill://formatting-bibtex-entries`; add to the repo's bib
   file and store the PDF per the repo's policy.

## Scope

Repos that cite paywalled work may sanction Sci-Hub for the author's local reading copies (PDFs
gitignored). Prefer open access and author copies when available. This is the acquire step of the
`searching-literature` workflow.
````

- [ ] **Step 2: Verify**

Run: `WS=/Users/joaichberger/Projects/omp-writing-skills; test -f "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/SKILL.md" && grep -q 'skill://retrieving-paper-pdfs/scripts/fetch_pdf.py' "$WS/plugins/writing-skills/skills/retrieving-paper-pdfs/SKILL.md" && echo OK`
Expected: `OK`

---

## Phase 3: Migrate `searching-literature` (generalized)

### Task 3.1: Write the generalized `searching-literature` SKILL.md

**Files:**
- Create: `$WS/plugins/writing-skills/skills/searching-literature/SKILL.md`

- [ ] **Step 1: Copy the thesis SKILL.md as the base**

```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
cp /Users/joaichberger/Projects/phd-thesis/.omp/skills/searching-literature/SKILL.md \
   "$WS/plugins/writing-skills/skills/searching-literature/SKILL.md"
```

- [ ] **Step 2: Generalize the repo-specific references**

In the copied SKILL.md, make these edits (the API/search content is already generic — only the
hand-off references are thesis-specific):

- The "search → acquire → register" line currently ends `register (\`bibliography/AGENTS.md\`)`.
  Replace with: `register (format per \`skill://formatting-bibtex-entries\`, into the repo's bib file)`.
- In **## Acquire and register**, replace the bullet:
  > - **Register** in `bibliography/references.bib` per `bibliography/AGENTS.md`, with metadata from Crossref/OpenAlex/DBLP, and read the paper before characterizing it.

  with:
  > - **Register** in the repo's bibliography file (see the repo's `AGENTS.md` / bib rule), formatting the entry per `skill://formatting-bibtex-entries`, with metadata from OpenAlex/Crossref/DBLP, and read the paper before characterizing it.
- Leave the OpenAlex/DBLP/Crossref API block, DOI verification, and screening sections unchanged.

- [ ] **Step 3: Verify**

Run: `WS=/Users/joaichberger/Projects/omp-writing-skills; grep -q 'formatting-bibtex-entries' "$WS/plugins/writing-skills/skills/searching-literature/SKILL.md" && ! grep -q 'bibliography/references.bib' "$WS/plugins/writing-skills/skills/searching-literature/SKILL.md" && echo OK`
Expected: `OK`

---

## Phase 4: New `formatting-bibtex-entries` skill (shared cleanup conventions)

### Task 4.1: Write the skill (extracted from `bibliography/AGENTS.md`, generalized)

**Files:**
- Create: `$WS/plugins/writing-skills/skills/formatting-bibtex-entries/SKILL.md`

- [ ] **Step 1: Write the SKILL.md**

````markdown
---
name: formatting-bibtex-entries
description: Use when adding or cleaning a BibTeX entry in any writing repo — normalizing a key, title, journal/conference name, DOI, or page range, or stripping DBLP cruft. The shared formatting conventions; the repo's bib rule says which .bib file and any section organization.
---

# Formatting BibTeX entries

Shared cleanup conventions for `.bib` entries. The repo's `AGENTS.md` / bib rule specifies the
target file (e.g. `main.bib`, `bibliography/references.bib`) and any section organization.

## Key format
`author_year_keyword` — first author's surname lowercase (e.g. `de_moura`), four-digit year, one
short lowercase title keyword, underscores. Examples: `baldoni_2018_survey`, `de_moura_2008_z3`,
`glock_2024_pasda`. **Never change an existing key** without updating every `\cite{...}` that uses it.

## Source preference
Take metadata from an authoritative source — **OpenAlex** or **Crossref BibTeX**
(`api.crossref.org/works/<DOI>/transform/application/x-bibtex`) first; DBLP is a fallback. Verify
against the paper; never fabricate.

## Cleanup rules
1. **Key:** replace any source-generated key (e.g. DBLP's `DBLP:journals/jss/GlockPP24`) with `author_year_keyword`.
2. **Title:** title-case; brace-protect tool names/acronyms/proper nouns (`{PASDA}`, `{Java}`, `{QuickCheck}`); a leading tool name gets `{PASDA:} {A} ...`.
3. **Journal names:** expand abbreviations — `J. Syst. Softw.` → `Journal of Systems and Software`; `IEEE Trans. Software Eng.` → `{IEEE} Transactions on Software Engineering`; `Commun. ACM` → `Communications of the {ACM}`; `ACM Comput. Surv.` → `{ACM} Computing Surveys`; `Empir. Softw. Eng.` → `Empirical Software Engineering`.
4. **Conference (booktitle):** full name — `Proceedings of the {n}th <Conference>` (ordinal known) or `Proceedings of the {year} <Conference>`; wrap acronyms `{IEEE}`/`{ACM}`/`{USENIX}`.
5. **Strip** `timestamp`, `biburl`, `bibsource`.
6. **URL vs DOI:** keep `doi`; drop `url` if it is just `https://doi.org/<doi>`; keep `url` only for resources with no DOI.
7. **DOI:** lowercase (`10.1016/j.jss.2024.112037`).
8. **Pages:** en-dash `123--134`.

## Example (before → after)
```bibtex
@article{DBLP:journals/jss/GlockPP24,
  title     = {{PASDA:} {A} partition-based semantic differencing approach ...},
  journal   = {J. Syst. Softw.},
  doi       = {10.1016/J.JSS.2024.112037},
  timestamp = {Sat, 08 Jun 2024 13:15:41 +0200},
  biburl    = {https://dblp.org/rec/journals/jss/GlockPP24.bib}
}
```
becomes
```bibtex
@article{glock_2024_pasda,
  title   = {{PASDA:} {A} Partition-Based Semantic Differencing Approach with Best-Effort Classification of Undecided Cases},
  journal = {Journal of Systems and Software},
  doi     = {10.1016/j.jss.2024.112037},
}
```
````

- [ ] **Step 2: Verify**

Run: `WS=/Users/joaichberger/Projects/omp-writing-skills; test -f "$WS/plugins/writing-skills/skills/formatting-bibtex-entries/SKILL.md" && echo OK`
Expected: `OK`

---

## Phase 5: Commit + register the marketplace

### Task 5.1: git init + commit the plugin repo

- [ ] **Step 1: Commit**

```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
cd "$WS" && git init -q && git add -A && git commit -q -m "feat: writing-skills plugin (searching-literature, retrieving-paper-pdfs, formatting-bibtex-entries)

Generalized from the phd-thesis skills: skill://-resolved bundled script, env
UNPAYWALL_EMAIL, repo-agnostic bib/PDF paths. Single source of truth for the
academic writing repos." && echo committed
```
Expected: `committed`

### Task 5.2: Register the marketplace with OMP

- [ ] **Step 1: Add the marketplace (idempotent)**

```bash
omp plugin marketplace add /Users/joaichberger/Projects/omp-writing-skills
omp plugin marketplace list | grep -q omp-writing-skills && echo registered
```
Expected: `registered`. (Per-repo project-scope installs happen in the paper and thesis plans.)

---

## Phase 6: Verification

### Task 6.1: Confirm the plugin shape

- [ ] **Step 1: Verify the tree + skill frontmatter**

Run:
```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
find "$WS" -type f -not -path '*/.git/*' | sort
for s in searching-literature retrieving-paper-pdfs formatting-bibtex-entries; do
  head -3 "$WS/plugins/writing-skills/skills/$s/SKILL.md" | grep -q "name: $s" && echo "$s ok" || echo "$s BAD frontmatter"
done
```
Expected: marketplace.json, plugin.json, three `SKILL.md`, one `fetch_pdf.py`; `… ok` for each skill.

- [ ] **Step 2: Confirm no thesis-specific paths leaked into the generalized skills**

Run:
```bash
WS=/Users/joaichberger/Projects/omp-writing-skills
grep -rnE 'bibliography/references\.bib|\.omp/skills/retrieving-paper-pdfs' "$WS/plugins/writing-skills/skills" && echo "LEAK — fix" || echo "clean"
```
Expected: `clean` (the script invocation uses `skill://`; SKILL text uses repo-agnostic phrasing).

---

## Self-Review (completed by plan author)

- **Spec §8.4 coverage:** single-source plugin with the three generalized skills (Phases 2–4); created from the thesis originals; bundled script invoked via `skill://` so it works at any install location; env-overridable email; repo-agnostic SKILL text. Registered with OMP (Task 5.2); per-repo installs deferred to paper/thesis plans. Covered.
- **Robustness (no hacks):** the fetch script is copied verbatim except one env-var line (a real improvement, not a workaround); `skill://` resolution is the documented bash internal-URI behavior, not a hardcoded path; `--out` already existed in the script, so output is parameterized without script surgery; Task 6.2 asserts no thesis path leaked.
- **Placeholder scan:** the two "copy then edit" tasks (2.1, 3.1) operate on known existing files with exact replacement text — concrete, not placeholders. `formatting-bibtex-entries` is written in full.
- **Consistency:** marketplace `omp-writing-skills` / plugin `writing-skills` match the install commands in the paper and thesis plans (`writing-skills@omp-writing-skills`).
- **Caveat:** `skill://…/fetch_pdf.py` resolution in bash should be confirmed once on first real use (thesis plan Phase 3 smoke-tests it against a known DOI). If a future OMP build does not resolve `skill://` in argument position, resolve the path first (`p=$(…)`); not expected per the bash tool contract.
```
