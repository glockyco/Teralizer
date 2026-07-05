---
title: Generation-Coverage Telemetry
type: plan
status: draft
created: 2026-07-02
parent: 2026-06-26-teralizer-overview
---

# Generation-Coverage Telemetry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add pipeline-time generation-coverage telemetry — per-clause shape + consumed/residual status and per-parameter symbolic-spec presence — so analysis can rank which clause shapes fall to the residual filter, which parameter types are entry-gaps, and which SPF gaps correlate with lost generalizations.

**Architecture:** A `ModelFolder<Shape>` computes canonical shape keys from `Model` expression trees (compile-strict — a new node kind without a shape case is a build break). Two new DB tables (`generation_clause`, `generation_parameter`) are populated at generation time in `TestGeneralizationTask`, where `InputGenerationPlan` already tracks consumed/residual clause IDs. A post-hoc `generation_coverage.py` analysis module reads the populated tables and produces the ranked "next type / next recipe / next SPF fix" lists. Design: `2026-06-28-generation-coverage-telemetry`.

**Tech stack:** Java 8, `ModelFolder<T>`, jOOQ generated records, PostgreSQL, Python (pandas, SQLAlchemy).

## File map

**Create:**
- `src/main/java/teralizer/domain/ShapeFolder.java` — `ModelFolder<String>` that produces canonical shape keys.
- `src/test/java/teralizer/domain/ShapeFolderTest.java` — unit tests for every node kind.
- `src/main/resources/db/create-tables.sql` — append `generation_clause` + `generation_parameter` table definitions (additive, nullable on old DBs).
- `analysis/src/teralizer/generation_coverage.py` — analysis module (sibling to `applicability_priorities.py`).
- `analysis/tests/test_generation_coverage.py` — analysis module tests.

**Modify:**
- `src/main/java/teralizer/processing/task/TestGeneralizationTask.java:348-354` — after writing constraint counts, populate `generation_clause` + `generation_parameter` rows from `InputGenerationPlan`.

---

## Task 1: `ShapeFolder` — canonical shape keys

**Files:**
- Create: `src/main/java/teralizer/domain/ShapeFolder.java`
- Create: `src/test/java/teralizer/domain/ShapeFolderTest.java`

- [x] **Step 1: Write failing tests** covering every node kind.

```java
package teralizer.domain;

import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ShapeFolderTest {

    @Test
    void variableShapesByDomain() {
        assertEquals("Variable:INTEGER", new Variable("x", TypeDomain.INTEGER).fold(new ShapeFolder()));
        assertEquals("Variable:STRING", new Variable("s", TypeDomain.STRING).fold(new ShapeFolder()));
    }

    @Test
    void constantShapesByDomainNotValue() {
        assertEquals("Constant:INTEGER", new Constant(42L, TypeDomain.INTEGER).fold(new ShapeFolder()));
        assertEquals("Constant:STRING", new Constant("foo", TypeDomain.STRING).fold(new ShapeFolder()));
        assertEquals("Constant:REAL", new Constant(3.14, TypeDomain.REAL).fold(new ShapeFolder()));
    }

    @Test
    void operationShapesByOperatorAndOperands() {
        Operation op = new Operation(
            new Variable("a", TypeDomain.INTEGER),
            Operator.MOD,
            new Constant(2L, TypeDomain.INTEGER));
        assertEquals("MOD(Variable:INTEGER,Constant:INTEGER)", op.fold(new ShapeFolder()));
    }

    @Test
    void invocationShapesByMethodAndArgs() {
        Invocation inv = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "startsWith",
            Collections.singletonList(new Constant("x", TypeDomain.STRING)));
        assertEquals("startsWith(Variable:STRING,Constant:STRING)", inv.fold(new ShapeFolder()));
    }

    @Test
    void staticInvocationShapesWithQualifier() {
        Invocation inv = new Invocation(
            null,
            "java.lang.Math",
            "sqrt",
            Collections.singletonList(new Variable("x", TypeDomain.REAL)));
        assertEquals("sqrt(Variable:REAL)", inv.fold(new ShapeFolder()));
    }

    @Test
    void notShapesOperand() {
        Not not = new Not(new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING))));
        assertEquals("!(equals(Variable:STRING,Constant:STRING))", not.fold(new ShapeFolder()));
    }

    @Test
    void nestedOperationInsideInvocation() {
        Invocation inv = new Invocation(
            new Variable("s", TypeDomain.STRING),
            null,
            "equals",
            Collections.singletonList(new Constant("foo", TypeDomain.STRING)));
        Operation op = new Operation(inv, Operator.EQ, new Constant(true, TypeDomain.BOOLEAN));
        assertEquals("EQ(equals(Variable:STRING,Constant:STRING),Constant:BOOLEAN)", op.fold(new ShapeFolder()));
    }
}
```

