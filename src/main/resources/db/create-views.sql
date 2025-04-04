DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant;
DROP MATERIALIZED VIEW mv_mutation_status_changes;
DROP MATERIALIZED VIEW mv_mutation_variant_comparison;
DROP MATERIALIZED VIEW mv_pit_mutation_report;

CREATE MATERIALIZED VIEW mv_pit_mutation_report AS
SELECT
    id,
    project_id,
    dense_rank() OVER (
        ORDER BY
            project_id,
            mutated_class,
            line_number,
            mutator,
            indexes,
            blocks,
            description
    ) AS mutation_id,
    killing_test_id,
    killing_generalization_id,
    step,
    stage,
    variant_name(stage, variant) AS variant,
    variant_order(variant_name(stage, variant)) AS variant_order,
    is_detected,
    status,
    number_of_tests_run,
    source_file,
    mutated_class,
    mutated_method,
    method_description,
    line_number,
    mutator,
    indexes,
    blocks,
    killing_package_name,
    killing_class_name,
    killing_method_name,
    description
FROM
    pit_mutation_report
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_report_id ON mv_pit_mutation_report (id);

CREATE INDEX idx_mv_pit_mutation_report_project_id ON mv_pit_mutation_report (project_id);
CREATE INDEX idx_mv_pit_mutation_report_killing_test_id ON mv_pit_mutation_report (killing_test_id);
CREATE INDEX idx_mv_pit_mutation_report_killing_generalization_id ON mv_pit_mutation_report (killing_generalization_id);

CREATE INDEX idx_mv_pit_mutation_report_step ON mv_pit_mutation_report (step);
CREATE INDEX idx_mv_pit_mutation_report_stage ON mv_pit_mutation_report (stage);
CREATE INDEX idx_mv_pit_mutation_report_variant ON mv_pit_mutation_report (variant);
CREATE INDEX idx_mv_pit_mutation_report_variant_order ON mv_pit_mutation_report (variant_order);

CREATE INDEX idx_mv_pit_mutation_report_is_detected ON mv_pit_mutation_report (is_detected);
CREATE INDEX idx_mv_pit_mutation_report_mutated_class ON mv_pit_mutation_report (mutated_class);
CREATE INDEX idx_mv_pit_mutation_report_mutated_method ON mv_pit_mutation_report (mutated_method);

CREATE MATERIALIZED VIEW mv_mutation_variant_comparison AS
SELECT
    a.mutation_id,
    a.id AS a_report_id,
    a.variant AS a_variant,
    a.status AS a_status,
    a.is_detected AS a_is_detected,
    b.id AS b_report_id,
    b.variant AS b_variant,
    b.status AS b_status,
    b.is_detected AS b_is_detected
FROM
    mv_pit_mutation_report a
JOIN
    mv_pit_mutation_report b
ON
    a.mutation_id = b.mutation_id AND
    a.variant != b.variant
ORDER BY a.id, b.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_mutation_variant_comparison_a_report_id_b_report_id ON mv_mutation_variant_comparison (a_report_id, b_report_id);

CREATE INDEX idx_mv_mutation_variant_comparison_mutation_id ON mv_mutation_variant_comparison (mutation_id);
CREATE INDEX idx_mv_mutation_variant_comparison_a_report_id ON mv_mutation_variant_comparison (a_report_id);
CREATE INDEX idx_mv_mutation_variant_comparison_b_report_id ON mv_mutation_variant_comparison (b_report_id);

CREATE INDEX idx_mv_mutation_variant_comparison_a_status ON mv_mutation_variant_comparison (a_status);
CREATE INDEX idx_mv_mutation_variant_comparison_b_status ON mv_mutation_variant_comparison (b_status);
CREATE INDEX idx_mv_mutation_variant_comparison_a_is_detected ON mv_mutation_variant_comparison (a_is_detected);
CREATE INDEX idx_mv_mutation_variant_comparison_b_is_detected ON mv_mutation_variant_comparison (b_is_detected);

