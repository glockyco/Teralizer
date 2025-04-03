-- Dialect: PostgreSQL

DROP VIEW mutation_results_by_project_variant_mutator;
DROP VIEW mutation_results_by_project_variant;
DROP VIEW mutation_results_by_variant_mutator;
DROP VIEW mutation_results_by_variant;

DROP FUNCTION project_name(project_id BIGINT);
DROP FUNCTION stage_order(stage TEXT);
DROP FUNCTION variant_order(variant TEXT);
DROP FUNCTION variant_name(stage TEXT, variant TEXT);

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS pit_mutation_report;
DROP TABLE IF EXISTS pit_coverage_report;
DROP TABLE IF EXISTS jacoco_coverage_report;
DROP TABLE IF EXISTS junit_test_report;
DROP TABLE IF EXISTS evosuite_report;
DROP TABLE IF EXISTS evosuite_runtime;
DROP TABLE IF EXISTS filter_result;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS assertion;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id                      BIGSERIAL PRIMARY KEY,
    type                    TEXT    NOT NULL,
    test_framework          TEXT, -- can be null for invalid project root paths
    test_framework_version  TEXT, -- can be null for invalid project root paths or if the test framework is UNKNOWN
    root_path               TEXT    NOT NULL,
    data_path               TEXT    NOT NULL,
    main_source_path        TEXT, -- can be null for invalid project root paths
    test_source_path        TEXT, -- can be null for invalid project root paths
    main_compiled_path      TEXT, -- can be null for invalid project root paths
    test_compiled_path      TEXT, -- can be null for invalid project root paths
    test_reports_path       TEXT, -- can be null for invalid project root paths
    coverage_reports_path   TEXT, -- can be null for invalid project root paths
    mutation_reports_path   TEXT, -- can be null for invalid project root paths
    classpath               TEXT, -- can be null for invalid project root paths
    git_version             TEXT, -- can be null for non-Git projects
    tool_git_version        TEXT, -- can be null because it does not matter if it is missing
    use_test_generation     BOOLEAN NOT NULL,
    use_test_generalization BOOLEAN NOT NULL,
    configuration           TEXT    NOT NULL,
    runtime                 REAL  -- can be null until the project is fully processed
);

CREATE INDEX idx_project_path ON project (root_path);
CREATE INDEX idx_project_type ON project (type);
CREATE INDEX idx_project_test_framework ON project (test_framework);

