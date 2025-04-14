DROP MATERIALIZED VIEW mv_exclusions_jpf;
DROP MATERIALIZED VIEW mv_exclusions_filtering;
DROP MATERIALIZED VIEW mv_exclusions_all;
DROP MATERIALIZED VIEW mv_mutation_detection_comparison;
DROP MATERIALIZED VIEW mv_efficiency_comparison_evosuite_vs_teralizer;
DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_project_variant;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant_mutator;
DROP MATERIALIZED VIEW mv_mutation_results_by_variant;
DROP MATERIALIZED VIEW mv_generalization_effects;
DROP MATERIALIZED VIEW mv_mutation_status_changes;
DROP MATERIALIZED VIEW mv_mutation_variant_comparison;
DROP MATERIALIZED VIEW mv_pit_mutation_report_extension;
DROP MATERIALIZED VIEW mv_mutation_covering_generalizations;
DROP MATERIALIZED VIEW mv_mutation_covering_assertions;
DROP MATERIALIZED VIEW mv_mutation_covering_tests;
DROP MATERIALIZED VIEW mv_pit_mutation_coverage;
DROP MATERIALIZED VIEW mv_pit_mutation_report;
DROP MATERIALIZED VIEW mv_pit_coverage_report;
DROP MATERIALIZED VIEW mv_pit_mutation_report_location;
DROP MATERIALIZED VIEW mv_pit_coverage_report_location;
DROP MATERIALIZED VIEW mv_pit_location;
DROP MATERIALIZED VIEW mv_runtime_comparison_test_vs_generalization;
DROP MATERIALIZED VIEW mv_evosuite_runtime_pivoted;
DROP MATERIALIZED VIEW mv_evosuite_runtime;
DROP MATERIALIZED VIEW mv_generalization_extension;
DROP MATERIALIZED VIEW mv_assertion_extension;
DROP MATERIALIZED VIEW mv_test_extension;
DROP MATERIALIZED VIEW mv_variant;

CREATE EXTENSION IF NOT EXISTS tablefunc;

CREATE MATERIALIZED VIEW mv_variant AS
SELECT
    v.project_id,
    v.variant,
    min((v.config->'jqwik'->>'tries')::int) as tries,
    v.variant_order
FROM (
    SELECT
        p.id AS project_id,
        key as variant,
        variant_order(key) AS variant_order,
        value as config
    FROM
        project p,
        jsonb_each(CAST(configuration as jsonb)->'generalizations')
) as v
GROUP BY v.project_id, v.variant, v.variant_order
ORDER BY v.project_id, v.variant_order;

CREATE UNIQUE INDEX idx_mv_variant ON mv_variant (project_id, variant);

CREATE INDEX idx_mv_variant_project_id ON mv_variant (project_id);
CREATE INDEX idx_mv_variant_variant ON mv_variant (variant);
CREATE INDEX idx_mv_variant_variant_order ON mv_variant (variant_order);

CREATE MATERIALIZED VIEW mv_test_extension AS
SELECT
    t.id AS test_id,
    variant_name(r.stage, r.variant) AS variant,
    variant_order(variant_name(r.stage, r.variant)) AS variant_order,
    coalesce(count(r.id), 0) AS reports,
    sum(r.runtime) AS runtime
FROM test t
LEFT JOIN junit_test_report r ON t.id = r.test_id
GROUP BY t.id, r.stage, r.variant
ORDER BY t.id, variant_order(variant_name(r.stage, r.variant));

CREATE UNIQUE INDEX idx_mv_test_extension ON mv_test_extension (test_id, variant);

CREATE INDEX idx_mv_test_extension_test_id ON mv_test_extension (test_id);
CREATE INDEX idx_mv_test_extension_variant ON mv_test_extension (variant);
CREATE INDEX idx_mv_test_extension_variant_order ON mv_test_extension (variant_order);

SELECT
    'Test extension exists for every test.' AS test,
    (SELECT count(DISTINCT te.test_id) FROM mv_test_extension te) = (SELECT count(*) FROM test) AS result;

CREATE MATERIALIZED VIEW mv_assertion_extension AS
SELECT
    a.id AS assertion_id,
    te.variant,
    te.variant_order,
    min((a.input_model_statistics::json->>'javaSize')::int) AS model_java_size,
    min((a.input_model_statistics::json->>'operationCount')::int) AS model_operation_count
FROM assertion a
JOIN mv_test_extension te ON a.test_id = te.test_id
GROUP BY a.id, te.variant, te.variant_order
ORDER BY a.id, te.variant_order;

CREATE UNIQUE INDEX idx_mv_assertion_extension ON mv_assertion_extension (assertion_id, variant);

CREATE INDEX idx_mv_assertion_extension_assertion_id ON mv_assertion_extension (assertion_id);
CREATE INDEX idx_mv_assertion_extension_variant ON mv_assertion_extension (variant);
CREATE INDEX idx_mv_assertion_extension_variant_order ON mv_assertion_extension (variant_order);

SELECT
    'Assertion extension exists for every assertion.' AS test,
    (SELECT count(DISTINCT ae.assertion_id) FROM mv_assertion_extension ae) = (SELECT count(*) FROM assertion) AS result;

CREATE MATERIALIZED VIEW mv_generalization_extension AS
SELECT
    g.id AS generalization_id,
    g.variant AS variant,
    variant_order(g.variant) AS variant_order,
    coalesce(count(r.id), 0) AS reports,
    sum(r.runtime) AS runtime
FROM generalization g
LEFT JOIN junit_test_report r ON g.id = r.generalization_id
GROUP BY g.id, g.variant
ORDER BY g.id, variant_order(g.variant);

CREATE UNIQUE INDEX idx_mv_generalization_extension ON mv_generalization_extension (generalization_id, variant);

CREATE INDEX idx_mv_generalization_extension_generalization_id ON mv_generalization_extension (generalization_id);
CREATE INDEX idx_mv_generalization_extension_variant ON mv_generalization_extension (variant);
CREATE INDEX idx_mv_generalization_extension_variant_order ON mv_generalization_extension (variant_order);

SELECT
    'Generalization extension exists for every generalization.' AS test,
    (SELECT count(DISTINCT ge.generalization_id) FROM mv_generalization_extension ge) = (SELECT count(*) FROM generalization) AS result;

CREATE MATERIALIZED VIEW mv_evosuite_runtime AS
SELECT
    id,
    project_id,
    dense_rank() OVER (
        ORDER BY
            project_id,
            class_name
    ) AS class_id,
    class_name,
    step,
    phase_name,
    runtime
FROM evosuite_runtime
WITH DATA;

CREATE UNIQUE INDEX idx_mv_evosuite_runtime ON mv_evosuite_runtime (id);

CREATE INDEX idx_mv_evosuite_runtime_project_id ON mv_evosuite_runtime (project_id);
CREATE INDEX idx_mv_evosuite_runtime_class_id ON mv_evosuite_runtime (class_id);

DO $$
DECLARE
    phase_columns TEXT := '';
    phase_column_defs TEXT := '';
    create_view_sql TEXT;