- [x] **Step 2: Run tests, expect FAIL** (compilation error — `ShapeFolder` absent).
  Run: `./gradlew test --tests 'teralizer.domain.ShapeFolderTest'`

- [x] **Step 3: Implement `ShapeFolder`.**

```java
package teralizer.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes a canonical shape key from a {@link Model} expression tree. Literals are
 * stripped — only the {@link TypeDomain} matters, not the value. The key is used for
 * generation-coverage telemetry: grouping clauses by shape to rank which shapes fall
 * to the residual filter.
 *
 * <p>Compile-strict: every node kind has an abstract hook in {@link ModelFolder}, so a
 * new node kind without a shape case is a build break.
 */
public class ShapeFolder extends ModelFolder<String> {

    @Override
    public String fold(Constant constant) {
        return "Constant:" + constant.domain.name();
    }

    @Override
    public String fold(Variable variable) {
        return "Variable:" + variable.domain.name();
    }

    @Override
    public String fold(ArrayExpression expression) {
        return "Array:" + expression.elementType;
    }

    @Override
    public String fold(ArrayElementExpression expression, String elementSelector) {
        return "ArrayElement:" + expression.elementType + "[" + elementSelector + "]";
    }

    @Override
    public String fold(Invocation invocation, String receiver, List<String> args) {
        String argList = args.stream().collect(Collectors.joining(","));
        return invocation.method + "(" + argList + ")";
    }

    @Override
    public String fold(Not not, String operand) {
        return "!(" + operand + ")";
    }

    @Override
    public String fold(Operation operation, String left, String right) {
        return operation.op.name() + "(" + left + "," + right + ")";
    }

    @Override
    public String fold(Operator operator) {
        return operator.name();
    }

    @Override
    public String fold(Error error) {
        return "Error";
    }

    @Override
    public String fold(ExceptionModel exceptionModel) {
        return "Exception:" + exceptionModel.name;
    }
}
```

- [x] **Step 4: Run tests, expect PASS.**
  Run: `./gradlew test --tests 'teralizer.domain.ShapeFolderTest'`

- [x] **Step 5: Commit.** `feat: add ShapeFolder for canonical model shape keys`

---

## Task 2: DB schema — `generation_clause` + `generation_parameter`

**Files:**
- Modify: `src/main/resources/db/create-tables.sql` (append at end)

- [x] **Step 1: Append the two table definitions.**

```sql
-- Generation-coverage telemetry: per-clause shape + consumed status.
-- Populated at generation time from InputGenerationPlan.
CREATE TABLE generation_clause
(
    id                 BIGSERIAL PRIMARY KEY,
    generalization_id  BIGINT  NOT NULL,
    parameter_name     TEXT    NOT NULL,
    type_domain        TEXT    NOT NULL,
    shape              TEXT    NOT NULL,
    consumed           BOOLEAN NOT NULL,

    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);
CREATE INDEX idx_generation_clause_generalization_id ON generation_clause (generalization_id);
CREATE INDEX idx_generation_clause_shape ON generation_clause (shape);
CREATE INDEX idx_generation_clause_consumed ON generation_clause (consumed);

-- Generation-coverage telemetry: per-parameter symbolic-spec presence + representation.
-- Not derivable post-hoc; populated at pipeline time.
CREATE TABLE generation_parameter
(
    id                      BIGSERIAL PRIMARY KEY,
    generalization_id       BIGINT  NOT NULL,
    name                    TEXT    NOT NULL,
    declared_type           TEXT    NOT NULL,
    type_domain             TEXT    NOT NULL,
    symbolic_spec_present   BOOLEAN NOT NULL,
    representation          TEXT    NOT NULL,

    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);
CREATE INDEX idx_generation_parameter_generalization_id ON generation_parameter (generalization_id);
CREATE INDEX idx_generation_parameter_type_domain ON generation_parameter (type_domain);
```

