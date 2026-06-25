---
title: "Agent Instruction-File & Repo Instrumentation Normalization — Design Spec"
type: spec
status: implemented
created: 2026-06-24
archived: 2026-06-25
---

# Agent Instruction-File & Repo Instrumentation Normalization — Design Spec

**Status:** Reviewed 2026-06-24; decisions recorded in §12 · **Author:** pairing session

**Tracking:** Untracked scratch artifact — kept in this repo, **not committed**; remove on
completion, only after the user confirms it is OK to delete.

**Goal:** Bring the agent setup of three repositories — `test-generalization-dev` (Teralizer
implementation), `test-generalization-paper` (ACM TOSEM paper), and `phd-thesis` — up to current
best practice: one canonical instruction file per repo, and the right *non-file* mechanisms
(skills, rules, hooks/extensions, subagents, MCP, advisor) for everything that does not belong in
an always-on file.

**Scope note:** This is a cross-repo design spec. Implementation is expected to decompose into
**one plan per repo** (each independently shippable) plus a shared "principles" reference. See
§13.

**Harness:** All three repos run under **oh-my-pi (`omp`)**. Recommendations target OMP-native
mechanisms; the broader Claude Code / agents.md ecosystem is the source of the *principles*.
Where OMP and Claude Code diverge, the OMP form is authoritative (§7).

---

## 1. Motivation

The instruction files have drifted: they are oversized, duplicated across provider-specific
files, contain rent-free "prompt-engineering" scaffolding, and reference files that no longer
exist. Separately, the repos under-use the mechanisms that should carry procedural and
enforcement concerns (skills, rules, hooks). The aim is a lean, accurate, layered setup where
**every line in an always-on file pays rent**, and procedures/guardrails live where they work.

Guiding principle (Anthropic memory docs; `writing-agent-instructions` skill): instruction files
are loaded into context *every session* — keep them concise (target <200 lines, ideally <100),
concrete, and verifiable; move multi-step procedures to skills and hard guarantees to
hooks/permissions.

---

## 2. Current state (verified on disk, 2026-06-24)

| Repo | Files | Size | Problems |
|---|---|---|---|
| `phd-thesis` | `AGENTS.md` (+ `CLAUDE.md`→symlink; nested `bibliography/`, `papers/` `AGENTS.md`) | 336 ln | **Best/model.** Over the 200-line target, driven by the ~80-line "Contribution Framing" essay. |
| `test-generalization-dev` | `CLAUDE.md` only (no `AGENTS.md`) | 310 ln / 15 KB | No `AGENTS.md` (OMP/cross-agent prefer it); "Read This First" header + ToC + 32-line "CLAUDE.md Maintenance" meta-section; "avoid marketing/temporal language" stated 3×; commit rules restate cbea.ms instead of `skill://commit`. |
| `test-generalization-paper` | `CLAUDE.md` | **583 ln / 28 KB** | ~450 lines of generic scaffolding (Metacognitive Checkpoints, Two-Level Planning, Adaptive Collaboration / Critical Analysis ASCII frameworks, Emergency Protocols, RFC 2119, Operational Memory) duplicating the harness. |
| | `AGENTS.md` | 40 ln | Good "Repository Guidelines" shape; underused; diverges from the 28 KB `CLAUDE.md`. |
| | `GEMINI.md` | 30 ln | Redundant dir overview; references two missing docs. |
| | `CLAUDE.md.backup` | 25 KB | Committed backup; delete. |

**Stale references found (must fix or remove):**
- paper: `@PAPER_SUMMARY.md` (cited as required, **missing**); `SPECIFICATION_EXTRACTION_TECHNICAL_DOC.md`,
  `TEST_GENERALIZATION_TECHNICAL_DOC.md` in `GEMINI.md` (**missing**); `TODO.md` (**absent**).
- dev: `docs/evaluation.md` (**missing**); `run.sh` in Quick Reference (**missing**); `TODO.md` (**absent**).