CREATE MATERIALIZED VIEW mv_mutation_status_changes AS
SELECT
    a_report_id,
    b_report_id,
    a_variant,
    b_variant,
    a_status,
    b_status,
    a_is_detected,
    b_is_detected,
    ra.number_of_tests_run AS a_number_of_tests_run,
    rb.number_of_tests_run AS b_number_of_tests_run,
    ra.source_file,
    simple_name(ra.mutator) AS mutator,
    ra.description,
    COALESCE(kt.test_method_qualified_name, t.test_method_qualified_name) AS test_method,
    a.input_specification_path,
    COALESCE(kg.project_id, kt.project_id) AS killing_project_id,
    COALESCE(kg.test_id, kt.id) AS killing_test_id,
    kg.assertion_id AS killing_assertion_id,
    kg.id AS killing_generalization_id,
    kg.class_qualified_name AS killing_class,
    kg.total_constraint_count AS killing_total_constraint_count,
    kg.used_constraint_count AS killing_used_constraint_count,
    kg.line_count AS killing_line_count,
    tr.runtime AS killing_runtime
FROM mv_mutation_variant_comparison c
JOIN mv_pit_mutation_report ra ON c.a_report_id = ra.id
JOIN mv_pit_mutation_report rb ON c.b_report_id = rb.id
LEFT JOIN test kt ON rb.killing_test_id = kt.id
LEFT JOIN generalization kg ON rb.killing_generalization_id = kg.id
LEFT JOIN test t ON t.id = kg.test_id
LEFT JOIN assertion a ON a.id = kg.assertion_id
LEFT JOIN junit_test_report tr ON kg.id = tr.generalization_id
WHERE c.a_variant = 'INITIAL' AND c.b_variant != 'ORIGINAL' AND c.a_status != c.b_status
ORDER BY b_is_detected, b_status = 'KILLED', kg.id IS NOT NULL, ra.id, rb.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_mutation_status_changes_a_report_id_b_report_id ON mv_mutation_status_changes (a_report_id, b_report_id);

CREATE MATERIALIZED VIEW mv_mutation_results_by_variant AS
WITH base_data AS (
    SELECT
        variant_name(pmr.stage, pmr.variant) AS variant,
        COUNT(*) AS total,
        COUNT(*) - COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS covered,
        COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS uncovered,
        COUNT(CASE WHEN pmr.status = 'SURVIVED' THEN 1 END) AS survived,
        SUM(pmr.is_detected::int) AS detected,
        COUNT(CASE WHEN pmr.status = 'KILLED' THEN 1 END) AS killed,
        COUNT(CASE WHEN pmr.status = 'TIMED_OUT' THEN 1 END) AS timed_out,
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error
    FROM
        pit_mutation_report pmr
    GROUP BY
        pmr.stage, pmr.variant
),
percentages AS (
    SELECT
        variant,
        total,
        covered,
        uncovered,
        survived,
        detected,
        killed,
        timed_out,
        memory_error,
        -- Calculate percentages
        ROUND((covered * 100.0 / total), 2) AS covered_pct,
        ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
        ROUND((survived * 100.0 / total), 2) AS survived_pct,
        ROUND((detected * 100.0 / total), 2) AS detected_pct,
        ROUND((killed * 100.0 / total), 2) AS killed_pct,
        ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
        ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
        --
        ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
        ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
        ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
        ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
        ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct
    FROM
        base_data
)
-- Final result with differences
SELECT
    s.variant,
    s.total,
    s.covered,
    s.uncovered,
    s.survived,
    s.detected,
    s.killed,
    s.timed_out,
    s.memory_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff
FROM
    percentages s,
    percentages b
WHERE
    b.variant = 'INITIAL'
ORDER BY
    variant_order(s.variant)
WITH DATA;

CREATE MATERIALIZED VIEW mv_mutation_results_by_variant_mutator AS
WITH base_data AS (
    SELECT
        variant_name(pmr.stage, pmr.variant) AS variant,
        SUBSTRING(mutator FROM '([^.]+)$') AS mutator,
        COUNT(*) AS total,
        COUNT(*) - COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS covered,
        COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS uncovered,
        COUNT(CASE WHEN pmr.status = 'SURVIVED' THEN 1 END) AS survived,
        SUM(pmr.is_detected::int) AS detected,
        COUNT(CASE WHEN pmr.status = 'KILLED' THEN 1 END) AS killed,
        COUNT(CASE WHEN pmr.status = 'TIMED_OUT' THEN 1 END) AS timed_out,
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error
    FROM
        pit_mutation_report pmr
    GROUP BY
        pmr.stage, pmr.variant, pmr.mutator
),
    percentages AS (
        SELECT
            variant,
            mutator,
            total,
            covered,
            uncovered,
            survived,
            detected,
            killed,
            timed_out,
            memory_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct
        FROM
            base_data
    )