BEGIN
    -- Get the list of phase names to create columns, ordered by step
    SELECT
        string_agg(format('%I', lower(phase_name)), ', '),
        string_agg(format('%I NUMERIC', lower(phase_name)), ', ')
    INTO
        phase_columns,
        phase_column_defs
    FROM (
        SELECT
            phase_name
        FROM (
            SELECT
                phase_name,
                MAX(step) as max_step
            FROM
                public.mv_evosuite_runtime
            GROUP BY
                phase_name
            ORDER BY
                max_step
        ) ordered_phases
    ) phases;

    -- Create the materialized view with the dynamic columns
    create_view_sql := format('
        CREATE MATERIALIZED VIEW mv_evosuite_runtime_pivoted AS
        WITH class_totals AS (
            SELECT
                class_id,
                SUM(runtime) AS total_runtime
            FROM
                public.mv_evosuite_runtime
            GROUP BY
                class_id
        ),
        pivoted_data AS (
            SELECT * FROM crosstab(
                ''SELECT
                    rt.class_id,
                    rt.project_id,
                    rt.class_name,
                    rt.phase_name,
                    sum(rt.runtime) AS runtime
                FROM
                    public.mv_evosuite_runtime AS rt
                GROUP BY
                    rt.class_id,
                    rt.project_id,
                    rt.class_name,
                    rt.phase_name
                ORDER BY rt.class_id'',

                ''SELECT phase_name
                  FROM (
                    SELECT
                        phase_name,
                        MAX(step) as max_step
                    FROM
                        public.mv_evosuite_runtime
                    GROUP BY
                        phase_name
                    ORDER BY
                        max_step
                  ) ordered_phases''
            ) AS ct (class_id INTEGER, project_id INTEGER, class_name TEXT, %s)
        )
        SELECT
            pd.class_id,
            pd.project_id,
            pd.class_name,
            ct.total_runtime AS total,
            %s
        FROM
            pivoted_data pd
        JOIN
            class_totals ct ON pd.class_id = ct.class_id
    ', phase_column_defs, phase_columns);

    -- Execute the dynamic SQL to create the materialized view
    EXECUTE create_view_sql;
END $$;

CREATE UNIQUE INDEX idx_mv_evosuite_runtime_pivoted ON mv_evosuite_runtime_pivoted(class_id);

CREATE INDEX idx_mv_evosuite_runtime_pivoted_project_id ON mv_evosuite_runtime_pivoted(project_id);

CREATE MATERIALIZED VIEW mv_runtime_comparison_test_vs_generalization AS
SELECT
    t.project_id,
    project_name(t.project_id) AS project_name,
    g.test_id,
    ge.generalization_id,
    ge.variant,
    ge.variant_order,
    te.runtime AS t_runtime,
    ge.runtime AS g_runtime,
    ge.runtime - te.runtime AS runtime_diff,
    v.tries,
    (ge.runtime - te.runtime) / v.tries AS runtime_diff_per_try
FROM generalization g
JOIN test t ON g.test_id = t.id
JOIN mv_test_extension te ON t.id = te.test_id
JOIN mv_generalization_extension ge ON g.id = ge.generalization_id
JOIN mv_variant v ON t.project_id = v.project_id AND ge.variant = v.variant
WHERE t.is_included AND g.is_included AND te.variant = 'INITIAL'
ORDER BY g.project_id, g.test_id, ge.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_runtime_comparison_test_vs_generalization ON mv_runtime_comparison_test_vs_generalization(test_id, generalization_id);

CREATE INDEX idx_mv_runtime_comparison_test_vs_generalization_p_id ON mv_runtime_comparison_test_vs_generalization(project_id);
CREATE INDEX idx_mv_runtime_comparison_test_vs_generalization_t_id ON mv_runtime_comparison_test_vs_generalization(test_id);
CREATE INDEX idx_mv_runtime_comparison_test_vs_generalization_g_id ON mv_runtime_comparison_test_vs_generalization(generalization_id);
CREATE INDEX idx_mv_runtime_comparison_test_vs_generalization_v ON mv_runtime_comparison_test_vs_generalization(variant);
CREATE INDEX idx_mv_runtime_comparison_test_vs_generalization_v_o ON mv_runtime_comparison_test_vs_generalization(variant_order);

CREATE MATERIALIZED VIEW mv_pit_location AS
WITH
    pit_coverage_locations AS (
        SELECT
            pcr.covered_package_name,
            pcr.covered_class_name,
            pcr.covered_method_name,
            pcr.covered_method_description,
            pcr.covered_block_number
        FROM
            pit_coverage_report pcr
    ),
    pit_mutation_locations AS (
        SELECT
            pmr.mutated_package,
            pmr.mutated_class,
            pmr.mutated_method,
            pmr.method_description,
            block::int
        FROM
            pit_mutation_report pmr,
            jsonb_array_elements_text(pmr.blocks::jsonb) AS block
    )
SELECT
    row_number() OVER () AS id,
    package_name,
    class_name,
    method_name,
    method_description,
    block
FROM (
    SELECT
        covered_package_name AS package_name,
        covered_class_name AS class_name,
        covered_method_name AS method_name,
        covered_method_description AS method_description,
        covered_block_number AS block
    FROM pit_coverage_locations
    UNION ALL
    SELECT * FROM pit_mutation_locations
) AS pit_locations
GROUP BY
    package_name,
    class_name,
    method_name,
    method_description,
    block
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_location_id ON mv_pit_location (id);

CREATE INDEX idx_mv_pit_location_package_name ON mv_pit_location (package_name);
CREATE INDEX idx_mv_pit_location_class_name ON mv_pit_location (class_name);
CREATE INDEX idx_mv_pit_location_method_name ON mv_pit_location (method_name);
CREATE INDEX idx_mv_pit_location_method_description ON mv_pit_location (method_description);
CREATE INDEX idx_mv_pit_location_block ON mv_pit_location (block);

CREATE MATERIALIZED VIEW mv_pit_coverage_report_location AS
SELECT
    pcr.id AS report_id,
    pl.id AS location_id
FROM pit_coverage_report pcr
LEFT JOIN mv_pit_location pl ON
    pl.package_name = pcr.covered_package_name AND
    pl.class_name = pcr.covered_class_name AND
    pl.method_name = pcr.covered_method_name AND
    pl.method_description = pcr.covered_method_description AND
    pl.block = pcr.covered_block_number;

CREATE UNIQUE INDEX idx_mv_pit_coverage_report_location ON mv_pit_coverage_report_location (report_id, location_id);

CREATE INDEX idx_mv_pit_coverage_report_location_report_id ON mv_pit_coverage_report_location (report_id);
CREATE INDEX idx_mv_pit_coverage_report_location_location_id ON mv_pit_coverage_report_location (location_id);

SELECT
    'Every coverage report is mapped to a location.' AS test,
    (SELECT count(*) = 0 FROM mv_pit_coverage_report_location crl WHERE crl.location_id IS NULL) AS result;

CREATE MATERIALIZED VIEW mv_pit_mutation_report_location AS
SELECT
    pmr.id AS report_id,
    pl.id AS location_id
FROM pit_mutation_report pmr
CROSS JOIN LATERAL jsonb_array_elements_text(pmr.blocks::jsonb) AS pmr_block
LEFT JOIN mv_pit_location pl ON
    pl.package_name = pmr.mutated_package AND
    pl.class_name = pmr.mutated_class AND
    pl.method_name = pmr.mutated_method AND
    pl.method_description = pmr.method_description AND
    pl.block = pmr_block::int;

CREATE UNIQUE INDEX idx_mv_pit_mutation_report_location ON mv_pit_mutation_report_location (report_id, location_id);

CREATE INDEX idx_mv_pit_mutation_report_location_report_id ON mv_pit_mutation_report_location (report_id);
CREATE INDEX idx_mv_pit_mutation_report_location_location_id ON mv_pit_mutation_report_location (location_id);

SELECT
    'Every mutation report is mapped to a location.' AS test,
    (SELECT count(*) = 0 FROM mv_pit_mutation_report_location mrl WHERE mrl.location_id IS NULL) AS result;

CREATE MATERIALIZED VIEW mv_pit_coverage_report AS
SELECT
    pcr.id,
    pcr.project_id,
    pcr.test_id,
    pcr.generalization_id,
    pcr.step,
    pcr.stage,
    variant_name(pcr.stage, pcr.variant) AS variant,
    variant_order(variant_name(pcr.stage, pcr.variant)) AS variant_order,
    pl.location_id AS location_id,
    pcr.covered_package_name,
    pcr.covered_class_name,
    pcr.covered_method_name,
    pcr.covered_method_description,
    pcr.covered_block_number,
    pcr.test_package_name,
    pcr.test_class_name,
    pcr.test_method_name
FROM pit_coverage_report pcr
LEFT JOIN mv_pit_coverage_report_location pl ON pcr.id = pl.report_id;

CREATE UNIQUE INDEX idx_mv_pit_coverage_report ON mv_pit_coverage_report (id);

CREATE INDEX idx_mv_pit_coverage_report_project_id ON mv_pit_coverage_report (project_id);
CREATE INDEX idx_mv_pit_coverage_report_test_id ON mv_pit_coverage_report (test_id);
CREATE INDEX idx_mv_pit_coverage_report_generalization_id ON mv_pit_coverage_report (generalization_id);

CREATE INDEX idx_mv_pit_coverage_report_step ON mv_pit_coverage_report (step);
CREATE INDEX idx_mv_pit_coverage_report_stage ON mv_pit_coverage_report (stage);
CREATE INDEX idx_mv_pit_coverage_report_variant ON mv_pit_coverage_report (variant);

CREATE INDEX idx_mv_pit_coverage_report_pit_location_id ON mv_pit_coverage_report (location_id);

SELECT
    'Every coverage report has a location.' AS test,
    (SELECT count(*) = 0 FROM mv_pit_coverage_report pcr WHERE pcr.location_id IS NULL) AS result;

CREATE MATERIALIZED VIEW mv_pit_mutation_report AS
SELECT
    id,
    project_id,
    dense_rank() OVER (
        ORDER BY
            project_id,
            mutated_package,
            mutated_class,
            mutated_method,
            line_number,
            mutator,
            indexes,
            blocks,
            method_description,
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
    mutated_package,
    mutated_class,
    mutated_method,
    method_description,
    line_number,
    mutator,
    indexes,
    blocks,
    COALESCE(
        (SELECT json_agg(pl.location_id)
         FROM mv_pit_mutation_report_location pl
         WHERE pmr.id = pl.report_id),
        '[]'::json
    ) AS location_ids,
    killing_package_name,
    killing_class_name,
    killing_method_name,
    description
FROM
    pit_mutation_report pmr
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_report_id ON mv_pit_mutation_report (id);

CREATE INDEX idx_mv_pit_mutation_report_project_id ON mv_pit_mutation_report (project_id);
CREATE INDEX idx_mv_pit_mutation_report_mutation_id ON mv_pit_mutation_report (mutation_id);
CREATE INDEX idx_mv_pit_mutation_report_killing_test_id ON mv_pit_mutation_report (killing_test_id);
CREATE INDEX idx_mv_pit_mutation_report_killing_generalization_id ON mv_pit_mutation_report (killing_generalization_id);

CREATE INDEX idx_mv_pit_mutation_report_step ON mv_pit_mutation_report (step);
CREATE INDEX idx_mv_pit_mutation_report_stage ON mv_pit_mutation_report (stage);
CREATE INDEX idx_mv_pit_mutation_report_variant ON mv_pit_mutation_report (variant);
CREATE INDEX idx_mv_pit_mutation_report_variant_order ON mv_pit_mutation_report (variant_order);

CREATE INDEX idx_mv_pit_mutation_report_is_detected ON mv_pit_mutation_report (is_detected);
CREATE INDEX idx_mv_pit_mutation_report_mutated_class ON mv_pit_mutation_report (mutated_class);
CREATE INDEX idx_mv_pit_mutation_report_mutated_method ON mv_pit_mutation_report (mutated_method);
CREATE INDEX idx_mv_pit_mutation_report_method_description ON mv_pit_mutation_report (method_description);

SELECT
    'The number of blocks and location IDs are the same.' AS test,
    (SELECT COUNT(*) = 0 FROM mv_pit_mutation_report pmr WHERE json_array_length(blocks::json) != json_array_length(location_ids)) AS result;

SELECT
    'All location IDs are non-NULL.' AS test,
    (SELECT
        CASE WHEN COUNT(*) = 0 THEN TRUE ELSE FALSE END
        FROM mv_pit_mutation_report
        WHERE (
            SELECT COUNT(*)
            FROM json_array_elements(location_ids) AS elements
            WHERE elements IS NULL OR elements::text = 'null'
        ) > 0
    ) AS result;

CREATE MATERIALIZED VIEW mv_pit_mutation_coverage AS
SELECT DISTINCT
    pmr.id AS mutation_id,
    pcr.id AS coverage_id
FROM
    mv_pit_mutation_report pmr
INNER JOIN LATERAL
    jsonb_array_elements_text(pmr.location_ids::jsonb) AS pmr_location_id ON true
INNER JOIN
    mv_pit_coverage_report pcr
ON
    pcr.location_id = pmr_location_id::int AND
    pcr.project_id = pmr.project_id AND
    pcr.variant = pmr.variant
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_coverage ON mv_pit_mutation_coverage (mutation_id, coverage_id);

CREATE INDEX idx_mv_pit_mutation_id ON mv_pit_mutation_coverage (mutation_id);
CREATE INDEX idx_mv_pit_coverage_id ON mv_pit_mutation_coverage (coverage_id);

SELECT
    'No mutation with status NO_COVERAGE has coverage.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_pit_mutation_report pmr
     JOIN mv_pit_mutation_coverage pmc ON pmr.id = pmc.mutation_id
     WHERE pmr.status = 'NO_COVERAGE'
    ) AS result;

SELECT
    'All mutations with status other than NO_COVERAGE have coverage.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_pit_mutation_report pmr
     LEFT JOIN mv_pit_mutation_coverage pmc ON pmr.id = pmc.mutation_id
     WHERE pmr.status != 'NO_COVERAGE'
     AND pmc.coverage_id IS NULL
    ) AS result;

