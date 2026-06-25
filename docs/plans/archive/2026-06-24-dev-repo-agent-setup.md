---
title: "Dev Repo (Teralizer) Agent Setup Implementation Plan"
type: plan
status: implemented
created: 2026-06-24
parent: 2026-06-24-agent-instruction-files-normalization-design
archived: 2026-06-25
---

# Dev Repo (Teralizer) Agent Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan **inline** (batch execution with review checkpoints) — **no subagents, no worktrees**. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give `test-generalization-dev` a tracked, canonical `AGENTS.md` (trimmed from the 310-line gitignored `CLAUDE.md`, with `CLAUDE.md` as a symlink), plus OMP-native mechanisms: path-scoped rules, project skills, a read-only PostgreSQL MCP, a build/test subagent, and a sticky `RULES.md`.

**Architecture:** `AGENTS.md` (facts + commands + DB notes + the `LIKE '%%'` trap, <100 lines) is the tracked source of truth; `CLAUDE.md` is a symlink (stays gitignored). Procedures → `.omp/skills/`; subtree guidance → `.omp/rules/` with `globs`; the read-only DB MCP pairs with the `db-query-patterns` skill; hard "never" rules → sticky `.omp/RULES.md`. Verbose build/test output is isolated in a `.omp/agents/` subagent.

**Tech Stack:** OMP harness; Java/Gradle (+ SPF/`jpf-symbc` submodule); PostgreSQL 17 (Docker, `postgres-teralizer`); Python analysis via `uv`/`ruff`/`ty`/`pytest`/Jupyter; `@bytebase/dbhub` MCP server.

**Source spec:** `docs/plans/archive/2026-06-24-agent-instruction-files-normalization-design.md` (§8.1). Scratch plan — do not commit the plan/spec docs; repo changes are committed normally.

**Consistency principle:** every repo uses the SAME tracked layout — `AGENTS.md` (canonical) plus `CLAUDE.md` and `GEMINI.md` as tracked symlinks; no instruction file is gitignored or untracked. The dev repo currently **gitignores** `CLAUDE.md` (`.gitignore:385`); this plan **removes that peculiarity** so dev matches paper/thesis.

**Repo path:** `/Users/joaichberger/Projects/test-generalization-dev` (`$DEV`, == cwd).

---

## Phase 1: Instruction-file consolidation

### Task 1.1: Author the canonical `AGENTS.md`

**Files:**
- Create: `AGENTS.md` (new, tracked)

- [ ] **Step 1: Write `AGENTS.md`**

Create `AGENTS.md` with this content (facts only; salvaged from `CLAUDE.md`, ceremony removed, stale `run.sh`/`docs/evaluation.md`/`TODO.md` references dropped):

````markdown
# Repository Guidelines — Teralizer

Teralizer transforms JUnit tests into property-based jqwik tests. It runs Symbolic PathFinder (SPF)
in constraint-collection mode along a test's concrete path to extract path-exact specifications
(input partitions + symbolic outputs), then generates property-based tests that explore more inputs
within the same execution paths. Java/Gradle pipeline + PostgreSQL + a Python analysis project.

## Commands
| Task | Command |
|---|---|
| Build (incl. SPF submodules) | `./gradlew build` |
| Run one config | `./gradlew run -Dteralizer.config=<config-file>` |
| Start / stop DB | `./gradlew startPostgres` / `./gradlew stopPostgres` |
| DB UI | `docker compose up adminer` → http://localhost:18080 (password: `teralizer`) |
| Analysis deps | `uv sync --directory analysis` |
| Notebooks | `uv run --directory analysis jupyter lab` |
| Validate (pre-commit gate) | `uv run --directory analysis python validate.py --changed` |
| Lint / format / types / tests | `uv run --directory analysis ruff check --fix .` · `ruff format .` · `ty check .` · `pytest` |

Run everything from the project root. Validate before committing (`validate.py --changed`, or
`--notebook <NAME.ipynb>` for one notebook; no flag = full run).

