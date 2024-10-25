-- Dialect: SQLite

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS mutation_report;
DROP TABLE IF EXISTS coverage_report;
DROP TABLE IF EXISTS test_report;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS assertion;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    type                   TEXT NOT NULL,
    test_framework         TEXT, -- can be null for invalid project root paths
    test_framework_version TEXT, -- can be null for invalid project root paths or if the test framework is UNKNOWN
    root_path              TEXT NOT NULL,
    data_path              TEXT NOT NULL,
    main_source_path       TEXT, -- can be null for invalid project root paths
    test_source_path       TEXT, -- can be null for invalid project root paths
    main_compiled_path     TEXT, -- can be null for invalid project root paths
    test_compiled_path     TEXT, -- can be null for invalid project root paths
    test_reports_path      TEXT, -- can be null for invalid project root paths
    coverage_reports_path  TEXT, -- can be null for invalid project root paths
    mutation_reports_path  TEXT, -- can be null for invalid project root paths
    classpath              TEXT, -- can be null for invalid project root paths
    runtime                REAL  -- can be null until the project is fully processed
);

CREATE INDEX idx_project_path ON project (root_path);
CREATE INDEX idx_project_type ON project (type);
CREATE INDEX idx_project_test_framework ON project (test_framework);

CREATE TABLE test
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                INTEGER NOT NULL,
    test_class_path           TEXT    NOT NULL,
    test_class_package        TEXT    NOT NULL,
    test_class_name           TEXT    NOT NULL,
    test_method_name          TEXT    NOT NULL,
    tested_class_path         TEXT, -- can be null if we cannot identify a tested class / method or if the tested class / method is from a JDK type such as java.lang.String
    tested_class_package      TEXT, -- can be null if we cannot identify a tested class / method
    tested_class_name         TEXT, -- can be null if we cannot identify a tested class / method
    tested_method_name        TEXT, -- can be null if we cannot identify a tested class / method
    tested_method_param_types TEXT, -- can be null if we cannot identify a tested class / method
    tested_method_return_type TEXT, -- can be null if we cannot identify a tested class / method
    driver_class_path         TEXT    NOT NULL,
    driver_class_package      TEXT    NOT NULL,
    driver_class_name         TEXT    NOT NULL,
    jpf_config_path           TEXT    NOT NULL,
    input_specification_path  TEXT    NOT NULL,
    output_specification_path TEXT    NOT NULL,
    is_included               INTEGER NOT NULL,
    exclusion_info            TEXT, -- can be null for tests that are not excluded
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_test_project_id ON test (project_id);
CREATE INDEX idx_test_is_included ON test (is_included);

CREATE TABLE assertion
(
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id               INTEGER NOT NULL,
    method_name           TEXT    NOT NULL,
    method_argument_types TEXT    NOT NULL,
    source_code           TEXT    NOT NULL,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_assertion_test_id ON assertion (test_id);

CREATE TABLE generalization
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id                   INTEGER NOT NULL,
    variant                   TEXT    NOT NULL,
    generalized_class_path    TEXT    NOT NULL,
    generalized_class_package TEXT    NOT NULL,
    generalized_class_name    TEXT    NOT NULL,
    is_included               INTEGER NOT NULL,
    exclusion_info            TEXT, -- can be null for generalizations that are not excluded
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_generalization_test_id ON generalization (test_id);
CREATE INDEX idx_generalization_variant ON generalization (variant);
CREATE INDEX idx_generalization_is_included ON generalization (is_included);

CREATE TABLE test_report
(
    id                 INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id            INTEGER, -- can be null if the report is for a generalization
    generalization_id  INTEGER, -- can be null if the report is for a test
    result             INTEGER NOT NULL,
    runtime            REAL    NOT NULL,
    failure_message    TEXT,    -- can be null for passed / skipped tests
    failure_type       TEXT,    -- can be null for passed / skipped tests
    failure_error_line TEXT,    -- can be null for passed / skipped tests
    failure_detail     TEXT,    -- can be null for passed / skipped tests
    report_path        TEXT    NOT NULL,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_test_report_test_id ON test_report (test_id);
CREATE INDEX idx_test_report_generalization_id ON test_report (generalization_id);
CREATE INDEX idx_test_report_result ON test_report (result);

CREATE TABLE coverage_report
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id          INTEGER NOT NULL,
    variant             TEXT, -- can be null if the report is for the original test suite
    covered_package     TEXT    NOT NULL,
    covered_class       TEXT    NOT NULL,
    instruction_missed  TEXT    NOT NULL,
    instruction_covered TEXT    NOT NULL,
    branch_missed       TEXT    NOT NULL,
    branch_covered      TEXT    NOT NULL,
    line_missed         TEXT    NOT NULL,
    line_covered        TEXT    NOT NULL,
    complexity_missed   TEXT    NOT NULL,
    complexity_covered  TEXT    NOT NULL,
    method_missed       TEXT    NOT NULL,
    method_covered      TEXT    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_coverage_report_project_id ON coverage_report (project_id);
CREATE INDEX idx_coverage_report_variant ON coverage_report (variant);

CREATE INDEX idx_coverage_report_package ON coverage_report (covered_package);
CREATE INDEX idx_coverage_report_class ON coverage_report (covered_class);

CREATE TABLE mutation_report
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id          INTEGER NOT NULL,
    variant             TEXT, -- can be null if the report is for the original test suite
    is_detected         INTEGER NOT NULL,
    status              TEXT    NOT NULL,
    number_of_tests_run INTEGER NOT NULL,
    source_file         TEXT    NOT NULL,
    mutated_class       TEXT    NOT NULL,
    mutated_method      TEXT    NOT NULL,
    method_description  TEXT    NOT NULL,
    line_number         INTEGER NOT NULL,
    mutator             TEXT    NOT NULL,
    -- @TODO: indexes
    -- @TODO: blocks
    -- @TODO: killing_tests
    description         TEXT    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_mutation_report_project_id ON mutation_report (project_id);
CREATE INDEX idx_mutation_report_variant ON mutation_report (variant);

CREATE INDEX idx_mutation_report_is_detected ON mutation_report (is_detected);
CREATE INDEX idx_mutation_report_mutated_class ON mutation_report (mutated_class);
CREATE INDEX idx_mutation_report_mutated_method ON mutation_report (mutated_method);

CREATE TABLE task
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id        INTEGER, -- can be null for cross-project tasks (e.g., cleanup, data analysis)
    test_id           INTEGER, -- can be null for cross-project and project-level tasks
    generalization_id INTEGER, -- can be null for cross-project and project-/test-level tasks
    step              INTEGER, -- can be null for one-off tasks that are not part of the normal processing flow (e.g., cleanup)
    stage             TEXT NOT NULL,
    variant           TEXT,    -- can be null for cross-variant tasks (e.g., JPF instrumentation + execution)
    status            TEXT NOT NULL,
    runtime           REAL,    -- can be null for failed tasks
    info              TEXT,    -- can be null for tasks that have nothing special to report
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE,
    FOREIGN KEY (generalization_id) REFERENCES generalization (id) ON DELETE CASCADE
);

CREATE INDEX idx_task_project_id ON task (project_id);
CREATE INDEX idx_task_test_id ON task (test_id);
CREATE INDEX idx_task_generalization_id ON task (generalization_id);

CREATE INDEX idx_task_step ON task (step);
CREATE INDEX idx_task_stage ON task (stage);
CREATE INDEX idx_task_variant ON task (variant);
CREATE INDEX idx_task_status ON task (status);