CREATE MATERIALIZED VIEW mv_mutation_covering_tests AS
SELECT
    pmr.project_id,
    pmr.id AS mutation_id,
    pmr.status,
    t.id AS test_id,
    pmr.variant,
    pmr.variant_order,
    min(te.reports) AS reports,
    min(te.runtime) AS runtime
FROM mv_pit_mutation_report pmr
JOIN mv_pit_mutation_coverage pmc ON pmr.id = pmc.mutation_id
JOIN mv_pit_coverage_report pcr ON pmc.coverage_id = pcr.id
JOIN test t ON pcr.test_id = t.id
JOIN mv_test_extension te ON t.id = te.test_id
GROUP BY pmr.project_id, pmr.id, pmr.status, t.id, pmr.variant, pmr.variant_order
ORDER BY pmr.project_id, pmr.id, t.id, pmr.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_covering_tests ON mv_mutation_covering_tests (mutation_id, test_id);

CREATE INDEX idx_mv_pit_mutation_covering_tests_project_id ON mv_mutation_covering_tests (project_id);
CREATE INDEX idx_mv_pit_mutation_covering_tests_mutation_id ON mv_mutation_covering_tests (mutation_id);
CREATE INDEX idx_mv_pit_mutation_covering_tests_test_id ON mv_mutation_covering_tests (test_id);
CREATE INDEX idx_mv_pit_mutation_covering_tests_variant ON mv_mutation_covering_tests (variant);
CREATE INDEX idx_mv_pit_mutation_covering_tests_variant_order ON mv_mutation_covering_tests (variant_order);

SELECT
    'No mutation with status NO_COVERAGE has covering tests.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_mutation_covering_tests mct
     JOIN mv_pit_mutation_report pmr ON mct.mutation_id = pmr.id
     WHERE pmr.status = 'NO_COVERAGE'
    ) AS result;

