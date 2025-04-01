-- Dialect: SQLite

DROP VIEW IF EXISTS pit_mutation_report_view;
DROP VIEW IF EXISTS pit_coverage_report_view;
DROP VIEW IF EXISTS jacoco_coverage_report_view;
DROP VIEW IF EXISTS junit_test_report_view;

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS pit_mutation_report;
DROP TABLE IF EXISTS pit_coverage_report;
DROP TABLE IF EXISTS jacoco_coverage_report;
DROP TABLE IF EXISTS junit_test_report;
DROP TABLE IF EXISTS evosuite_report;
DROP TABLE IF EXISTS evosuite_runtime;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS assertion;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id                      INTEGER PRIMARY KEY AUTOINCREMENT,
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
    use_test_generation     INTEGER NOT NULL,
    use_test_generalization INTEGER NOT NULL,
    configuration           TEXT    NOT NULL,
    runtime                 REAL  -- can be null until the project is fully processed
);

CREATE INDEX idx_project_path ON project (root_path);
CREATE INDEX idx_project_type ON project (type);
CREATE INDEX idx_project_test_framework ON project (test_framework);

CREATE TABLE test
(
    id                           INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                   INTEGER NOT NULL,
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
    is_included                  INTEGER NOT NULL,
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
    id                                 INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                         INTEGER NOT NULL,
    test_id                            INTEGER NOT NULL,

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

    is_included                        INTEGER NOT NULL,
    exclusion_info                     TEXT, -- can be null for tests that are not excluded
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_assertion_project_id ON assertion (project_id);
CREATE INDEX idx_assertion_test_id ON assertion (test_id);

CREATE INDEX idx_assertion_is_included ON assertion (is_included);

CREATE TABLE generalization
(
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id             INTEGER NOT NULL,
    test_id                INTEGER NOT NULL,
    assertion_id           INTEGER NOT NULL,
    variant                TEXT    NOT NULL,

    file_path              TEXT    NOT NULL,
    class_qualified_name   TEXT    NOT NULL,
    method_qualified_name  TEXT    NOT NULL,
    package_name           TEXT    NOT NULL,
    class_name             TEXT    NOT NULL,
    method_name            TEXT    NOT NULL,

    total_constraint_count INTEGER, -- can be null for variants that do not process constraints
    used_constraint_count  INTEGER, -- can be null for variants that do not process constraints

    is_included            INTEGER NOT NULL,
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

CREATE TABLE evosuite_runtime
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    class_name TEXT    NOT NULL,
    step       INTEGER NOT NULL,
    phase_name TEXT    NOT NULL,
    runtime    REAL    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_evosuite_runtime_project_id ON evosuite_runtime (project_id);

CREATE TABLE evosuite_report
(
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id    INTEGER NOT NULL,
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
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id         INTEGER NOT NULL,
    test_id            INTEGER, -- can be null if the report is for the original test run or if the report is for a generalization
    generalization_id  INTEGER, -- can be null if the report is for the original test run or if the report is for a test
    step               INTEGER NOT NULL,
    stage              TEXT    NOT NULL,
    variant            TEXT,    -- can be null if the report is for the original test suite
    test_package_name  TEXT    NOT NULL,
    test_class_name    TEXT    NOT NULL,
    test_method_name   TEXT    NOT NULL,
    test_case_name     TEXT    NOT NULL,
    result             TEXT    NOT NULL,
    runtime            REAL    NOT NULL,
    failure_message    TEXT,    -- can be null for passed / skipped tests
    failure_type       TEXT,    -- can be null for passed / skipped tests
    failure_error_line TEXT,    -- can be null for passed / skipped tests
    failure_detail     TEXT,    -- can be null for passed / skipped tests
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
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id          INTEGER NOT NULL,
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
    id                         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                 INTEGER NOT NULL,
    test_id                    INTEGER, -- can be null if the report is for a generalization
    generalization_id          INTEGER, -- can be null if the report is for a test
    step                       INTEGER NOT NULL,
    stage                      TEXT    NOT NULL,
    variant                    TEXT,    -- can be null if the report is for the original test suite
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
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                INTEGER NOT NULL,
    killing_test_id           INTEGER, -- can be null if the mutant was not killed or was killed by a generalization
    killing_generalization_id INTEGER, -- can be null if the mutant was not killed or was killed by a test
    step                      INTEGER NOT NULL,
    stage                     TEXT    NOT NULL,
    variant                   TEXT,    -- can be null if the report is for the original test suite
    is_detected               INTEGER NOT NULL,
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
    killing_package_name      TEXT,    -- can be null if the mutant was not killed
    killing_class_name        TEXT,    -- can be null if the mutant was not killed
    killing_method_name       TEXT,    -- can be null if the mutant was not killed
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
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id        INTEGER, -- can be null for cross-project tasks (e.g., cleanup, data analysis)
    test_id           INTEGER, -- can be null for cross-project and project-level tasks
    assertion_id      INTEGER, -- can be null for cross-project and project-/test-level tasks
    generalization_id INTEGER, -- can be null for cross-project and project-/test-/assertion-level tasks
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

CREATE VIEW junit_test_report_view AS
SELECT
    id,
    project_id,
    test_id,
    generalization_id,
    step,
    stage,
    CASE
        WHEN stage LIKE '%ORIGINAL' THEN 'ORIGINAL'
        WHEN stage LIKE '%INITIAL' THEN 'INITIAL'
        ELSE variant
    END AS variant,
    test_package_name,
    test_class_name,
    test_method_name,
    test_case_name,
    result,
    runtime,
    failure_message,
    failure_type,
    failure_error_line,
    failure_detail,
    report_file_path
FROM junit_test_report;

CREATE VIEW jacoco_coverage_report_view AS
SELECT
    id,
    project_id,
    step,
    stage,
    CASE
        WHEN stage LIKE '%ORIGINAL' THEN 'ORIGINAL'
        WHEN stage LIKE '%INITIAL' THEN 'INITIAL'
        ELSE variant
    END AS variant,
    covered_package,
    covered_class,
    instruction_missed,
    instruction_covered,
    branch_missed,
    branch_covered,
    line_missed,
    line_covered,
    complexity_missed,
    complexity_covered,
    method_missed,
    method_covered
FROM jacoco_coverage_report;

CREATE VIEW pit_coverage_report_view AS
SELECT
    id,
    project_id,
    test_id,
    generalization_id,
    step,
    stage,
    CASE
        WHEN stage LIKE '%ORIGINAL' THEN 'ORIGINAL'
        WHEN stage LIKE '%INITIAL' THEN 'INITIAL'
        ELSE variant
    END AS variant,
    covered_package_name,
    covered_class_name,
    covered_method_name,
    covered_method_description,
    covered_block_number,
    test_package_name,
    test_class_name,
    test_method_name
FROM pit_coverage_report;

CREATE VIEW pit_mutation_report_view AS
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
    CASE
        WHEN stage LIKE '%ORIGINAL' THEN 'ORIGINAL'
        WHEN stage LIKE '%INITIAL' THEN 'INITIAL'
        ELSE variant
    END AS variant,
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
FROM pit_mutation_report;