**Facts that constrain the design:**
- **CI is dual and mirrored:** paper/dev have both a GitLab pipeline (`.gitlab-ci.yml`, the internal
  AAU institute remote `git-isys.aau.at`) **and** GitHub Actions (`.github/workflows`, the public
  remote). Both are live and are meant to hold the **same state** — neither is vestigial; keep both.
  Thesis is GitHub-only (GitHub Actions).
- Paper has 32 `data/*.csv` (its data-source rules are valid).
- Thesis already uses pre-commit + CI and an `AGENTS.md`-as-source + symlink + nested-`AGENTS.md`
  pattern.

---

## 3. Principles adopted

1. **One canonical file, tracked symlinks — uniformly.** `AGENTS.md` is the committed source of
   truth in every repo; `CLAUDE.md` and `GEMINI.md` are **tracked** symlinks to it. No
   provider-specific deltas, and **no per-repo peculiarities** — no instruction file is gitignored
   or kept untracked. Rationale in §5.
2. **Every line pays rent.** Concise, concrete, verifiable. Delete ceremony and anything the
   harness/system prompt already enforces.
3. **Route by axis, not by habit** (§6): always-on fact → `AGENTS.md`; subtree-only fact →
   path-scoped rule; on-demand procedure → skill; triggered action → command; isolated/restricted
   work → subagent; must-always/never → hook + permissions; new capability → MCP; per-turn
   critique → advisor + WATCHDOG; cross-session learning → auto-memory.
4. **Don't make the model do a linter's job.** Style/format → tooling (pre-commit/CI), not prose.
5. **Reference skills, don't restate them.** Commit policy → `skill://commit`; don't re-encode.
6. **Proactive over reactive** (§9): guidance that should shape *generation* stays in the main
   agent's context; the advisor is a complementary *second pass*, never the primary home.

Sources: agents.md; agentskills.io; Anthropic "How Claude remembers your project" and "Steering
Claude Code"; the `writing-agent-instructions` skill; OMP docs (`omp://{context-files,skills,
rulebook-matching-pipeline,hooks,extensions,mcp-config,settings,task-agent-discovery,
slash-command-internals,advisor-watchdog,memory,system-prompt-customization,marketplace}.md`).

---

## 4. Canonical file policy (cross-cutting)

- Source file: **`AGENTS.md`** (tracked, committed) — the same layout in every repo, no exceptions.
- `CLAUDE.md` and `GEMINI.md` → **tracked symlinks** to `AGENTS.md` (or set
  `contextFileName: "AGENTS.md"` in `.gemini/settings.json`). **No instruction file may be gitignored
  or untracked** — e.g. the dev repo's current `.gitignore` entry for `CLAUDE.md` is removed (§8.1).
- Windows-only caveat (not applicable on this macOS workstation): use an `@AGENTS.md` import
  instead of a symlink.
- OMP behavior that makes this correct: OMP discovers `AGENTS.md`, `CLAUDE.md`, *and* `GEMINI.md`
  and **collapses byte-identical files (realpath-aware)**; divergent copies are *all* injected.
  Symlinking guarantees one logical file in context.
- Target sizes: root files <200 lines (ideally ~100). Nested `AGENTS.md` only where a subtree has
  materially different commands/conventions/boundaries (thesis `bibliography/`, `papers/` already
  do this correctly).

---

## 5. Why symlink, not provider deltas (decision record)

Considered: (a) symlink all to `AGENTS.md`; (b) minimal `CLAUDE.md` that `@AGENTS.md`-imports plus
Claude-only deltas. **Chosen: (a).** There is no Claude-only content to justify divergence, agents.md
and Anthropic both document the symlink path, and OMP's byte-identical dedup means a symlink is the
zero-divergence option. Provider deltas would add a maintenance seam for no benefit.

---

## 6. The mechanism stack (routing model)

| Axis | Mechanism | Loads | Enforcement |
|---|---|---|---|
| Always-true fact | `AGENTS.md` / nested `AGENTS.md` | every session | advisory |
| Subtree-only fact | path-scoped rule | when touching matching files | advisory |
| Hard rule, must stay visible | sticky `RULES.md` | re-attached near current turn | advisory (strong) |
| On-demand procedure/reference/script | skill | description always; body on trigger | advisory |
| Triggered action w/ args | slash command | on `/invoke` | runs a prompt |
| Isolated/restricted specialist | subagent | own context window | tools hard-scoped |
| New runtime capability | MCP server | tool schemas (lazy) | n/a |
| Must always/never happen | hook/extension + permissions | out-of-band | **deterministic** |
| Per-turn critique | advisor + `WATCHDOG.md` | second model | advisory review |
| Cross-session learning | auto-memory backend | summary at startup | advisory |

Rules of thumb: wrong twice → `AGENTS.md`; grew into a procedure or only matters in one subtree →
skill/rule; pasted a playbook a third time → skill; must hold every time regardless of judgment →
hook/permission.

---

## 7. OMP-native mapping & gotchas (so plans target the right files)

These are the places where authoring Claude-Code-shaped config would silently no-op under OMP:

- **Instruction files:** native project file is `.omp/AGENTS.md` (highest discovery priority); a
  standalone root `AGENTS.md` works via the `agents-md` provider. Symlinked `CLAUDE.md`/`GEMINI.md`
  also discovered.
- **Skills:** `.omp/skills/<name>/SKILL.md` (or `.agent[s]/skills/`), `description` **required**,
  read via `skill://`. CC-only frontmatter (`paths:`, `context: fork`, `` !`cmd` `` injection) does
  not apply — use rule `globs` for path-triggering.
