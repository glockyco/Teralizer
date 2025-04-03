DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant;

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