## Layout & config
- Architecture / DB references: `docs/architecture.md`, `docs/database.md`.
- Config: Typesafe Config (HOCON); examples in `project-configs/example-*.conf`.
- Analysis in `analysis/`; legacy notebooks under `notebooks/legacy/` are excluded via `pyproject.toml`.
- Exports: `save_latex_table`, `save_csv_data`, `save_figure` from `teralizer.exports` →
  `analysis/output/{tables,data,figures}`.
- Paper sync (on-demand only): `uv run --directory analysis python sync.py`; `PAPER_REPO_PATH` in
  `.env`. Never sync automatically.

## Database
Dockerized Postgres, container `postgres-teralizer`, `localhost:5432`. Databases: `postgres_dev`
(eqbench + commons-utils), `postgres_test` (RepoReapers), `postgres_timeout_retry`. Direct query:
`docker exec -i postgres-teralizer psql -U postgres -d <db> -c "..."`.
- Key tables: `project`, `test`, `assertion`, `generalization`, `task`, `filter_result`.
- Key views: `v_project_failures`, `mv_exclusions_*`.
- For read-only analytical queries, prefer the `db-query-patterns` skill + the read-only Postgres MCP.

## Pitfalls
- **Raw-SQL `LIKE` escaping:** in SQLAlchemy raw strings, double the percent signs —
  `LIKE '%%_TRIES'`, not `LIKE '%_TRIES'` (a single `%` causes a parameter error).

## Style & commits
- Explicit over implicit; minimal comments (explain *why*, not *what*); fail fast.
- No marketing/temporal language in code or comments ("modern", "new", "enhanced").
- Never reference paper section numbers (e.g. "Section 4.1") or legacy notebooks in code/notebooks.
- Commits: follow `skill://commit`.

## Boundaries
- `projects/` holds git submodules (target programs) — **read-only**; don't edit them as Teralizer work.
- Never commit build artifacts or generated datasets.
````

- [ ] **Step 2: Verify length + no stale references**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
wc -l AGENTS.md
grep -nE 'run\.sh|docs/evaluation\.md|TODO\.md|Table of Contents|Read This Entire' AGENTS.md || echo "clean"
```
Expected: under 100 lines; `clean`.

### Task 1.2: Remove the gitignore peculiarity; symlink `CLAUDE.md` + `GEMINI.md`

**Files:**
- Modify: `.gitignore` (remove the `CLAUDE.md` entry, ~line 385)
- Replace: `CLAUDE.md` (regular file → tracked symlink)
- Create: `GEMINI.md` (tracked symlink)

- [ ] **Step 1: Stop ignoring `CLAUDE.md`**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
sed -i '' '/^CLAUDE\.md$/d' .gitignore
git check-ignore CLAUDE.md && echo "STILL IGNORED — fix .gitignore by hand" || echo "no longer ignored"
```
Expected: `no longer ignored`.

- [ ] **Step 2: Replace `CLAUDE.md` with a symlink and add `GEMINI.md`**

```bash
cd /Users/joaichberger/Projects/test-generalization-dev
rm -f CLAUDE.md
ln -s AGENTS.md CLAUDE.md
ln -s AGENTS.md GEMINI.md
```

- [ ] **Step 3: Verify both resolve and are no longer ignored**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
readlink CLAUDE.md; readlink GEMINI.md
test -f CLAUDE.md && test -f GEMINI.md && echo "resolve OK"
git check-ignore CLAUDE.md GEMINI.md || echo "tracked-eligible"
```
Expected:
```
AGENTS.md
AGENTS.md
resolve OK
tracked-eligible
```

### Task 1.3: Commit the consolidation (`AGENTS.md` + symlinks + `.gitignore`)

- [ ] **Step 1: Commit everything (all tracked, consistent with paper/thesis)**

```bash
cd /Users/joaichberger/Projects/test-generalization-dev
git add AGENTS.md CLAUDE.md GEMINI.md .gitignore
git commit -q -m "docs: add canonical AGENTS.md; track CLAUDE/GEMINI symlinks