- **Rules:** `.omp/rules/*.md` with `description` (rulebook, on-demand via `rule://`) + `globs:`
  (path gate), or `condition:`/`astCondition:` (TTSR — regex/ast-grep triggers). Sticky hard rules
  → top-level `.omp/RULES.md`.
- **Hooks/enforcement:** OMP hooks are **TypeScript extensions** (`.omp/hooks/*.ts` /
  `.omp/extensions/`, `pi.on("tool_call", …) => {block}`), **not** Claude's `settings.json`
  JSON+shell hooks. Permissions are **per-tool** (`tools.approval`, `tools.approvalMode` in
  `.omp/config.yml`), **not** per-path/command deny-globs — a path guard (e.g. block edits under a
  read-only submodule) needs a TS `tool_call` extension. [INFERENCE: OMP does not execute Claude's
  JSON-shell hook format as-is; verify before relying.]
- **Subagents:** `.omp/agents/*.md` only. OMP deliberately ignores `.claude/agents` and
  `.github/agents` for task agents.
- **Commands:** `.omp/commands/*.md` (template + `$ARGUMENTS`/`$1`); no `` !`cmd` `` build-time bash
  injection (CC-only) — have the body run commands via `bash`.
- **MCP:** `.omp/mcp.json` (or root `mcp.json`), `${VAR}` expansion; manage via `/mcp`.
- **Shared skills across a subset of repos:** local **marketplace** + **project-scoped plugin**
  install (`omp plugin install --scope project …`, recorded in each repo's
  `.omp/plugins/installed_plugins.json`). See §8.4.
- **Reviewer:** native **advisor** (`advisor.enabled` + `modelRoles.advisor`) + **`WATCHDOG.md`**
  (advisor-only; see §9).
- **Prompt customization:** `APPEND_SYSTEM.md` (adds a block; keeps default prompt + skills);
  avoid `SYSTEM.md` (replaces the base block).
- **Portable determinism:** git pre-commit + CI is the agent-independent baseline (thesis has it;
  paper has CI). OMP TS extensions add in-session, per-tool interception on top.

---

## 8. Per-repo target design

### 8.1 `test-generalization-dev` (Teralizer)