CREATE MATERIALIZED VIEW mv_mutation_covering_assertions AS
SELECT
    mct.project_id,
    mct.mutation_id,
    mct.status,
    a.id AS assertion_id,
    mct.variant,
    mct.variant_order,
    min(ae.model_java_size) AS model_java_size,
    min(ae.model_operation_count) AS model_operation_count
FROM mv_mutation_covering_tests mct
JOIN assertion a ON mct.test_id = a.test_id
JOIN mv_assertion_extension ae ON a.id = ae.assertion_id
GROUP BY mct.project_id, mct.mutation_id, mct.status, a.id, mct.variant, mct.variant_order
ORDER BY mct.project_id, mct.mutation_id, a.id, mct.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_covering_assertions ON mv_mutation_covering_assertions (mutation_id, assertion_id);

CREATE INDEX idx_mv_pit_mutation_covering_assertions_project_id ON mv_mutation_covering_assertions (project_id);
CREATE INDEX idx_mv_pit_mutation_covering_assertions_mutation_id ON mv_mutation_covering_assertions (mutation_id);
CREATE INDEX idx_mv_pit_mutation_covering_assertions_test_id ON mv_mutation_covering_assertions (assertion_id);
CREATE INDEX idx_mv_pit_mutation_covering_assertions_variant ON mv_mutation_covering_assertions (variant);
CREATE INDEX idx_mv_pit_mutation_covering_assertions_variant_order ON mv_mutation_covering_assertions (variant_order);

SELECT
    'No mutation with status NO_COVERAGE has covering assertions.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_mutation_covering_assertions mca
     JOIN mv_pit_mutation_report pmr ON mca.mutation_id = pmr.id
     WHERE pmr.status = 'NO_COVERAGE'
    ) AS result;

SELECT
    'All mutations with covering assertions also have covering tests.' AS test,
    (SELECT COUNT(*) = 0
     FROM (
         SELECT DISTINCT mca.mutation_id, mca.variant
         FROM mv_mutation_covering_assertions mca
         EXCEPT
         SELECT DISTINCT mct.mutation_id, mct.variant
         FROM mv_mutation_covering_tests mct
     ) AS mutations_with_assertions_but_no_tests
    ) AS result;

CREATE MATERIALIZED VIEW mv_mutation_covering_generalizations AS
SELECT
    pmr.project_id,
    pmr.id AS mutation_id,
    pmr.status,
    g.id AS generalization_id,
    pmr.variant,
    pmr.variant_order,
    min(ge.reports) AS reports,
    min(ge.runtime) AS runtime,
    min(g.total_constraint_count) AS total_constraint_count,
    min(g.used_constraint_count) AS used_constraint_count
FROM mv_pit_mutation_report pmr
JOIN mv_pit_mutation_coverage pmc ON pmr.id = pmc.mutation_id
JOIN mv_pit_coverage_report pcr ON pmc.coverage_id = pcr.id
JOIN generalization g ON pcr.generalization_id = g.id
JOIN mv_generalization_extension ge ON g.id = ge.generalization_id
GROUP BY pmr.project_id, pmr.id, pmr.status, g.id, pmr.variant, pmr.variant_order
ORDER BY pmr.project_id, pmr.id, g.id, pmr.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_pit_mutation_covering_generalizations ON mv_mutation_covering_generalizations (mutation_id, generalization_id);

CREATE INDEX idx_mv_pit_mutation_covering_generalizations_project_id ON mv_mutation_covering_generalizations (project_id);
CREATE INDEX idx_mv_pit_mutation_covering_generalizations_mutation_id ON mv_mutation_covering_generalizations (mutation_id);
CREATE INDEX idx_mv_pit_mutation_covering_generalizations_generalization_id ON mv_mutation_covering_generalizations (generalization_id);
CREATE INDEX idx_mv_pit_mutation_covering_generalizations_variant ON mv_mutation_covering_generalizations (variant);
CREATE INDEX idx_mv_pit_mutation_covering_generalizations_variant_order ON mv_mutation_covering_generalizations (variant_order);

SELECT
    'No mutation with status NO_COVERAGE has covering generalizations.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_mutation_covering_generalizations mcg
     JOIN mv_pit_mutation_report pmr ON mcg.mutation_id = pmr.id
     WHERE pmr.status = 'NO_COVERAGE'
    ) AS result;

SELECT
    'All mutations with status other than NO_COVERAGE have covering tests or generalizations.' AS test,
    (SELECT COUNT(*) = 0
     FROM mv_pit_mutation_report pmr
     LEFT JOIN mv_mutation_covering_tests mct ON pmr.id = mct.mutation_id
     LEFT JOIN mv_mutation_covering_generalizations mcg ON pmr.id = mcg.mutation_id
     WHERE pmr.status != 'NO_COVERAGE'
       AND mct.mutation_id IS NULL
       AND mcg.mutation_id IS NULL
    ) AS result;

CREATE MATERIALIZED VIEW mv_pit_mutation_report_extension AS
WITH base_data AS (
    SELECT
        pmr.id AS report_id,
        pmr.project_id,
        project_name(pmr.project_id) AS project_name,
        pmr.mutation_id,
        pmr.variant,
        pmr.is_detected,
        pmr.status,
        pmr.variant_order
    FROM mv_pit_mutation_report pmr
    JOIN project p ON pmr.project_id = p.id
),
test_stats AS (
    SELECT
        mct.mutation_id,
        count(t.id) AS covering_tests,
        count(t.id) FILTER (WHERE t.is_included) AS included_tests,
        count(t.id) FILTER (WHERE NOT t.is_included) AS excluded_tests,
        sum(mct.reports) AS test_reports_sum,
        sum(mct.runtime) AS test_runtime_sum
    FROM mv_mutation_covering_tests mct
    LEFT JOIN test t ON mct.test_id = t.id
    GROUP BY mct.mutation_id
),
assertion_stats AS (
    SELECT
        mca.mutation_id,
        count(a.id) AS covering_assertions,
        count(a.id) FILTER (WHERE a.is_included) AS included_assertions,
        count(a.id) FILTER (WHERE NOT a.is_included) AS excluded_assertions,
        sum(mca.model_java_size) AS model_java_size_sum,
        sum(mca.model_operation_count) AS model_operation_count_sum
    FROM mv_mutation_covering_assertions mca
    LEFT JOIN assertion a ON mca.assertion_id = a.id
    GROUP BY mca.mutation_id
),
generalization_stats AS (
    SELECT
        mcg.mutation_id,
        count(g.id) AS covering_generalizations,
        count(g.id) FILTER (WHERE g.is_included) AS included_generalizations,
        count(g.id) FILTER (WHERE NOT g.is_included) AS excluded_generalizations,
        sum(mcg.reports) AS generalization_reports_sum,
        sum(mcg.runtime) AS generalization_runtime_sum,
        sum(mcg.total_constraint_count) AS total_constraint_count_sum,
        sum(mcg.used_constraint_count) AS used_constraint_count_sum
    FROM mv_mutation_covering_generalizations mcg
    LEFT JOIN generalization g ON mcg.generalization_id = g.id
    GROUP BY mcg.mutation_id
)
SELECT
    b.report_id,
    b.project_id,
    b.project_name,
    b.mutation_id,
    b.variant,
    b.variant_order,
    b.is_detected,
    b.status,
    COALESCE(t.covering_tests, 0) AS covering_tests,
    COALESCE(t.included_tests, 0) AS included_tests,
    COALESCE(t.excluded_tests, 0) AS excluded_tests,
    COALESCE(a.covering_assertions, 0) AS covering_assertions,
    COALESCE(a.included_assertions, 0) AS included_assertions,
    COALESCE(a.excluded_assertions, 0) AS excluded_assertions,
    COALESCE(g.covering_generalizations, 0) AS covering_generalizations,
    COALESCE(g.included_generalizations, 0) AS included_generalizations,
    COALESCE(g.excluded_generalizations, 0) AS excluded_generalizations,
    COALESCE(t.test_reports_sum, 0) AS test_reports_sum,
    COALESCE(t.test_runtime_sum, 0) AS test_runtime_sum,
    COALESCE(a.model_java_size_sum, 0) AS model_java_size_sum,
    COALESCE(a.model_operation_count_sum, 0) AS model_operation_count_sum,
    COALESCE(g.generalization_reports_sum, 0) AS generalization_reports_sum,
    COALESCE(g.generalization_runtime_sum, 0) AS generalization_runtime_sum,
    COALESCE(g.total_constraint_count_sum, 0) AS total_constraint_count_sum,
    COALESCE(g.used_constraint_count_sum, 0) AS used_constraint_count_sum
