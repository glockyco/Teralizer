-- Dialect: SQLite

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    path      TEXT NOT NULL,
    classpath TEXT -- can be null for invalid project paths
);

CREATE TABLE test
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                INTEGER NOT NULL,
    test_class_path           TEXT NOT NULL,
    test_class_package        TEXT NOT NULL,
    test_class_name           TEXT NOT NULL,
    test_method_name          TEXT NOT NULL,
    tested_class_path         TEXT NOT NULL,
    tested_class_package      TEXT NOT NULL,
    tested_class_name         TEXT NOT NULL,
    tested_method_name        TEXT NOT NULL,
    tested_method_param_types TEXT NOT NULL,
    tested_method_return_type TEXT NOT NULL,
    driver_class_path         TEXT NOT NULL,
    driver_class_package      TEXT NOT NULL,
    driver_class_name         TEXT NOT NULL,
    jpf_config_path           TEXT NOT NULL,
    input_specification_path  TEXT NOT NULL,
    output_specification_path TEXT NOT NULL
);

CREATE TABLE generalization
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id                   INTEGER NOT NULL,
    tool                      TEXT NOT NULL,
    generalized_class_path    TEXT NOT NULL,
    generalized_class_package TEXT NOT NULL,
    generalized_class_name    TEXT NOT NULL
);

CREATE TABLE task
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id        INTEGER, -- can be null for failed project setup tasks
    test_id           INTEGER, -- can be null for project-level tasks
    generalization_id INTEGER, -- can be null for project-/test-level tasks
    step              INTEGER NOT NULL,
    stage             TEXT NOT NULL,
    status            TEXT NOT NULL,
    runtime           REAL, -- can be null for failed tasks
    error             TEXT -- can be null for successful tasks
);