- **Files:** create tracked `AGENTS.md` (canonical); **remove the `CLAUDE.md` `.gitignore` entry** and symlink `CLAUDE.md` + `GEMINI.md` → `AGENTS.md` (all tracked, matching paper/thesis). Trim to facts:
  Quick-Reference commands, build/DB/analysis commands, DB tables/views, the genuine
  `LIKE '%%'` SQLAlchemy-escaping trap, legacy-exclusion note. Remove: "Read This First" header,
  ToC, the "CLAUDE.md Maintenance" section, the triple-stated language rules (collapse to one line
  → `skill://commit`). Fix/remove stale `docs/evaluation.md`, `run.sh`, `TODO.md` references.
- **Skills (`.omp/skills/`):** `db-query-patterns` (schema + RQ query conventions + the escaping
  trap; pairs with the Postgres MCP), `gradle-build-triage`, `python-analysis-conventions`,
  `packaging-artifact` (Zenodo/replication flow).
- **Rules (`.omp/rules/`):** `python.md` (`globs: ["analysis/**/*.py","**/*.ipynb"]` — uv/ruff/ty,
  clear notebook outputs); a DB/SQL rule.
- **MCP / Postgres — DB located:** the live DB is a Dockerized PostgreSQL in *this* repo — service
  `postgres` / container **`postgres-teralizer`** (`docker-compose.yml`), data volume
  `./database/teralizer`, on `localhost:${DB_PORT:-5432}`, started via `./gradlew startPostgres` or
  `docker compose up postgres adminer` (Adminer `localhost:18080`, pgAdmin `:18081`). Active
  database per `.env`: `postgres_timeout_retry`; documented datasets `postgres_dev`
  (eqbench + commons-utils) and `postgres_test` (RepoReapers). **Decision: target this working
  `postgres-teralizer` instance** for the MCP. The **replication package** ships a separate,
  reproducible, localhost-bound instance (container `postgres-replication`, user `teralizer`, dbs
  `postgres_dev`/`postgres_test`, loaded by `replication/scripts/import-databases.sh`) — a viable
  alternative later, but not the target now. The sibling repo `~/Projects/test-generalization` (no
  `-dev`) is the **predecessor** (last touched Apr 2025) — exclude.