FROM base_data b
LEFT JOIN test_stats t ON b.report_id = t.mutation_id
LEFT JOIN assertion_stats a ON b.report_id = a.mutation_id
LEFT JOIN generalization_stats g ON b.report_id = g.mutation_id
ORDER BY b.project_name, b.project_id, b.mutation_id, b.variant_order;

CREATE UNIQUE INDEX idx_mv_pit_mutation_report_extension ON mv_pit_mutation_report_extension (report_id);

CREATE INDEX idx_mv_pit_mutation_report_extension_project_id ON mv_pit_mutation_report_extension (project_id);
CREATE INDEX idx_mv_pit_mutation_report_extension_mutation_id ON mv_pit_mutation_report_extension (mutation_id);
CREATE INDEX idx_mv_pit_mutation_report_extension_variant ON mv_pit_mutation_report_extension (variant);
CREATE INDEX idx_mv_pit_mutation_report_extension_variant_order ON mv_pit_mutation_report_extension (variant_order);

CREATE INDEX idx_mv_pit_mutation_report_extension_is_detected ON mv_pit_mutation_report_extension (is_detected);
CREATE INDEX idx_mv_pit_mutation_report_extension_status ON mv_pit_mutation_report_extension (status);

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
CREATE INDEX idx_mv_mutation_variant_comparison_a_variant ON mv_mutation_variant_comparison (a_variant);
CREATE INDEX idx_mv_mutation_variant_comparison_b_variant ON mv_mutation_variant_comparison (b_variant);

CREATE INDEX idx_mv_mutation_variant_comparison_a_status ON mv_mutation_variant_comparison (a_status);
CREATE INDEX idx_mv_mutation_variant_comparison_b_status ON mv_mutation_variant_comparison (b_status);
CREATE INDEX idx_mv_mutation_variant_comparison_a_is_detected ON mv_mutation_variant_comparison (a_is_detected);
CREATE INDEX idx_mv_mutation_variant_comparison_b_is_detected ON mv_mutation_variant_comparison (b_is_detected);

CREATE MATERIALIZED VIEW mv_mutation_status_changes AS
SELECT
    p.id AS project_id,
    project_name(p.id) AS project_name,
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
    a.input_model_statistics,
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
JOIN project p ON ra.project_id = p.id
LEFT JOIN test kt ON rb.killing_test_id = kt.id
LEFT JOIN generalization kg ON rb.killing_generalization_id = kg.id
LEFT JOIN test t ON t.id = kg.test_id
LEFT JOIN assertion a ON a.id = kg.assertion_id
LEFT JOIN junit_test_report tr ON kg.id = tr.generalization_id
WHERE c.a_variant IN ('ORIGINAL', 'INITIAL') AND c.b_variant != 'ORIGINAL' AND c.a_status != c.b_status
ORDER BY b_is_detected, b_status = 'KILLED', kg.id IS NOT NULL, ra.id, rb.variant_order
WITH DATA;

CREATE UNIQUE INDEX idx_mv_mutation_status_changes ON mv_mutation_status_changes (a_report_id, b_report_id);

CREATE INDEX idx_mv_mutation_status_changes_project_id ON mv_mutation_status_changes (project_id);
CREATE INDEX idx_mv_mutation_status_changes_a_report_id ON mv_mutation_status_changes (a_report_id);
CREATE INDEX idx_mv_mutation_status_changes_b_report_id ON mv_mutation_status_changes (b_report_id);
CREATE INDEX idx_mv_mutation_status_changes_a_variant ON mv_mutation_status_changes (a_variant);
CREATE INDEX idx_mv_mutation_status_changes_b_variant ON mv_mutation_status_changes (b_variant);
CREATE INDEX idx_mv_mutation_status_changes_a_status ON mv_mutation_status_changes (a_status);
CREATE INDEX idx_mv_mutation_status_changes_b_status ON mv_mutation_status_changes (b_status);
CREATE INDEX idx_mv_mutation_status_changes_a_is_detected ON mv_mutation_status_changes (a_is_detected);
CREATE INDEX idx_mv_mutation_status_changes_b_is_detected ON mv_mutation_status_changes (b_is_detected);

CREATE MATERIALIZED VIEW mv_generalization_effects AS
WITH
    newly_killed_mutations AS (
        SELECT *
        FROM mv_mutation_status_changes
        WHERE a_is_detected = FALSE AND b_status = 'KILLED'
    ),
    killing_generalizations AS (
        SELECT
            nkm.project_id,
            nkm.a_variant,
            nkm.b_variant,
            nkm.killing_generalization_id AS generalization_id,
            min(g.test_id) AS test_id,
            min(nkm.killing_line_count) AS line_count,
            min(ge.runtime) AS runtime
        FROM newly_killed_mutations nkm
        JOIN generalization g ON nkm.killing_generalization_id = g.id
        JOIN mv_generalization_extension ge ON nkm.killing_generalization_id = ge.generalization_id
        GROUP BY 1, 2, 3, 4
    ),
    generalized_tests AS (
        SELECT
            kg.project_id,
            kg.a_variant,
            kg.b_variant,
            kg.test_id,
            t.line_count,
            te.runtime,
            (SELECT count(*) FROM assertion a WHERE a.test_id = kg.test_id) AS assertions
        FROM killing_generalizations kg
        JOIN test t ON kg.test_id = t.id
        JOIN mv_test_extension te ON kg.test_id = te.test_id AND te.variant = kg.a_variant
    ),
    base_data AS (
        SELECT
            nkm.project_id,
            nkm.a_variant,
            nkm.b_variant,
            count(*) AS mutations,
            (SELECT count(*) FROM killing_generalizations kg
             WHERE kg.project_id = nkm.project_id
             AND kg.a_variant = nkm.a_variant
             AND kg.b_variant = nkm.b_variant) AS generalizations,
            (SELECT sum(kg.line_count) FROM killing_generalizations kg
             WHERE kg.project_id = nkm.project_id
             AND kg.a_variant = nkm.a_variant
             AND kg.b_variant = nkm.b_variant) AS generalization_lines,
            (SELECT sum(kg.runtime) FROM killing_generalizations kg
             WHERE kg.project_id = nkm.project_id
             AND kg.a_variant = nkm.a_variant
             AND kg.b_variant = nkm.b_variant) AS generalization_runtime
        FROM newly_killed_mutations nkm
        GROUP BY 1, 2, 3
    ),
    test_data AS (
        SELECT
            gt.project_id,
            gt.a_variant,
            gt.b_variant,
            count(*) AS tests,
            sum(CASE WHEN assertions = 1 THEN 1 ELSE 0 END) AS tests_replaceable,
            sum(gt.line_count) AS test_lines,
            coalesce(sum(gt.line_count) FILTER (WHERE gt.assertions = 1), 0) AS test_lines_replaceable,
            coalesce(sum(gt.line_count) FILTER (WHERE gt.assertions > 1), 0) AS test_lines_non_replaceable,
            sum(gt.runtime) AS test_runtime,
            coalesce(sum(gt.runtime) FILTER (WHERE gt.assertions = 1), 0) AS test_runtime_replaceable,
            coalesce(sum(gt.runtime) FILTER (WHERE gt.assertions > 1), 0) AS test_runtime_non_replaceable
        FROM generalized_tests gt
        GROUP BY 1, 2, 3
    ),
    test_counts AS (
        SELECT
            project_id,
            count(*) AS test_count,
            sum(t.line_count) AS line_count,
            sum(te.runtime) AS runtime
        FROM test t
        JOIN mv_test_extension te ON t.id = te.test_id
        WHERE te.variant = 'ORIGINAL'
        GROUP BY 1
    )