Trim the prior gitignored CLAUDE.md into a facts-only AGENTS.md (commands, DB
notes, the LIKE '%%' SQLAlchemy trap, boundaries); drop ceremony and stale
references (run.sh, docs/evaluation.md, TODO.md). Stop gitignoring CLAUDE.md and
add CLAUDE.md/GEMINI.md symlinks so this repo matches paper/thesis." && echo committed
```
Expected: `committed`

---

## Phase 2: Rules (path-scoped + sticky)

### Task 2.1: Python analysis rule

**Files:**
- Create: `.omp/rules/python.md`

- [ ] **Step 1: Write the rule**

```markdown
---
description: Python analysis conventions (uv, ruff, ty, notebooks)
globs:
  - "analysis/**/*.py"
  - "**/*.ipynb"
---

# Python analysis conventions

- Manage env/deps with `uv` (never bare `pip`); run tools via `uv run --directory analysis ...`.
- Lint+fix `ruff check --fix`, format `ruff format`, type-check `ty check`, test `pytest`.
- Run `validate.py --changed` before committing analysis changes.
- Clear notebook outputs before committing; `notebooks/legacy/` is excluded via `pyproject.toml`.
- Export via `teralizer.exports` (`save_latex_table`/`save_csv_data`/`save_figure`), not ad-hoc writes.
```

### Task 2.2: Database rule

**Files:**
- Create: `.omp/rules/db.md`

- [ ] **Step 1: Write the rule**

```markdown
---
description: Database access conventions for Teralizer's PostgreSQL
globs:
  - "analysis/**/*.py"
  - "**/*.sql"
---

# Database conventions

- Container `postgres-teralizer`, `localhost:5432`; DBs `postgres_dev`, `postgres_test`,
  `postgres_timeout_retry`. Schema reference: `docs/database.md`.
- Raw-SQL `LIKE`: double percent signs in SQLAlchemy strings (`LIKE '%%_TRIES'`).
- Prefer read-only access for analysis; the read-only MCP (`skill://db-query-patterns`) over ad-hoc
  superuser `psql`.
- Never DROP/TRUNCATE or write to the analysis databases from analysis code.
```

### Task 2.3: Sticky hard rules

**Files:**
- Create: `.omp/RULES.md`

- [ ] **Step 1: Write the sticky rules**

```markdown
- `projects/` contains git submodules (target programs) and is read-only — never edit them.
- Never commit build artifacts, generated datasets, or the `.env` file.
- Never push without an explicit request.
```

- [ ] **Step 2: Verify and commit Phase 2**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
test -f .omp/rules/python.md && test -f .omp/rules/db.md && test -f .omp/RULES.md && \
git add .omp/rules .omp/RULES.md && git commit -q -m "feat: add .omp path-scoped rules and sticky RULES.md" && echo committed
```
Expected: `committed`

---

## Phase 3: Project skills

### Task 3.1: `db-query-patterns`

**Files:**
- Create: `.omp/skills/db-query-patterns/SKILL.md`

- [ ] **Step 1: Write the skill (references `docs/database.md` rather than duplicating the schema)**

````markdown
---
name: db-query-patterns
description: Query Teralizer's PostgreSQL for analysis. Use when inspecting the schema, writing analytical SQL, computing RQ statistics, or debugging pipeline/filter results across the dev/test/timeout_retry datasets.
---

# Teralizer DB query patterns

Full schema: `docs/database.md`. Container `postgres-teralizer`, `localhost:5432`.
Datasets: `postgres_dev` (eqbench + commons-utils), `postgres_test` (RepoReapers),
`postgres_timeout_retry`.

## Access
Prefer the read-only MCP (`teralizer-db`) for analytical queries. Ad-hoc CLI:
```bash
docker exec -i postgres-teralizer psql -U postgres -d postgres_dev -c "SELECT ..."
```

## Core tables / views
- `project`, `test`, `assertion`, `generalization`, `task` (stage/status/info), `filter_result`
  (ACCEPT/DEFER/REJECT).