- **Read-only role — does NOT exist yet.** Both compose files connect as superusers
  (`postgres`/`postgres` and `teralizer`/`teralizer`). Create a least-privilege role and point the
  MCP at it (defense-in-depth alongside the server's `--readonly`/restricted mode):
  ```sql
  CREATE ROLE teralizer_ro LOGIN PASSWORD '<set-me>';
  GRANT CONNECT ON DATABASE postgres_dev TO teralizer_ro;        -- repeat per DB
  GRANT USAGE ON SCHEMA public TO teralizer_ro;
  GRANT SELECT ON ALL TABLES IN SCHEMA public TO teralizer_ro;
  ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO teralizer_ro;
  ```
- **MCP server:** `@bytebase/dbhub --readonly` (or `crystaldba/postgres-mcp` restricted) in
  `.omp/mcp.json`, DSN `postgresql://teralizer_ro:…@localhost:5432/<db>` supplied via
  `${TERALIZER_DB_DSN}` env expansion (no creds in the file). Pairs with `db-query-patterns`.
- **Security note (out of scope):** the dev `.env` contains a live GitHub PAT in plaintext. User
  has **declined** rotation for now — left as-is by explicit choice; not an action item.
- **Enforcement:** keep/extend pre-commit (ruff/ty). Optional OMP TS extension: block destructive
  bash (`rm -rf`, `DROP`/`TRUNCATE`) and edits to generated/build dirs.
- **Subagent (`.omp/agents/`):** a test/build runner that returns only failures (context
  isolation for verbose Gradle output).
- **Memory:** consider `memory.backend: local` for cross-session DB/query lore.

### 8.2 `test-generalization-paper` (ACM TOSEM) — biggest cleanup

- **Files:** make `AGENTS.md` the single source (~60–90 lines); symlink `CLAUDE.md` and
  `GEMINI.md` → `AGENTS.md`; **delete `CLAUDE.md.backup`**. Salvage the ~6 real paper-specific rules
  from the 583-line `CLAUDE.md` into `AGENTS.md`: data-source comments for every number, `~`
  non-breaking-space typography, tool/dataset macro usage, `chktex` after edits, abstract word
  budget. **Delete** the scaffolding (frameworks, checkpoints, RFC 2119, emergency protocols).
  Keep the concrete, checkable quality bars in-context (see §9). Remove stale `PAPER_SUMMARY.md`/
  `TODO.md` references.
- **Skills:** the bibliography skills (`searching-literature`, `retrieving-paper-pdfs`,
  `formatting-bibtex-entries`) come from the shared `writing-skills` plugin (§8.4), installed at
  project scope. Ship paper-only `latex-build-triage` (+ `references/errors.md`) and
  `acmart-conventions` (anonymous/review vs camera-ready toggles, ACM Reference Format) as project
  skills under `.omp/skills/`.
- **Rules (`.omp/rules/`):** prose style `globs: ["sections/**/*.tex"]`; bib hygiene
  `globs: ["main.bib"]`.
- **Reviewer:** ship `WATCHDOG.md` with the review priorities (overclaim, citation–claim
  alignment, cross-section number consistency); the corresponding proactive bars also stay in
  `AGENTS.md`/the prose rule (§9). Advisor on/off is a per-session toggle, not part of this spec.
- Optional MCP: Zotero at user scope.

### 8.3 `phd-thesis` — already the model; light touch

- Keep the `AGENTS.md`-source + symlink + nested-`AGENTS.md` pattern (it's the template the others
  should copy).
- **Add a `GEMINI.md` → `AGENTS.md` symlink** for parity (currently only `CLAUDE.md` is symlinked) —
  same tracked three-file layout as the other repos.
- Relocate the ~80-line "Contribution Framing" essay to a `.omp/rules/` rule scoped to
  `chapters/**` (or a `thesis-framing` skill) to bring root `AGENTS.md` under ~200 lines.
- **Migrate** `searching-literature` + `retrieving-paper-pdfs` into the shared `writing-skills`
  plugin (§8.4) — the thesis is their origin — then drop the thesis-local copies and install the
  plugin. Keep `bibliography/AGENTS.md` + `papers/AGENTS.md` (thesis-specific), moving their shared
  cleanup rules to `skill://formatting-bibtex-entries` and leading metadata with OpenAlex/Crossref.
- Ship `WATCHDOG.md` from its existing "challenge overclaiming / verify citations / flag
  inconsistencies" rules; the in-context bars stay in `AGENTS.md` too (§9). Advisor on/off remains
  a per-session toggle.
- Keep pre-commit + CI as the portable enforcement layer.

### 8.4 Shared writing-repo skills (distribution)

The proven bibliography toolkit already exists in the thesis — `searching-literature` (OpenAlex/
Crossref-lead search, citation-graph traversal, DOI verification), `retrieving-paper-pdfs` (the
OA→Sci-Hub→repository→arXiv fallback chain + a `%PDF`-validating `fetch_pdf.py`), and the cleanup
conventions in `bibliography/AGENTS.md`. These are useful across the **writing** repos (paper,
thesis, future papers) but must **not** load in non-writing repos like `test-generalization-dev`
(even a cheap skill's always-on `description` can nudge an agent). That rules out user-level skills
(`~/.omp/agent/skills/`, which load everywhere) — and a new DBLP-first skill (rejected: it would
duplicate and contradict the proven, OpenAlex/Crossref-lead workflow, and DBLP has been unreliable).

**Chosen mechanism:** a small **local OMP marketplace** (`~/Projects/omp-writing-skills`) holding a
`writing-skills` plugin with the three skills **migrated and generalized from the thesis** — single
source of truth — installed at **project scope** in each writing repo:

```
omp plugin marketplace add ~/Projects/omp-writing-skills
omp plugin install --scope project writing-skills@omp-writing-skills   # paper and thesis
```

Generalization (so nothing is repo-specific): the bundled script is invoked via
`skill://retrieving-paper-pdfs/scripts/fetch_pdf.py` (auto-resolves to a filesystem path in bash at
any install location); output path via the existing `--out`; `UNPAYWALL_EMAIL` env-overridable; SKILL
text refers to "the repo's bib file / PDF policy". A third skill `formatting-bibtex-entries` holds
the shared cleanup conventions so paper and thesis don't duplicate them. The thesis **drops its
local copies** and consumes the plugin; project-scoped installs live in each repo's
`.omp/plugins/installed_plugins.json`, so non-writing repos never load them and future papers opt in
with the same one-line install.

**Prose stays per-repo (decision).** `writing-chapter-prose` is thesis-flavored and reliable;
generalizing it earns less than the bib skills and risks destabilizing it. The paper gets an
acmart-specific prose rule instead; a shared `academic-prose` skill can be extracted later if a
clear shared core emerges.

---

## 9. Advisor / WATCHDOG decision (resolved)

**Decision:** proactive, checkable quality bars **stay in the main agent's context** (`AGENTS.md`
or a `*.tex` path-scoped rule); the advisor + `WATCHDOG.md` is an **optional second line of
defense**, not the home for the guidance.

Rationale: `WATCHDOG.md` is **advisor-only** — OMP does **not** inject it into the primary agent
(`omp://advisor-watchdog.md`). Putting the guidance only there is purely reactive: the writer
produces the overclaim, then the advisor flags it a turn later. The advisor also doesn't see
`AGENTS.md` (it runs on its own system prompt + WATCHDOG + the transcript delta).

**Advisor enablement is a per-session OMP setting** (`advisor.enabled` / `/advisor on`), not a spec
decision. This spec only commits to **shipping `WATCHDOG.md`** (review priorities) plus the
in-context bars; whether to actually run the advisor in a given session is the user's runtime call.
Shipping the file is harmless when the advisor is off.

Routing of the paper's current "reviewer mindset" section:
- **Concrete checkable bars** ("every claimed contribution maps to a results subsection"; "a
  citation must support the *specific* claim"; "no 'proves' without empirical backing"; "numbers
  consistent across sections") → **main context** (`AGENTS.md`/`*.tex` rule). *Must-have.*
- **Ceremony** (ASCII Critical-Analysis framework, metacognitive checkpoints, conflict hierarchies,
  "simulate a reviewer") → **delete.**
- **`WATCHDOG.md`** → holds the review priorities, and may **restate the 2–3 most-violated bars**
  for independent re-checking (deliberate, minimal duplication) plus review-only emphasis that
  would be noise in the writer.

---

## 10. Deterministic enforcement strategy

- **Portable baseline (preferred, agent- and human-independent):** git pre-commit + CI for
  format/lint/spelling/artifact-blocking/no-push-to-main. Thesis already has this; extend to paper
  and dev as appropriate. Note the dev/paper CI is dual (GitLab + GitHub mirrors, §2) — any CI
  gate must be added to both.
- **In-session (OMP-specific, additive):** TS `tool_call` extensions for guards pre-commit can't do
  (block destructive bash before it runs; protect read-only submodule/generated paths), since OMP
  permissions are per-tool, not per-path.
- **Sticky `RULES.md`:** a handful of hard "never" lines (e.g. `projects/` is read-only; never push
  without asking; never commit build artifacts).

---

## 11. Non-goals

- No rewrite of repo READMEs or human-facing docs.
- No editing of `projects/` submodules (read-only upstream) or `spf-eval` (out of scope).
- No new MCP servers beyond the read-only Postgres (Teralizer) and optional Zotero (writing repos).
- No `SYSTEM.md` base-prompt replacement; no Claude-Code JSON hooks; no output-style files (use
  `APPEND_SYSTEM.md` if needed).
- No custom slash commands (`.omp/commands/`) — maintainer preference; the equivalent workflows
  live in skills (`latex-build-triage`, the bib skills).
- Shared writing skills must **not** be installed user-level or in non-writing repos; they live in a
  writing-only plugin installed at project scope (§8.4).
- Not solving the reviewer-feedback paper work — that is a separate track.

---

## 12. Resolved decisions (review 2026-06-24)

1. **Spec location/commit:** keep this file here, **do not commit** (scratch artifact); remove on
   completion — **only after the user confirms** it is OK to delete.
2. **Advisor/WATCHDOG:** ship `WATCHDOG.md` in the writing repos (harmless when the advisor is off);
   advisor enablement is a per-session OMP toggle and out of scope (§9).
3. **Postgres MCP / DB:** target the working **`postgres-teralizer`** instance in *this* repo
   (§8.1, confirmed); replication instance is a later alternative; sibling `test-generalization`
   (no `-dev`) excluded. **No read-only role exists** — create `teralizer_ro` (§8.1) and use the
   server's `--readonly`/restricted mode; supply the DSN via `${TERALIZER_DB_DSN}`. GitHub-PAT
   rotation in dev `.env`: **declined by user** (their call).
4. **Shared skills:** the three bibliography skills (`searching-literature`, `retrieving-paper-pdfs`,
   `formatting-bibtex-entries`), migrated/generalized from the thesis into a project-scoped
   `writing-skills` plugin (`~/Projects/omp-writing-skills`); installed only in paper + thesis
   (+ future papers); never user-level or in non-writing repos (§8.4). Metadata leads with
   OpenAlex/Crossref (DBLP fallback); the DBLP-first skill idea was rejected. Prose stays per-repo.
5. **Dev/paper CI:** both GitLab (internal AAU institute) and GitHub (public) are live and mirror
   the same state — keep both; neither is vestigial (§2, §10).

---

## 13. Decomposition into implementation plans

This spec yields these implementation plans (each independently shippable), in dependency order:
0. **Shared writing-skills plugin** — build the `omp-writing-skills` marketplace + plugin by
   migrating/generalizing the thesis's `searching-literature` + `retrieving-paper-pdfs` and adding
   `formatting-bibtex-entries`. Prerequisite for the paper and thesis bibliography steps.
1. **Paper** (largest win: 583-line `CLAUDE.md` → ~90-line `AGENTS.md` + symlinks; delete backup; install the plugin).
2. **Dev** (remove the `CLAUDE.md` gitignore; tracked `AGENTS.md` + symlinks; skills/rules; Postgres MCP).
3. **Thesis** (relocate framing essay; `GEMINI.md` symlink; WATCHDOG.md; migrate bib skills to the plugin, drop local copies, align `bibliography/AGENTS.md`).

A shared "principles + OMP gotchas" reference (§3, §6, §7) is common to all three.

## 14. Success criteria

- Each repo has exactly one canonical `AGENTS.md` (root <200 lines), with `CLAUDE.md`/`GEMINI.md`
  as symlinks; `CLAUDE.md.backup` gone.
- Zero stale path references in any instruction file (every referenced file exists).
- No harness-duplicating ceremony; commit guidance via `skill://commit`.
- Procedures live in `.omp/skills/`; subtree-only guidance in `.omp/rules/` with `globs`; shared
  writing skills load in paper + thesis only, never in `test-generalization-dev`.
- Concrete quality bars remain in-context; `WATCHDOG.md` shipped; advisor left to per-session choice.
- Read-only Postgres MCP works against a non-privileged `teralizer_ro` role.
- Portable enforcement (pre-commit/CI, both GitLab + GitHub) green; any OMP TS guards verified to
  block in-session.