SELECT
    bd.project_id,
    project_name(bd.project_id),
    bd.a_variant,
    bd.b_variant,
    variant_order(bd.a_variant) AS a_variant_order,
    variant_order(bd.b_variant) AS b_variant_order,
    -- Mutation data
    bd.mutations AS killed_mutations,
    -- Test counts
    tc.test_count AS tests_before,
    bd.generalizations AS added_tests,
    td.tests_replaceable AS removed_tests,
    (tc.test_count + bd.generalizations - td.tests_replaceable) AS tests_after,
    (tc.test_count + bd.generalizations - td.tests_replaceable) - tc.test_count AS tests_delta,
    ((tc.test_count + bd.generalizations - td.tests_replaceable) - tc.test_count) * 100.0 / NULLIF(tc.test_count, 0) AS tests_delta_pct,
    -- Line counts
    tc.line_count AS lines_before,
    bd.generalization_lines AS added_lines,
    td.test_lines_replaceable AS removed_lines,
    (tc.line_count + bd.generalization_lines - td.test_lines_replaceable) AS lines_after,
    (tc.line_count + bd.generalization_lines - td.test_lines_replaceable) - tc.line_count AS lines_delta,
    ((tc.line_count + bd.generalization_lines - td.test_lines_replaceable) - tc.line_count) * 100.0 / NULLIF(tc.line_count, 0) AS lines_delta_pct,
    -- Runtime data
    tc.runtime AS runtime_before,
    bd.generalization_runtime AS added_runtime,
    td.test_runtime_replaceable AS removed_runtime,
    (tc.runtime + bd.generalization_runtime - td.test_runtime_replaceable) AS runtime_after,
    (tc.runtime + bd.generalization_runtime - td.test_runtime_replaceable) - tc.runtime AS runtime_delta,
    ((tc.runtime + bd.generalization_runtime - td.test_runtime_replaceable) - tc.runtime) * 100.0 / NULLIF(tc.runtime, 0) AS runtime_delta_pct
FROM base_data bd
JOIN test_data td ON bd.project_id = td.project_id AND bd.a_variant = td.a_variant AND bd.b_variant = td.b_variant
JOIN test_counts tc ON bd.project_id = tc.project_id
ORDER BY bd.project_id, variant_order(bd.a_variant), variant_order(bd.b_variant)
WITH DATA;

CREATE UNIQUE INDEX idx_mv_generalization_effects ON mv_generalization_effects (project_id, a_variant, b_variant);

CREATE INDEX idx_mv_generalization_effects_project_id ON mv_generalization_effects (project_id);
CREATE INDEX idx_mv_generalization_effects_a_variant ON mv_generalization_effects (a_variant);
CREATE INDEX idx_mv_generalization_effects_b_variant ON mv_generalization_effects (b_variant);

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
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error,
        COUNT(CASE WHEN pmr.status = 'RUN_ERROR' THEN 1 END) AS run_error
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
        run_error,
        -- Calculate percentages
        ROUND((covered * 100.0 / total), 2) AS covered_pct,
        ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
        ROUND((survived * 100.0 / total), 2) AS survived_pct,
        ROUND((detected * 100.0 / total), 2) AS detected_pct,
        ROUND((killed * 100.0 / total), 2) AS killed_pct,
        ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
        ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
        ROUND((run_error * 100.0 / total), 2) AS run_error_pct,
        --
        ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
        ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
        ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
        ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
        ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct,
        ROUND((run_error * 100.0 / covered), 2) AS run_error_of_covered_pct
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
    s.run_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    s.run_error - b.run_error AS run_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    s.run_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    s.run_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    ROUND((s.run_error_pct - b.run_error_pct), 2) AS run_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff,
    ROUND((s.run_error_of_covered_pct - b.run_error_of_covered_pct), 2) AS run_error_of_covered_pct_diff
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
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error,
        COUNT(CASE WHEN pmr.status = 'RUN_ERROR' THEN 1 END) AS run_error
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
            run_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            ROUND((run_error * 100.0 / total), 2) AS run_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct,
            ROUND((run_error * 100.0 / covered), 2) AS run_error_of_covered_pct
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
    s.run_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    s.run_error - b.run_error AS run_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    s.run_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    s.run_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    ROUND((s.run_error_pct - b.run_error_pct), 2) AS run_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff,
    ROUND((s.run_error_of_covered_pct - b.run_error_of_covered_pct), 2) AS run_error_of_covered_pct_diff
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
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error,
        COUNT(CASE WHEN pmr.status = 'RUN_ERROR' THEN 1 END) AS run_error
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
            run_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            ROUND((run_error * 100.0 / total), 2) AS run_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct,
            ROUND((run_error * 100.0 / covered), 2) AS run_error_of_covered_pct
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
    s.run_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    s.run_error - b.run_error AS run_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    s.run_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    s.run_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    ROUND((s.run_error_pct - b.run_error_pct), 2) AS run_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff,
    ROUND((s.run_error_of_covered_pct - b.run_error_of_covered_pct), 2) AS run_error_of_covered_pct_diff
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
        COUNT(CASE WHEN pmr.status = 'MEMORY_ERROR' THEN 1 END) AS memory_error,
        COUNT(CASE WHEN pmr.status = 'RUN_ERROR' THEN 1 END) AS run_error
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
            run_error,
            -- Calculate percentages
            ROUND((covered * 100.0 / total), 2) AS covered_pct,
            ROUND((uncovered * 100.0 / total), 2) AS uncovered_pct,
            ROUND((survived * 100.0 / total), 2) AS survived_pct,
            ROUND((detected * 100.0 / total), 2) AS detected_pct,
            ROUND((killed * 100.0 / total), 2) AS killed_pct,
            ROUND((timed_out * 100.0 / total), 2) AS timed_out_pct,
            ROUND((memory_error * 100.0 / total), 2) AS memory_error_pct,
            ROUND((run_error * 100.0 / total), 2) AS run_error_pct,
            --
            ROUND((survived * 100.0 / covered), 2) AS survived_of_covered_pct,
            ROUND((detected * 100.0 / covered), 2) AS detected_of_covered_pct,
            ROUND((killed * 100.0 / covered), 2) AS killed_of_covered_pct,
            ROUND((timed_out * 100.0 / covered), 2) AS timed_out_of_covered_pct,
            ROUND((memory_error * 100.0 / covered), 2) AS memory_error_of_covered_pct,
            ROUND((run_error * 100.0 / covered), 2) AS run_error_of_covered_pct
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
    s.run_error,
    -- Difference columns
    s.covered - b.covered AS covered_diff,
    s.uncovered - b.uncovered AS uncovered_diff,
    s.survived - b.survived AS survived_diff,
    s.detected - b.detected AS detected_diff,
    s.killed - b.killed AS killed_diff,
    s.timed_out - b.timed_out AS timed_out_diff,
    s.memory_error - b.memory_error AS memory_error_diff,
    s.run_error - b.run_error AS run_error_diff,
    -- Percentage columns
    s.covered_pct,
    s.uncovered_pct,
    s.survived_pct,
    s.detected_pct,
    s.killed_pct,
    s.timed_out_pct,
    s.memory_error_pct,
    s.run_error_pct,
    --
    s.survived_of_covered_pct,
    s.detected_of_covered_pct,
    s.killed_of_covered_pct,
    s.timed_out_of_covered_pct,
    s.memory_error_of_covered_pct,
    s.run_error_of_covered_pct,
    -- Difference columns
    ROUND((s.covered_pct - b.covered_pct), 2) AS covered_pct_diff,
    ROUND((s.uncovered_pct - b.uncovered_pct), 2) AS uncovered_pct_diff,
    ROUND((s.survived_pct - b.survived_pct), 2) AS survived_pct_diff,
    ROUND((s.detected_pct - b.detected_pct), 2) AS detected_pct_diff,
    ROUND((s.killed_pct - b.killed_pct), 2) AS killed_pct_diff,
    ROUND((s.timed_out_pct - b.timed_out_pct), 2) AS timed_out_pct_diff,
    ROUND((s.memory_error_pct - b.memory_error_pct), 2) AS memory_error_pct_diff,
    ROUND((s.run_error_pct - b.run_error_pct), 2) AS run_error_pct_diff,
    --
    ROUND((s.survived_of_covered_pct - b.survived_of_covered_pct), 2) AS survived_of_covered_pct_diff,
    ROUND((s.detected_of_covered_pct - b.detected_of_covered_pct), 2) AS detected_of_covered_pct_diff,
    ROUND((s.killed_of_covered_pct - b.killed_of_covered_pct), 2) AS killed_of_covered_pct_diff,
    ROUND((s.timed_out_of_covered_pct - b.timed_out_of_covered_pct), 2) AS timed_out_of_covered_pct_diff,
    ROUND((s.memory_error_of_covered_pct - b.memory_error_of_covered_pct), 2) AS memory_error_of_covered_pct_diff,
    ROUND((s.run_error_of_covered_pct - b.run_error_of_covered_pct), 2) AS run_error_of_covered_pct_diff
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