CREATE TABLE test
(
    id                           BIGSERIAL PRIMARY KEY,
    project_id                   BIGINT  NOT NULL,
    test_file_path               TEXT    NOT NULL,
    test_class_qualified_name    TEXT    NOT NULL,
    test_method_qualified_name   TEXT    NOT NULL,
    test_package_name            TEXT    NOT NULL,
    test_class_name              TEXT    NOT NULL,
    test_method_name             TEXT    NOT NULL,
    test_method_absolute_path    TEXT,    -- is null until JunitDataCollectionTask has mapped the test report to a method
    test_method_relative_path    TEXT,    -- is null until JunitDataCollectionTask has mapped the test report to a method
    test_annotation_name         TEXT,    -- is null until JunitDataCollectionTask has mapped the test report to a method and can remain null for unknown / unsupported test annotations
    test_annotations_source_code TEXT,    -- is null until JunitDataCollectionTask has mapped the test report to a method and can remain null for unknown / unsupported test annotations
    line_count                   INTEGER, -- is null until JunitDataCollectionTask has mapped the test report to a method
    is_included                  BOOLEAN NOT NULL,
    exclusion_info               TEXT,    -- can be null for tests that are not excluded

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_test_project_id ON test (project_id);

CREATE INDEX idx_test_test_file_path ON test (test_file_path);
CREATE INDEX idx_test_test_class_qualified_name ON test (test_class_qualified_name);
CREATE INDEX idx_test_test_method_qualified_name ON test (test_method_qualified_name);
CREATE INDEX idx_test_test_package_name ON test (test_package_name);
CREATE INDEX idx_test_test_class_name ON test (test_class_name);
CREATE INDEX idx_test_test_method_name ON test (test_method_name);

CREATE INDEX idx_test_is_included ON test (is_included);

CREATE TABLE assertion
(
    id                                 BIGSERIAL PRIMARY KEY,
    project_id                         BIGINT  NOT NULL,
    test_id                            BIGINT  NOT NULL,

    assertion_name                     TEXT    NOT NULL,
    assertion_arguments                TEXT    NOT NULL,
    assertion_source_code              TEXT    NOT NULL,
    assertion_absolute_path            TEXT    NOT NULL,
    assertion_relative_path            TEXT    NOT NULL,

    tested_file_path                   TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_class_qualified_name        TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_qualified_name       TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_package_name                TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_class_name                  TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_name                 TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_parameters           TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_return_type          TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_absolute_path        TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_relative_path        TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String

    tested_method_call_arguments       TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_call_source_code     TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_call_absolute_path   TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_method_call_relative_path   TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String

    instrumented_file_path             TEXT, -- can be null before JPF instrumentation
    instrumented_class_qualified_name  TEXT, -- can be null before JPF instrumentation
    instrumented_method_qualified_name TEXT, -- can be null before JPF instrumentation
    instrumented_package_name          TEXT, -- can be null before JPF instrumentation
    instrumented_class_name            TEXT, -- can be null before JPF instrumentation
    instrumented_method_name           TEXT, -- can be null before JPF instrumentation

    driver_file_path                   TEXT, -- can be null before JPF instrumentation
    driver_class_qualified_name        TEXT, -- can be null before JPF instrumentation
    driver_package_name                TEXT, -- can be null before JPF instrumentation
    driver_class_name                  TEXT, -- can be null before JPF instrumentation

    jpf_config_path                    TEXT, -- can be null before JPF instrumentation

    input_values_path                  TEXT, -- can be null before JPF execution
    output_value_path                  TEXT, -- can be null before JPF execution
    input_specification_path           TEXT, -- can be null before JPF execution
    output_specification_path          TEXT, -- can be null before JPF execution

    equivalent_assertions              TEXT, -- can be null before JPF analysis
    input_model_statistics             TEXT, -- can be null before JPF analysis
    output_model_statistics            TEXT, -- can be null before JPF analysis

    is_included                        BOOLEAN NOT NULL,
    exclusion_info                     TEXT, -- can be null for tests that are not excluded

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_assertion_project_id ON assertion (project_id);
CREATE INDEX idx_assertion_test_id ON assertion (test_id);

CREATE INDEX idx_assertion_is_included ON assertion (is_included);

CREATE TABLE generalization
(
    id                     BIGSERIAL PRIMARY KEY,
    project_id             BIGINT  NOT NULL,
    test_id                BIGINT  NOT NULL,
    assertion_id           BIGINT  NOT NULL,
    variant                TEXT    NOT NULL,

    file_path              TEXT    NOT NULL,
    class_qualified_name   TEXT    NOT NULL,
    method_qualified_name  TEXT    NOT NULL,
    package_name           TEXT    NOT NULL,
    class_name             TEXT    NOT NULL,
    method_name            TEXT    NOT NULL,

    total_constraint_count INTEGER, -- can be null for variants that do not process constraints
    used_constraint_count  INTEGER, -- can be null for variants that do not process constraints

    line_count             INTEGER NOT NULL,

    is_included            BOOLEAN NOT NULL,
    exclusion_info         TEXT,    -- can be null for generalizations that are not excluded

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (assertion_id) REFERENCES assertion (id) ON DELETE CASCADE
);

CREATE INDEX idx_generalization_project_id ON generalization (project_id);
CREATE INDEX idx_generalization_test_id ON generalization (test_id);
CREATE INDEX idx_generalization_assertion_id ON generalization (assertion_id);
CREATE INDEX idx_generalization_variant ON generalization (variant);

CREATE INDEX idx_generalization_file_path ON generalization (file_path);
CREATE INDEX idx_generalization_class_qualified_name ON generalization (class_qualified_name);
CREATE INDEX idx_generalization_method_qualified_name ON generalization (method_qualified_name);
CREATE INDEX idx_generalization_is_included ON generalization (is_included);

CREATE TABLE filter_result
(
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT NOT NULL,

    test_id           BIGINT, -- can be null if the filter result is for an assertion / generalization
    assertion_id      BIGINT, -- can be null if the filter result is for a test / generalization
    generalization_id BIGINT, -- can be null if the filter result is for a test / assertion

    filter_name       TEXT   NOT NULL,
    decision          TEXT   NOT NULL,
    reason            TEXT   NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (assertion_id) REFERENCES assertion (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_filter_result_project_id ON filter_result (project_id);
CREATE INDEX idx_filter_result_test_id ON filter_result (test_id);
CREATE INDEX idx_filter_result_assertion_id ON filter_result (assertion_id);
CREATE INDEX idx_filter_result_generalization_id ON filter_result (generalization_id);

CREATE INDEX idx_filter_result_decision ON filter_result (decision);

CREATE TABLE evosuite_runtime
(
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT  NOT NULL,
    class_name TEXT    NOT NULL,
    step       INTEGER NOT NULL,
    phase_name TEXT    NOT NULL,
    runtime    REAL    NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_evosuite_runtime_project_id ON evosuite_runtime (project_id);

CREATE TABLE evosuite_report
(
    id            BIGSERIAL PRIMARY KEY,
    project_id    BIGINT  NOT NULL,
    class_name    TEXT    NOT NULL,
    criterion     TEXT    NOT NULL,
    coverage      REAL    NOT NULL,
    total_goals   INTEGER NOT NULL,
    covered_goals INTEGER NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_evosuite_report_project_id ON evosuite_report (project_id);

CREATE TABLE junit_test_report
(
    id                 BIGSERIAL PRIMARY KEY,
    project_id         BIGINT  NOT NULL,
    test_id            BIGINT, -- can be null if the report is for the original test run or if the report is for a generalization
    generalization_id  BIGINT, -- can be null if the report is for the original test run or if the report is for a test
    step               INTEGER NOT NULL,
    stage              TEXT    NOT NULL,
    variant            TEXT,   -- can be null if the report is for the original test suite
    test_package_name  TEXT    NOT NULL,
    test_class_name    TEXT    NOT NULL,
    test_method_name   TEXT    NOT NULL,
    test_case_name     TEXT    NOT NULL,
    result             TEXT    NOT NULL,
    runtime            REAL    NOT NULL,
    failure_message    TEXT,   -- can be null for passed / skipped tests
    failure_type       TEXT,   -- can be null for passed / skipped tests
    failure_error_line TEXT,   -- can be null for passed / skipped tests
    failure_detail     TEXT,   -- can be null for passed / skipped tests
    report_file_path   TEXT    NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_junit_test_report_project_id ON junit_test_report (project_id);
CREATE INDEX idx_junit_test_report_test_id ON junit_test_report (test_id);
CREATE INDEX idx_junit_test_report_generalization_id ON junit_test_report (generalization_id);

CREATE INDEX idx_junit_test_report_step ON junit_test_report (step);
CREATE INDEX idx_junit_test_report_stage ON junit_test_report (stage);
CREATE INDEX idx_junit_test_report_variant ON junit_test_report (variant);

CREATE INDEX idx_junit_test_report_result ON junit_test_report (result);

CREATE TABLE jacoco_coverage_report
(
    id                  BIGSERIAL PRIMARY KEY,
    project_id          BIGINT  NOT NULL,
    step                INTEGER NOT NULL,
    stage               TEXT    NOT NULL,
    variant             TEXT, -- can be null if the report is for the original test suite
    covered_package     TEXT    NOT NULL,
    covered_class       TEXT    NOT NULL,
    instruction_missed  INTEGER NOT NULL,
    instruction_covered INTEGER NOT NULL,
    branch_missed       INTEGER NOT NULL,
    branch_covered      INTEGER NOT NULL,
    line_missed         INTEGER NOT NULL,
    line_covered        INTEGER NOT NULL,
    complexity_missed   INTEGER NOT NULL,
    complexity_covered  INTEGER NOT NULL,
    method_missed       INTEGER NOT NULL,
    method_covered      INTEGER NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_jacoco_coverage_report_project_id ON jacoco_coverage_report (project_id);

CREATE INDEX idx_jacoco_coverage_report_step ON jacoco_coverage_report (step);
CREATE INDEX idx_jacoco_coverage_report_stage ON jacoco_coverage_report (stage);
CREATE INDEX idx_jacoco_coverage_report_variant ON jacoco_coverage_report (variant);

CREATE INDEX idx_jacoco_coverage_report_package ON jacoco_coverage_report (covered_package);
CREATE INDEX idx_jacoco_coverage_report_class ON jacoco_coverage_report (covered_class);

CREATE TABLE pit_coverage_report
(
    id                         BIGSERIAL PRIMARY KEY,
    project_id                 BIGINT  NOT NULL,
    test_id                    BIGINT, -- can be null if the report is for a generalization
    generalization_id          BIGINT, -- can be null if the report is for a test
    step                       INTEGER NOT NULL,
    stage                      TEXT    NOT NULL,
    variant                    TEXT,   -- can be null if the report is for the original test suite
    covered_package_name       TEXT    NOT NULL,
    covered_class_name         TEXT    NOT NULL,
    covered_method_name        TEXT    NOT NULL,
    covered_method_description TEXT    NOT NULL,
    covered_block_number       INTEGER NOT NULL,
    test_package_name          TEXT    NOT NULL,
    test_class_name            TEXT    NOT NULL,
    test_method_name           TEXT    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_pit_coverage_report_project_id ON pit_coverage_report (project_id);
CREATE INDEX idx_pit_coverage_report_test_id ON pit_coverage_report (test_id);
CREATE INDEX idx_pit_coverage_report_generalization_id ON pit_coverage_report (generalization_id);

CREATE INDEX idx_pit_coverage_report_step ON pit_coverage_report (step);
CREATE INDEX idx_pit_coverage_report_stage ON pit_coverage_report (stage);
CREATE INDEX idx_pit_coverage_report_variant ON pit_coverage_report (variant);

CREATE TABLE pit_mutation_report
(
    id                        BIGSERIAL PRIMARY KEY,
    project_id                BIGINT  NOT NULL,
    killing_test_id           BIGINT, -- can be null if the mutant was not killed or was killed by a generalization
    killing_generalization_id BIGINT, -- can be null if the mutant was not killed or was killed by a test
    step                      INTEGER NOT NULL,
    stage                     TEXT    NOT NULL,
    variant                   TEXT,   -- can be null if the report is for the original test suite
    is_detected               BOOLEAN NOT NULL,
    status                    TEXT    NOT NULL,
    number_of_tests_run       INTEGER NOT NULL,
    source_file               TEXT    NOT NULL,
    mutated_class             TEXT    NOT NULL,
    mutated_method            TEXT    NOT NULL,
    method_description        TEXT    NOT NULL,
    line_number               INTEGER NOT NULL,
    mutator                   TEXT    NOT NULL,
    indexes                   TEXT    NOT NULL,
    blocks                    TEXT    NOT NULL,
    killing_package_name      TEXT,   -- can be null if the mutant was not killed
    killing_class_name        TEXT,   -- can be null if the mutant was not killed
    killing_method_name       TEXT,   -- can be null if the mutant was not killed
    description               TEXT    NOT NULL,

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (killing_test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (killing_generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_pit_mutation_report_project_id ON pit_mutation_report (project_id);
CREATE INDEX idx_pit_mutation_report_killing_test_id ON pit_mutation_report (killing_test_id);
CREATE INDEX idx_pit_mutation_report_killing_generalization_id ON pit_mutation_report (killing_generalization_id);

CREATE INDEX idx_pit_mutation_report_step ON pit_mutation_report (step);
CREATE INDEX idx_pit_mutation_report_stage ON pit_mutation_report (stage);
CREATE INDEX idx_pit_mutation_report_variant ON pit_mutation_report (variant);

CREATE INDEX idx_pit_mutation_report_is_detected ON pit_mutation_report (is_detected);
CREATE INDEX idx_pit_mutation_report_mutated_class ON pit_mutation_report (mutated_class);
CREATE INDEX idx_pit_mutation_report_mutated_method ON pit_mutation_report (mutated_method);

CREATE TABLE task
(
    id                BIGSERIAL PRIMARY KEY,
    project_id        BIGINT,  -- can be null for cross-project tasks (e.g., cleanup, data analysis)
    test_id           BIGINT,  -- can be null for cross-project and project-level tasks
    assertion_id      BIGINT,  -- can be null for cross-project and project-/test-level tasks
    generalization_id BIGINT,  -- can be null for cross-project and project-/test-/assertion-level tasks
    step              INTEGER, -- can be null for one-off tasks that are not part of the normal processing flow (e.g., cleanup)
    stage             TEXT NOT NULL,
    variant           TEXT,    -- can be null for cross-variant tasks (e.g., JPF instrumentation + execution)
    status            TEXT NOT NULL,
    runtime           REAL,    -- can be null for failed tasks
    info              TEXT,    -- can be null for tasks that have nothing special to report

    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (assertion_id) REFERENCES assertion (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_task_project_id ON task (project_id);
CREATE INDEX idx_task_test_id ON task (test_id);
CREATE INDEX idx_task_assertion_id ON task (assertion_id);
CREATE INDEX idx_task_generalization_id ON task (generalization_id);

CREATE INDEX idx_task_step ON task (step);
CREATE INDEX idx_task_stage ON task (stage);
CREATE INDEX idx_task_variant ON task (variant);

CREATE INDEX idx_task_status ON task (status);

CREATE FUNCTION project_name(project_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    root_path TEXT;
    project_name TEXT;
BEGIN
    SELECT p.root_path INTO root_path
    FROM project p
    WHERE p.id = project_id;

    IF root_path IS NULL THEN
        RETURN NULL;
    END IF;

    -- Extract everything after the last '/'.
    project_name := regexp_replace(root_path, '^.*/([^/]+)$', '\1');

    -- If the regex didn't match but the path contains '/' something went wrong.
    IF project_name = root_path AND root_path LIKE '%/%' THEN
        RAISE EXCEPTION 'Failed to extract project name from path: %', root_path;
    END IF;

    -- If there is no '/' in the path, the entire path is the project name.
    RETURN project_name;END;
$$ LANGUAGE plpgsql STABLE;

CREATE FUNCTION variant_name(stage TEXT, variant TEXT)
RETURNS TEXT AS $$
BEGIN
    IF variant IS NOT NULL THEN
        RETURN variant;
    END IF;

    IF stage LIKE '%ORIGINAL' THEN
        RETURN 'ORIGINAL';
    ELSIF stage LIKE '%INITIAL' THEN
        RETURN 'INITIAL';
    ELSE
        RETURN NULL;
    END IF;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE FUNCTION variant_order(variant TEXT)
RETURNS INTEGER AS $$
DECLARE
    base_order INTEGER;
    tries_number INTEGER := 0;
    base_variant TEXT;
BEGIN
    IF variant IS NULL THEN
        RETURN 0;
    END IF;

    IF variant ~ '_[0-9]+_TRIES$' THEN
        -- Extract the number between _ and _TRIES
        tries_number := CAST(regexp_replace(variant, '^.*_([0-9]+)_TRIES$', '\1') AS INTEGER);
        -- Extract base variant name by removing the _X_TRIES suffix
        base_variant := regexp_replace(variant, '(_[0-9]+_TRIES)$', '');
    ELSE
        base_variant := variant;
    END IF;

    base_order := CASE
        WHEN base_variant = 'ORIGINAL' THEN 10000000
        WHEN base_variant = 'INITIAL' THEN 20000000
        WHEN base_variant = 'BASELINE' THEN 30000000
        WHEN base_variant = 'NAIVE' THEN 40000000
        WHEN base_variant = 'IMPROVED' THEN 60000000
    END;

    IF base_order IS NULL THEN
        RAISE EXCEPTION 'Unknown variant type: %', base_variant;
    END IF;

    RETURN base_order + tries_number;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE FUNCTION stage_order(stage TEXT)
RETURNS INTEGER AS $$
DECLARE
    stage_order INTEGER;
BEGIN
    IF stage IS NULL THEN
        RETURN -1;
    END IF;

    stage_order := CASE
        WHEN stage = 'CLEANUP_PROJECT' THEN 0

        WHEN stage = 'DOWNLOAD_PROJECT' THEN 1
        WHEN stage = 'SETUP_PROJECT' THEN 2

        WHEN stage = 'ADD_DEPENDENCIES' THEN 3
        WHEN stage = 'BUILD_PROJECT_ORIGINAL' THEN 4

        WHEN stage = 'GENERATE_EVOSUITE_TESTS' THEN 5
        WHEN stage = 'POSTPROCESS_EVOSUITE_TESTS' THEN 6

        WHEN stage = 'BUILD_SPOON_MODEL' THEN 7

        WHEN stage = 'EXECUTE_TESTS_ORIGINAL' THEN 8
        WHEN stage = 'COLLECT_JUNIT_REPORTS_ORIGINAL' THEN 9
        WHEN stage = 'COLLECT_JACOCO_DATA_ORIGINAL' THEN 10
        WHEN stage = 'FILTER_TESTS_ORIGINAL' THEN 11
        WHEN stage = 'COLLECT_PIT_DATA_ORIGINAL' THEN 12

        WHEN stage = 'ANALYZE_TESTS' THEN 13
        WHEN stage = 'FILTER_TESTS' THEN 14
        WHEN stage = 'FILTER_ASSERTIONS' THEN 15

        WHEN stage = 'ADD_JPF_INSTRUMENTATION' THEN 16
        WHEN stage = 'BUILD_PROJECT_INSTRUMENTED' THEN 17
        WHEN stage = 'EXECUTE_JPF' THEN 18
        WHEN stage = 'ANALYZE_JPF' THEN 19
        WHEN stage = 'CLEANUP_JPF_INSTRUMENTATION' THEN 20

        WHEN stage = 'BUILD_PROJECT_INITIAL' THEN 21
        WHEN stage = 'EXECUTE_TESTS_INITIAL' THEN 22

        WHEN stage = 'COLLECT_JUNIT_REPORTS_INITIAL' THEN 23
        WHEN stage = 'COLLECT_JACOCO_DATA_INITIAL' THEN 24
        WHEN stage = 'COLLECT_PIT_DATA_INITIAL' THEN 25

        WHEN stage = 'CLEANUP_GENERALIZATION' THEN 26

        WHEN stage = 'GENERALIZE_TESTS' THEN 27
        WHEN stage = 'BUILD_PROJECT_GENERALIZED' THEN 28

        WHEN stage = 'EXECUTE_TESTS_GENERALIZED' THEN 29
        WHEN stage = 'COLLECT_JUNIT_REPORTS_GENERALIZED' THEN 30
        WHEN stage = 'FILTER_GENERALIZATIONS' THEN 31

        WHEN stage = 'COLLECT_JACOCO_DATA_GENERALIZED' THEN 32
        WHEN stage = 'COLLECT_PIT_DATA_GENERALIZED' THEN 33
    END;

    IF stage_order IS NULL THEN
        RAISE EXCEPTION 'Unknown stage: %', stage;
    END IF;

    RETURN stage_order;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE VIEW mutation_results_by_variant AS
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
    variant_order(s.variant);

CREATE VIEW mutation_results_by_variant_mutator AS
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
    variant_order(s.variant), s.total DESC, s.mutator;

CREATE VIEW mutation_results_by_project_variant AS
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
    s.project_id, variant_order(s.variant);

CREATE VIEW mutation_results_by_project_variant_mutator AS
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
    s.project_id, variant_order(s.variant), s.total DESC, s.mutator;