- `v_project_failures` (failed projects + reasons); `mv_exclusions_*` (materialized exclusion stats).

## Gotcha
Raw-SQL `LIKE` in SQLAlchemy needs doubled `%`: `WHERE ec.teralizer_variant LIKE '%%_TRIES'`.

## Example: filter outcomes
```sql
SELECT decision, COUNT(*) FROM filter_result GROUP BY decision ORDER BY 2 DESC;
```
````

### Task 3.2: `gradle-build-triage`

**Files:**
- Create: `.omp/skills/gradle-build-triage/SKILL.md`

- [ ] **Step 1: Write the skill**

````markdown
---
name: gradle-build-triage
description: Build/run the Teralizer Gradle pipeline and triage failures. Use when ./gradlew build fails, the SPF/jpf-symbc submodule is missing, or a run config errors.
---

# Gradle build triage

## Build & run
```bash
./gradlew build                                   # includes SPF submodules
./gradlew run -Dteralizer.config=project-configs/example-maven-junit5.conf
```

## Triage
1. **Submodule errors** (`jpf-symbc` missing / classpath): `git submodule update --init --recursive`, rebuild.
2. **DB connection refused**: `./gradlew startPostgres` first; confirm `postgres-teralizer` is up.
3. **Config not found**: pass an existing `project-configs/example-*.conf` (HOCON).
4. Read the Gradle stacktrace from the bottom up; rerun the single failing task with `--stacktrace`.
````

### Task 3.3: `python-analysis-conventions`

**Files:**
- Create: `.omp/skills/python-analysis-conventions/SKILL.md`

- [ ] **Step 1: Write the skill**

````markdown
---
name: python-analysis-conventions
description: How to run and validate the Python analysis project. Use when working in analysis/, running notebooks, validating changes, or adding analysis modules/exports.
---

# Python analysis conventions

Always run from the repo root, tools via `uv`:
```bash
uv sync --directory analysis
uv run --directory analysis python validate.py --changed   # gate before commit
uv run --directory analysis ruff check --fix . && uv run --directory analysis ruff format .
uv run --directory analysis ty check . && uv run --directory analysis pytest
uv run --directory analysis jupyter lab
```
- `validate.py` covers imports/env/DB/notebook-exec/lint/types. Use `--notebook <NAME.ipynb>` to scope.
- Clear notebook outputs before commit; `notebooks/legacy/` is excluded.
- Output via `teralizer.exports` → `analysis/output/{tables,data,figures}`.
- Paper sync is manual: `uv run --directory analysis python sync.py` (needs `PAPER_REPO_PATH`).
````

### Task 3.4: `packaging-artifact`

**Files:**
- Create: `.omp/skills/packaging-artifact/SKILL.md`

- [ ] **Step 1: Write the skill (references the verified `replication/` flow)**

````markdown
---
name: packaging-artifact
description: Reproduce or package the Teralizer replication artifact. Use when building the replication package, importing databases, re-running analysis in Docker, or verifying outputs match.
---

# Replication / artifact packaging

Self-contained Docker flow lives in `replication/` (`docker-compose.yml`, `quick-start.sh`,
`.env.example`, `scripts/`).

## Quick start (inspect data, re-run analysis)
```bash
cd replication
cp .env.example .env            # set DB creds/ports
docker compose up -d postgres adminer
./scripts/import-databases.sh   # load postgres_dev / postgres_test
docker compose up -d analysis   # Jupyter at http://localhost:8888
```

## Verify outputs match after re-running
```bash
docker compose run --rm verify original verify
```

Tear down with `docker compose down -v`. Follow `replication/quick-start.sh` for the full guided path.
````