CREATE MATERIALIZED VIEW mv_efficiency_comparison_evosuite_vs_teralizer AS
WITH
    mutation_results AS (
        SELECT
            mr.project_id,
            mr.project_name,
            mr.variant,
            variant_order(mr.variant) AS variant_order,
            mr.detected_of_covered_pct
        FROM mv_mutation_results_by_project_variant mr
    ),
    evosuite_runtime AS (
        SELECT
            rt.project_id,
            project_name(rt.project_id) AS project_name,
            sum(rt.runtime) AS runtime
        FROM mv_evosuite_runtime rt
        GROUP BY rt.project_id
    ),
    evosuite_runtime_no_validation AS (
        SELECT
            rt.project_id,
            project_name(rt.project_id) AS project_name,
            sum(rt.runtime) AS runtime
        FROM mv_evosuite_runtime rt
        WHERE rt.phase_name != 'JUNIT_CHECK'
        GROUP BY rt.project_id
    ),
    teralizer_runtime AS (
        SELECT
            t.project_id,
            project_name(t.project_id) AS project_name,
            coalesce(t.variant, 'SHARED') AS variant,
            variant_order(t.variant) AS variant_order,
            sum(t.runtime) AS runtime
        FROM
            task t
        WHERE
            t.runtime IS NOT NULL
            AND t.stage IN (
                -- Specification Extraction
                'BUILD_SPOON_MODEL', 'ANALYZE_TESTS', 'FILTER_TESTS', 'FILTER_ASSERTIONS',
                'ADD_JPF_INSTRUMENTATION', 'BUILD_PROJECT_INSTRUMENTED', 'EXECUTE_JPF', 'ANALYZE_JPF',
                -- Initial Validation (excluding evaluation-only)
               'ADD_DEPENDENCIES', 'BUILD_PROJECT_INITIAL', 'EXECUTE_TESTS_INITIAL',
               'COLLECT_JUNIT_REPORTS_INITIAL', --'COLLECT_JACOCO_DATA_INITIAL', 'COLLECT_PIT_DATA_INITIAL'
                -- Generalization
               'GENERALIZE_TESTS',
                -- Generalization Validation (excluding generalization-only):
               'BUILD_PROJECT_GENERALIZED', 'EXECUTE_TESTS_GENERALIZED',
               'COLLECT_JUNIT_REPORTS_GENERALIZED', 'FILTER_GENERALIZATIONS',
                --'COLLECT_JACOCO_DATA_GENERALIZED',
               'COLLECT_PIT_DATA_GENERALIZED'
            )
        GROUP BY
            t.project_id,
            t.variant
        ORDER BY t.project_id, variant_order(t.variant)
    ),
    teralizer_runtime_no_validation AS (
        SELECT
            t.project_id,
            project_name(t.project_id) AS project_name,
            coalesce(t.variant, 'SHARED') AS variant,
            variant_order(t.variant) AS variant_order,
            sum(t.runtime) AS runtime
        FROM
            task t
        WHERE
            t.runtime IS NOT NULL
            AND t.stage IN (
                -- Specification Extraction
                'BUILD_SPOON_MODEL', 'ANALYZE_TESTS', 'FILTER_TESTS', 'FILTER_ASSERTIONS',
                'ADD_JPF_INSTRUMENTATION', 'BUILD_PROJECT_INSTRUMENTED', 'EXECUTE_JPF', 'ANALYZE_JPF',
                -- Generalization (excluding validation stages)
                'GENERALIZE_TESTS'
                -- Excluded all validation stages
            )
        GROUP BY
            t.project_id,
            t.variant
        ORDER BY t.project_id, variant_order(t.variant)
    )
SELECT
    mre.project_name,
    mre.variant AS evosuite_variant,
    mrt.variant AS teralizer_variant,
    ert.runtime AS evosuite_runtime,
    trt_shared.runtime + trt_variant.runtime AS teralizer_runtime,
    ert_no_val.runtime AS e_no_validation,
    trt_shared_no_val.runtime + trt_variant_no_val.runtime AS t_no_validation,
    (ert.runtime - ert_no_val.runtime) AS e_validation,
    (trt_shared.runtime + trt_variant.runtime) - (trt_shared_no_val.runtime + trt_variant_no_val.runtime) AS t_validation,
    mre.detected_of_covered_pct AS evosuite_detected,
    mrt.detected_of_covered_pct AS teralizer_detected
FROM mutation_results mre
INNER JOIN evosuite_runtime ert ON mre.project_name = ert.project_name AND mre.variant = 'INITIAL'
INNER JOIN evosuite_runtime_no_validation ert_no_val ON mre.project_name = ert_no_val.project_name AND mre.variant = 'INITIAL'
INNER JOIN teralizer_runtime trt_shared ON mre.project_id = trt_shared.project_id AND trt_shared.variant = 'SHARED'
INNER JOIN teralizer_runtime trt_variant ON mre.project_id = trt_variant.project_id AND trt_variant.variant != 'SHARED'
INNER JOIN teralizer_runtime_no_validation trt_shared_no_val ON mre.project_id = trt_shared_no_val.project_id AND trt_shared_no_val.variant = 'SHARED'
INNER JOIN teralizer_runtime_no_validation trt_variant_no_val ON mre.project_id = trt_variant_no_val.project_id AND trt_variant_no_val.variant != 'SHARED' AND trt_variant_no_val.variant = trt_variant.variant
INNER JOIN mutation_results mrt ON trt_variant.project_id = mrt.project_id AND trt_variant.variant = mrt.variant
ORDER BY mre.project_id, mre.variant_order, trt_variant.variant_order
WITH DATA;