- [x] **Step 2: Regenerate jOOQ from the DDL and verify.** `postgres_dev` is PROTECTED — never
  recreate it for schema work. Run `scripts/regenerate-jooq.sh` (creates a throwaway
  `teralizer_codegen` DB from `create-tables.sql`, regenerates the jOOQ sources, drops the DB;
  read the script header first). Verify `GenerationClause`/`GenerationParameter` records exist
  in the regenerated sources and the build compiles.

- [x] **Step 3: Commit.** `feat(db): add generation_clause and generation_parameter tables`
  (DDL + regenerated jOOQ sources in one commit).

---

## Task 3: Pipeline population — write telemetry rows in `TestGeneralizationTask`

**Files:**
- Modify: `src/main/java/teralizer/processing/task/TestGeneralizationTask.java` — the
  `IMPROVED` branch where `InputGenerationPlan` is available (currently the
  `setTotalConstraintCount`/`setUsedConstraintCount` block around lines 246–250; re-locate
  by symbol, not line number).

- [ ] **Step 1: Read the current `IMPROVED` branch** to confirm the insertion point — after
  `this.generalizationRecord.store()` in the constraint-count block.

- [ ] **Step 2: Write the telemetry population code.** After the constraint counts are stored, iterate `inputGenerationPlan`:
  - For each `ConstraintClause` in the plan's clause list, compute the shape via `ShapeFolder`, determine `consumed` from `inputGenerationPlan.getConsumedClauseIds()`, and determine the primary `parameter_name` + `type_domain` from the variables referenced in the clause expression (via `VariableNameCollector` — reuse the existing pattern from `ConstraintClauses.from`).
  - For each `ParameterGenerationPlan` in the plan, record `symbolic_spec_present` (true if the plan's `recipe` is non-null and non-default) and `representation` (`encoded` if consumed clauses exist, `residual` if the parameter has residual clauses, `none` if no symbolic spec was present).

  Task 2 already regenerated the jOOQ sources, so use the generated `GenerationClauseRecord` /
  `GenerationParameterRecord` directly — no raw-SQL fallback.

- [ ] **Step 3: Run the pipeline** on a small project and verify rows appear. Every run names
  its DB explicitly (run-target redesign):
  `./gradlew run -Dteralizer.config=project-configs/jarvis-scoreboard/commons-lang-3.5.conf -Dteralizer.database.name=postgres_gencov_verify`
  (scratch DB, create before / drop after), then query:
  `docker exec -i postgres-teralizer psql -U postgres -d postgres_gencov_verify -c "SELECT shape, consumed, count(*) FROM generation_clause gc JOIN generalization g ON gc.generalization_id = g.id WHERE g.variant = 'IMPROVED_100_TRIES' GROUP BY shape, consumed ORDER BY count(*) DESC LIMIT 20"`
  Expected: rows with shapes like `EQ(Variable:INTEGER,Constant:INTEGER)` and consumed=true/false.

- [ ] **Step 4: Commit.** `feat(task): populate generation-coverage telemetry at generation time`

---

## Task 4: Analysis module — `generation_coverage.py`

**Files:**
- Create: `analysis/src/teralizer/generation_coverage.py`
- Create: `analysis/tests/test_generation_coverage.py`

- [ ] **Step 1: Write failing tests** using an in-memory SQLite DB with the two new tables.

```python
"""Tests for generation_coverage analysis module."""
import pandas as pd
from sqlalchemy import create_engine, text


def _setup_db(conn):
    conn.execute(text("""
        CREATE TABLE generalization (
            id INTEGER PRIMARY KEY, project_id INTEGER NOT NULL,
            test_id INTEGER NOT NULL, assertion_id INTEGER NOT NULL,
            variant TEXT NOT NULL, file_path TEXT NOT NULL,
            class_qualified_name TEXT NOT NULL, method_qualified_name TEXT NOT NULL,
            package_name TEXT NOT NULL, class_name TEXT NOT NULL, method_name TEXT NOT NULL,
            total_constraint_count INTEGER, used_constraint_count INTEGER,
            line_count INTEGER NOT NULL, is_included BOOLEAN NOT NULL,
            exclusion_info TEXT
        )"""))
    conn.execute(text("""
        CREATE TABLE generation_clause (
            id INTEGER PRIMARY KEY, generalization_id INTEGER NOT NULL,
            parameter_name TEXT NOT NULL, type_domain TEXT NOT NULL,
            shape TEXT NOT NULL, consumed BOOLEAN NOT NULL
        )"""))
    conn.execute(text("""
        CREATE TABLE generation_parameter (
            id INTEGER PRIMARY KEY, generalization_id INTEGER NOT NULL,
            name TEXT NOT NULL, declared_type TEXT NOT NULL, type_domain TEXT NOT NULL,
            symbolic_spec_present BOOLEAN NOT NULL, representation TEXT NOT NULL
        )"""))
    conn.execute(text("""
        CREATE TABLE project (id INTEGER PRIMARY KEY, name TEXT NOT NULL)
    """))
    conn.execute(text("""
        CREATE TABLE filter_result (
            id INTEGER PRIMARY KEY, project_id INTEGER NOT NULL,
            filter_name TEXT NOT NULL, decision TEXT NOT NULL, reason TEXT NOT NULL
        )"""))
    conn.execute(text("""
        INSERT INTO generalization VALUES
            (1, 1, 1, 1, 'IMPROVED_100_TRIES', 'f', 'c', 'm', 'p', 'c', 'm', 3, 2, 10, TRUE, NULL)
    """))
    conn.execute(text("""
        INSERT INTO generation_clause VALUES
            (1, 1, 'x', 'INTEGER', 'LT(Variable:INTEGER,Constant:INTEGER)', TRUE),
            (2, 1, 'x', 'INTEGER', 'MOD(Variable:INTEGER,Constant:INTEGER)', FALSE),
            (3, 1, 'x', 'INTEGER', 'EQ(Variable:INTEGER,Constant:INTEGER)', TRUE)
    """))
    conn.execute(text("""
        INSERT INTO generation_parameter VALUES
            (1, 1, 'x', 'int', 'INTEGER', TRUE, 'encoded')
    """))
    conn.execute(text("""
        INSERT INTO project VALUES (1, 'test-project')
    """))
    conn.commit()


def test_top_residual_shapes(conn):
    from teralizer.generation_coverage import get_top_residual_shapes
    result = get_top_residual_shapes(conn)
    assert len(result) == 1
    assert result.iloc[0]["shape"] == "MOD(Variable:INTEGER,Constant:INTEGER)"
    assert result.iloc[0]["count"] == 1


def test_per_domain_coverage(conn):
    from teralizer.generation_coverage import get_per_domain_coverage
    result = get_per_domain_coverage(conn)
    row = result[result["type_domain"] == "INTEGER"].iloc[0]
    assert row["consumed"] == 2
    assert row["residual"] == 1


def test_parameter_representations(conn):
    from teralizer.generation_coverage import get_parameter_representations
    result = get_parameter_representations(conn)
    row = result[result["representation"] == "encoded"].iloc[0]
    assert row["count"] == 1
```

- [ ] **Step 2: Run tests, expect FAIL** (module absent).
  Run: `uv run --directory analysis pytest analysis/tests/test_generation_coverage.py -v`

- [ ] **Step 3: Implement `generation_coverage.py`.**

```python
"""Generation-coverage analysis: which clause shapes fall to the residual filter,
which parameter types are entry-gaps, and which SPF gaps correlate with lost
generalizations.

Sibling to ``applicability_priorities.py`` (which keeps the front-end funnel).
This module reads the ``generation_clause`` and ``generation_parameter`` tables
populated at pipeline time by ``TestGeneralizationTask``.
"""
from __future__ import annotations

import pandas as pd
from sqlalchemy import Connection


def get_top_residual_shapes(conn: Connection) -> pd.DataFrame:
    """Top clause shapes that fell to the residual filter (consumed = false).

    Columns: shape, count
    """
    return pd.read_sql(
        """SELECT shape, count(*) AS count
           FROM generation_clause
           WHERE consumed = false
           GROUP BY shape
           ORDER BY count DESC""",
        conn,
    )


def get_per_domain_coverage(conn: Connection) -> pd.DataFrame:
    """Consumed vs. residual clause counts per TypeDomain.

    Columns: type_domain, consumed, residual
    """
    return pd.read_sql(
        """SELECT type_domain,
                  sum(CASE WHEN consumed THEN 1 ELSE 0 END) AS consumed,
                  sum(CASE WHEN NOT consumed THEN 1 ELSE 0 END) AS residual
           FROM generation_clause
           GROUP BY type_domain
           ORDER BY type_domain""",
        conn,
    )


def get_parameter_representations(conn: Connection) -> pd.DataFrame:
    """Per-parameter representation breakdown (encoded / residual / none).

    Columns: representation, count
    """
    return pd.read_sql(
        """SELECT representation, count(*) AS count
           FROM generation_parameter
           GROUP BY representation
           ORDER BY count DESC""",
        conn,
    )


def get_entry_gap_by_type(conn: Connection) -> pd.DataFrame:
    """Parameter types rejected at the filter gate (entry gaps).

    Columns: declared_type, type_domain, count
    """
    return pd.read_sql(
        """SELECT fr.reason AS declared_type, 'ENTRY_GAP' AS type_domain, count(*) AS count
           FROM filter_result fr
           WHERE fr.filter_name = 'ParameterTypeFilter'
             AND fr.decision = 'REJECT'
           GROUP BY fr.reason
           ORDER BY count DESC""",
        conn,
    )


def get_spf_gap_ranking(conn: Connection) -> pd.DataFrame:
    """SPF gaps (admitted but no symbolic spec) joined to exclusions.

    Columns: type_domain, count, exclusion_reason
    """
    return pd.read_sql(
        """SELECT gp.type_domain,
                  count(*) AS count,
                  g.exclusion_info AS exclusion_reason
           FROM generation_parameter gp
           JOIN generalization g ON g.id = gp.generalization_id
           WHERE gp.symbolic_spec_present = false
           GROUP BY gp.type_domain, g.exclusion_info
           ORDER BY count DESC""",
        conn,
    )


def main() -> None:
    """Print a generation-coverage summary from the configured DB."""
    from teralizer.config import get_engine
    with get_engine(validate=False).connect() as conn:
        print("=== Top residual shapes ===")
        print(get_top_residual_shapes(conn).to_string(index=False))
        print("\n=== Per-domain coverage ===")
        print(get_per_domain_coverage(conn).to_string(index=False))
        print("\n=== Parameter representations ===")
        print(get_parameter_representations(conn).to_string(index=False))
        print("\n=== Entry gaps by type ===")
        print(get_entry_gap_by_type(conn).to_string(index=False))
        print("\n=== SPF gap ranking ===")
        print(get_spf_gap_ranking(conn).to_string(index=False))
```

- [ ] **Step 4: Run tests, expect PASS.**
  Run: `uv run --directory analysis pytest analysis/tests/test_generation_coverage.py -v`

- [ ] **Step 5: Lint + format.**
  Run: `uv run --directory analysis ruff check --fix analysis/src/teralizer/generation_coverage.py analysis/tests/test_generation_coverage.py && uv run --directory analysis ruff format analysis/src/teralizer/generation_coverage.py analysis/tests/test_generation_coverage.py`

- [ ] **Step 6: Commit.** `feat(analysis): add generation_coverage analysis module`

---

## Task 5: Validate end-to-end

**Files:** none (empirical verification)

- [ ] **Step 1: Run the full test suite.**
  Run: `./gradlew test`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run the analysis module** against a DB with real data (e.g. after a JARVIS scoreboard run).
  Run: `uv run --directory analysis python -m teralizer.generation_coverage`
  Expected: non-empty tables with real shapes, consumed flags, and parameter representations.

- [ ] **Step 3: Validate.** `uv run --directory analysis python validate.py --changed`
  Expected: pass.

## Self-review

- **Spec coverage:** shape key (`ShapeFolder`) = Task 1; DB schema = Task 2; pipeline population = Task 3; analysis module = Task 4; validation = Task 5.
- **Compile-strict:** `ShapeFolder extends ModelFolder<String>` — all 10 hooks implemented; a new node kind is a build break.
- **Additive:** new tables are nullable on old DBs; old generalization rows simply have no telemetry rows.
- **No post-hoc re-planning:** `generation_clause.consumed` is written from `InputGenerationPlan.getConsumedClauseIds()` at generation time — the authoritative source.