- [ ] **Step 2: Verify and commit Phase 3**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
for s in db-query-patterns gradle-build-triage python-analysis-conventions packaging-artifact; do test -f ".omp/skills/$s/SKILL.md" || { echo "MISSING $s"; exit 1; }; done
git add .omp/skills && git commit -q -m "feat: add Teralizer project skills (db, gradle, python, packaging)" && echo committed
```
Expected: `committed`

---

## Phase 4: Read-only PostgreSQL MCP

### Task 4.1: Create the `teralizer_ro` read-only role

**Files:** none (DB-side change).

- [ ] **Step 1: Ensure the DB is running**

Run: `cd /Users/joaichberger/Projects/test-generalization-dev && ./gradlew startPostgres && docker ps --filter name=postgres-teralizer --format '{{.Names}}'`
Expected: `postgres-teralizer`

- [ ] **Step 2: Create the role and grant read-only on each DB**

Run (replace `CHANGE_ME` with a chosen password):
```bash
docker exec -i postgres-teralizer psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c \
  "DO \$\$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='teralizer_ro') THEN CREATE ROLE teralizer_ro LOGIN PASSWORD 'CHANGE_ME'; END IF; END \$\$;"
for db in postgres_dev postgres_test postgres_timeout_retry; do
  docker exec -i postgres-teralizer psql -U postgres -d "$db" -v ON_ERROR_STOP=1 <<SQL
GRANT CONNECT ON DATABASE $db TO teralizer_ro;
GRANT USAGE ON SCHEMA public TO teralizer_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO teralizer_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO teralizer_ro;
SQL
done
echo done
```
Expected: `done` (no errors).

- [ ] **Step 3: Verify the role is read-only**

Run:
```bash
docker exec -i postgres-teralizer psql "postgresql://teralizer_ro:CHANGE_ME@localhost:5432/postgres_dev" -c "SELECT count(*) FROM filter_result;" && \
docker exec -i postgres-teralizer psql "postgresql://teralizer_ro:CHANGE_ME@localhost:5432/postgres_dev" -c "CREATE TABLE _ro_probe(x int);" 2>&1 | grep -qi "permission denied" && echo "READ-ONLY CONFIRMED"
```
Expected: a row count, then `READ-ONLY CONFIRMED` (the write is rejected).

### Task 4.2: Configure the MCP server

**Files:**
- Create: `.omp/mcp.json`

- [ ] **Step 1: Write the MCP config (DSN via env; no secrets committed)**

Create `.omp/mcp.json`:
```json
{
  "$schema": "https://raw.githubusercontent.com/can1357/oh-my-pi/main/packages/coding-agent/src/config/mcp-schema.json",
  "mcpServers": {
    "teralizer-db": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@bytebase/dbhub", "--transport", "stdio", "--readonly", "--dsn", "${TERALIZER_DB_DSN}"]
    }
  }
}
```

- [ ] **Step 2: Set the DSN env var (NOT committed)**

Add to your shell profile / local env (contains the password — never commit):
```bash
export TERALIZER_DB_DSN="postgresql://teralizer_ro:CHANGE_ME@localhost:5432/postgres_dev"
```
Point the DSN at `postgres_test` / `postgres_timeout_retry` to query those datasets.

- [ ] **Step 3: Verify the server is reachable, then commit the config**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
uv run --no-project python -m json.tool .omp/mcp.json >/dev/null && echo "json ok"
TERALIZER_DB_DSN="postgresql://teralizer_ro:CHANGE_ME@localhost:5432/postgres_dev" timeout 60 npx -y @bytebase/dbhub --transport stdio --readonly --dsn "$TERALIZER_DB_DSN" --help >/dev/null 2>&1 && echo "dbhub runnable" || echo "verify dbhub package name/flags"
git add .omp/mcp.json && git commit -q -m "feat: add read-only PostgreSQL MCP (teralizer-db) via dbhub" && echo committed
```
Expected: `json ok`; `dbhub runnable` (or the verify note if the package/flags differ — adjust then); `committed`. In OMP, run `/mcp test teralizer-db` next session to confirm the live connection.

---

## Phase 5: Build/test subagent (+ optional memory)

### Task 5.1: Test/build runner subagent

**Files:**
- Create: `.omp/agents/test-runner.md`

- [ ] **Step 1: Write the agent definition**