-- Final result with differences
SELECT
    s.variant,
    s.mutator,
    s.total,
    s.covered,
    s.uncovered,
    s.survived,
    s.detected,
    s.killed,
    s.timed_out,
    s.memory_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff
FROM
    percentages s,
    percentages b
WHERE
      b.variant = 'INITIAL'
  AND s.mutator = b.mutator
ORDER BY
    variant_order(s.variant), s.total DESC, s.mutator
WITH DATA;

CREATE MATERIALIZED VIEW mv_mutation_results_by_project_variant AS
WITH base_data AS (
    SELECT
        pmr.project_id,
        project_name(pmr.project_id) AS project_name,
        variant_name(pmr.stage, pmr.variant) AS variant,
        COUNT(*) AS total,
        COUNT(*) - COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS covered,
        COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS uncovered,
        COUNT(CASE WHEN pmr.status = 'SURVIVED' THEN 1 END) AS survived,
        SUM(pmr.is_detected::int) AS detected,
        COUNT(CASE WHEN pmr.status = 'KILLED' THEN 1 END) AS killed,
        COUNT(CASE WHEN pmr.status = 'TIMED_OUT' THEN 1 END) AS timed_out,
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error
    FROM
        pit_mutation_report pmr
    GROUP BY
        pmr.project_id, pmr.stage, pmr.variant
),
    percentages AS (
        SELECT
            project_id,
            project_name,
            variant,
            total,
            covered,
            uncovered,
            survived,
            detected,
            killed,
            timed_out,
            memory_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct
        FROM
            base_data
    )
-- Final result with differences
SELECT
    s.project_id,
    s.project_name,
    s.variant,
    s.total,
    s.covered,
    s.uncovered,
    s.survived,
    s.detected,
    s.killed,
    s.timed_out,
    s.memory_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff
FROM
    percentages s,
    percentages b
WHERE
      b.variant = 'INITIAL'
  AND s.project_id = b.project_id
ORDER BY
    s.project_id, variant_order(s.variant)
WITH DATA;

CREATE MATERIALIZED VIEW mv_mutation_results_by_project_variant_mutator AS
WITH base_data AS (
    SELECT
        pmr.project_id,
        project_name(pmr.project_id) AS project_name,
        variant_name(pmr.stage, pmr.variant) AS variant,
        SUBSTRING(mutator FROM '([^.]+)$') AS mutator,
        COUNT(*) AS total,
        COUNT(*) - COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS covered,
        COUNT(CASE WHEN pmr.status = 'NO_COVERAGE' THEN 1 END) AS uncovered,
        COUNT(CASE WHEN pmr.status = 'SURVIVED' THEN 1 END) AS survived,
        SUM(pmr.is_detected::int) AS detected,
        COUNT(CASE WHEN pmr.status = 'KILLED' THEN 1 END) AS killed,
        COUNT(CASE WHEN pmr.status = 'TIMED_OUT' THEN 1 END) AS timed_out,
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error
    FROM
        pit_mutation_report pmr
    GROUP BY
        pmr.project_id, pmr.stage, pmr.variant, pmr.mutator
),
    percentages AS (
        SELECT
            project_id,
            project_name,
            variant,
            mutator,
            total,
            covered,
            uncovered,
            survived,
            detected,
            killed,
            timed_out,
            memory_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct
        FROM
            base_data
    )
-- Final result with differences
SELECT
    s.project_id,
    s.project_name,
    s.variant,
    s.mutator,
    s.total,
    s.covered,
    s.uncovered,
    s.survived,
    s.detected,
    s.killed,
    s.timed_out,
    s.memory_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff
FROM
    percentages s,
    percentages b
WHERE
      b.variant = 'INITIAL'
  AND s.project_id = b.project_id
  AND s.mutator = b.mutator
ORDER BY
    s.project_id, variant_order(s.variant), s.total DESC, s.mutator
WITH DATA;