CREATE MATERIALIZED VIEW mv_mutation_detection_comparison AS
SELECT
    pmr.project_id,
    project_name(pmr.project_id) AS project_name,
    pmr.variant,
    pmr.is_detected,
    count(DISTINCT pmr.id) AS count,
    avg(mct.runtime) AS avg_test_runtime,
    avg(mca.model_java_size) AS avg_model_java_size,
    avg(mca.model_operation_count) AS avg_model_operation_count,
    avg(mcg.runtime) AS avg_generalization_runtime,
    avg(mcg.total_constraint_count) AS avg_total_constraint_count,
    avg(mcg.used_constraint_count) AS avg_used_constraint_count,
    100 * avg(CASE
        WHEN mcg.total_constraint_count = 0 THEN 1  -- Use 1 (100%) when denominator is 0
        ELSE mcg.used_constraint_count / mcg.total_constraint_count
    END) AS avg_used_constraint_pct
FROM mv_pit_mutation_report pmr
LEFT JOIN mv_mutation_covering_tests mct ON pmr.id = mct.mutation_id AND pmr.variant = mct.variant
LEFT JOIN mv_mutation_covering_assertions mca ON pmr.id = mca.mutation_id AND pmr.variant = mca.variant
LEFT JOIN mv_mutation_covering_generalizations mcg ON pmr.id = mcg.mutation_id AND pmr.variant = mcg.variant
WHERE pmr.status != 'NO_COVERAGE' AND pmr.variant = 'IMPROVED_200_TRIES'
GROUP BY pmr.project_id, pmr.variant, pmr.variant_order, pmr.is_detected
ORDER BY pmr.project_id, pmr.variant_order, pmr.is_detected
WITH DATA;

CREATE MATERIALIZED VIEW mv_exclusions_all AS
SELECT
    coalesce(e.variant, 'SHARED') AS variant,
    e.level,
    e.is_included,
    SUBSTRING(e.exclusion_info,
        POSITION('Excluded by ' IN e.exclusion_info) + 12,
        POSITION('{' IN e.exclusion_info) - (POSITION('Excluded by ' IN e.exclusion_info) + 12)
    ) AS excluded_by,
    COUNT(*) AS count
FROM (
    SELECT t.project_id, null AS variant, '1-TEST' AS level, t.is_included, t.exclusion_info FROM test t
    UNION ALL
    SELECT a.project_id, null, '2-ASSERTION', a.is_included, a.exclusion_info FROM assertion a
    UNION ALL
    SELECT g.project_id, g.variant, '3-GENERALIZATION', g.is_included, g.exclusion_info FROM generalization g
) AS e
JOIN project p ON e.project_id = p.id
WHERE p.use_test_generalization
GROUP BY
    e.variant,
    e.level,
    e.is_included,
    excluded_by
ORDER BY
    variant_order(e.variant),
    e.level,
    e.is_included DESC,
    e.count DESC
WITH DATA;

CREATE MATERIALIZED VIEW mv_exclusions_filtering AS
WITH
    base_data AS (
        SELECT
            coalesce(g.variant, 'SHARED') AS variant,
            CASE
                WHEN fr.test_id IS NOT NULL THEN '1-TEST'
                WHEN fr.assertion_id IS NOT NULL THEN '2-ASSERTION'
                WHEN fr.generalization_id IS NOT NULL THEN '3-GENERALIZATION'
            END AS level,
            simple_name(fr.filter_name) AS filter_name,
            fr.decision,
            count(*) AS count
        FROM filter_result fr
        JOIN project p ON fr.project_id = p.id
        LEFT JOIN generalization g ON fr.generalization_id = g.id
        WHERE p.use_test_generalization
        GROUP BY
            g.variant,
            fr.filter_name,
            CASE
                WHEN fr.test_id IS NOT NULL THEN '1-TEST'
                WHEN fr.assertion_id IS NOT NULL THEN '2-ASSERTION'
                WHEN fr.generalization_id IS NOT NULL THEN '3-GENERALIZATION'
            END,
            fr.decision
    ),
    pivoted AS (
        SELECT
            variant,
            level,
            filter_name,
            SUM(count) AS total,
            SUM(CASE WHEN decision = 'ACCEPT' THEN count ELSE 0 END) AS accept,
            SUM(CASE WHEN decision = 'REJECT' THEN count ELSE 0 END) AS reject,
            SUM(CASE WHEN decision = 'DEFER' THEN count ELSE 0 END) AS defer
        FROM base_data
        GROUP BY
            variant,
            level,
            filter_name
    )
SELECT
    variant,
    level,
    filter_name,
    total,
    accept,
    reject,
    defer
FROM pivoted
ORDER BY
    variant_order(CASE WHEN variant = 'SHARED' THEN null ELSE variant END),
    level,
    total DESC,
    filter_name
WITH DATA;

CREATE MATERIALIZED VIEW mv_exclusions_jpf AS
WITH
    categorized_tasks AS (
        SELECT
            CASE
                WHEN info LIKE '%java.lang.NoSuchMethodException!!%' THEN 'NoSuchMethodException'
                WHEN info LIKE '%java.lang.ArithmeticException: !!!div by 0%' THEN 'ArithmeticException: div by 0'
                WHEN info LIKE '%java.lang.RuntimeException: NEWARRAY: symbolic array length%' THEN 'RuntimeException: symbolic array length'
                WHEN info LIKE '%at gov.nasa.jpf.vm.NamedFields.setLongValue(NamedFields.java:158)%' THEN 'ArrayIndexOutOfBoundsException (setLongValue)'
                WHEN info LIKE '%at gov.nasa.jpf.vm.NamedFields.setDoubleValue(NamedFields.java:164)%' THEN 'ArrayIndexOutOfBoundsException (setDoubleValue)'
                WHEN info LIKE '%at gov.nasa.jpf.vm.FunctionObjectFactory.getFunctionObject(FunctionObjectFactory.java:28)%' THEN 'NullPointerException (getFunctionObject)'
                WHEN info LIKE '%at gov.nasa.jpf.vm.GenericHeap.queueMark(GenericHeap.java:557)%' THEN 'NullPointerException (queueMark)'
                WHEN info LIKE '%at teralizer.jpf.TestGeneralizationListener.writeSpecificationFiles(TestGeneralizationListener.java:124)%' THEN 'NullPointerException (writeSpecificationFiles:124)'
                WHEN info LIKE '%at teralizer.jpf.TestGeneralizationListener.writeSpecificationFiles(TestGeneralizationListener.java:147)%' THEN 'NullPointerException (writeSpecificationFiles:147)'
                WHEN info LIKE '%Depth limit of 100 exceeded.%' THEN 'Depth limit exceeded'
                WHEN info LIKE '%PC size limit exceeded%' THEN 'PC size limit exceeded'
                WHEN info LIKE '%Failed to collect input/output specification for unknown reason%' THEN 'Failed to collect specification'
                WHEN info LIKE '%Execution timeout exceeded%' THEN 'Execution timeout'
                WHEN info LIKE '%java.lang.OutOfMemoryError: Java heap spac%' THEN 'OutOfMemoryError: Java heap space'
                WHEN info LIKE '%java.lang.OutOfMemoryError: GC overhead limit exceeded%' THEN 'OutOfMemoryError: GC overhead'
                WHEN info LIKE '%org.opentest4j.AssertionFailedError%' THEN 'AssertionFailedError'
                WHEN info LIKE '%gov.nasa.jpf.vm.NoUncaughtExceptionsProperty%' THEN 'NoUncaughtExceptionsProperty'
                WHEN info = E'java.lang.ArrayIndexOutOfBoundsException\n' THEN 'ArrayIndexOutOfBoundsException (simple)'
                WHEN info = E'java.lang.NullPointerException\n' THEN 'NullPointerException (simple)'
                ELSE 'Other failures'
            END AS error_category
        FROM task
        WHERE status = 'FAILED' AND stage = 'EXECUTE_JPF'
    )
SELECT
    error_category,
    count(*) AS count
FROM categorized_tasks
GROUP BY error_category
ORDER BY count DESC
WITH DATA;