```markdown
---
name: test-runner
description: Runs the Gradle build / Python tests and returns only failures and the minimal context to fix them. Use to keep verbose build/test logs out of the main thread.
tools: bash, read, search, find
---

You run builds/tests for the Teralizer repo and report back compactly.

- Java: `./gradlew build` (or the requested task with `--stacktrace`).
- Python: `uv run --directory analysis pytest` and/or `uv run --directory analysis python validate.py --changed`.

Return ONLY: pass/fail per suite, the failing test/task names, and the 5–15 most relevant log lines
per failure (file:line + message). Do not paste full logs. Do not edit files. If everything passes,
say so in one line.
```

- [ ] **Step 2: Verify and commit**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
test -f .omp/agents/test-runner.md && git add .omp/agents && git commit -q -m "feat: add test-runner subagent for isolated build/test output" && echo committed
```
Expected: `committed`

### Task 5.2 (optional): Enable local auto-memory

**Files:**
- Create: `.omp/config.yml`

- [ ] **Step 1: Opt into the local memory backend (project scope)**

Create `.omp/config.yml`:
```yaml
memory:
  backend: local
```

- [ ] **Step 2: Commit**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
git add .omp/config.yml && git commit -q -m "chore: enable local auto-memory for this project" && echo committed
```
Expected: `committed`. (Skip this task if you don't want cross-session memory.)

---

## Phase 6: Verification

### Task 6.1: Confirm the full surface

- [ ] **Step 1: Verify files, symlink, and no stale refs**

Run:
```bash
cd /Users/joaichberger/Projects/test-generalization-dev
echo "--- context ---"; ls -l AGENTS.md CLAUDE.md GEMINI.md
echo "--- not ignored ---"; git check-ignore AGENTS.md CLAUDE.md GEMINI.md || echo "none ignored"
echo "--- omp tree ---"; find .omp -type f | sort
echo "--- stale ref scan ---"; grep -rnE 'run\.sh|docs/evaluation\.md|TODO\.md' AGENTS.md .omp 2>/dev/null || echo "clean"
echo "--- git clean ---"; git status --short
```
Expected: `CLAUDE.md`/`GEMINI.md` → `AGENTS.md` symlinks, none ignored; `.omp/{rules,RULES.md,skills,agents,mcp.json,config.yml}` present; `clean`; empty git status.

- [ ] **Step 2: Live MCP check (next OMP session)**

In a fresh OMP session in this repo: `/mcp test teralizer-db` → expect a successful read-only connection; the new skills/rules appear in discovery.

---

## Self-Review (completed by plan author)

- **Spec §8.1 coverage:** tracked `AGENTS.md` + `CLAUDE.md` symlink + stale-ref removal (Phase 1); `python`/`db` path-scoped rules + sticky `RULES.md` (Phase 2); `db-query-patterns`/`gradle-build-triage`/`python-analysis-conventions`/`packaging-artifact` skills (Phase 3); read-only Postgres MCP against `postgres-teralizer` with a new `teralizer_ro` role (Phase 4); build/test subagent + optional memory (Phase 5). Covered.
- **Deferred (noted, not silently dropped):** the optional OMP TS enforcement extension (block destructive bash / protect generated paths) — pre-commit already gates lint/types; the TS guard is a separate small follow-up if desired. GitHub PAT rotation: declined by user (spec §12).
- **Placeholder scan:** none. `CHANGE_ME` is an explicit secret the operator sets; `${TERALIZER_DB_DSN}` is real OMP env expansion.
- **Consistency:** skill name `db-query-patterns` referenced in `AGENTS.md`, the `db` rule, and defined in Task 3.1; MCP server `teralizer-db` + role `teralizer_ro` consistent across Phase 4.
- **Caveats:** `CLAUDE.md` stays gitignored (it's a symlink); `AGENTS.md` is newly tracked (decision flagged in header). `@bytebase/dbhub` exact flags/package verified at Task 4.2 Step 3 — adjust if the CLI differs. OMP discovers `.omp/*` on next session start.
```
