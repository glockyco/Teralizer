-- Dialect: SQLite

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    path      TEXT,
    classpath TEXT
);

CREATE TABLE test
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                INTEGER,
    test_class_path           TEXT,
    test_class_package        TEXT,
    test_class_name           TEXT,
    test_method_name          TEXT,
    tested_class_path         TEXT,
    tested_class_package      TEXT,
    tested_class_name         TEXT,
    tested_method_name        TEXT,
    tested_method_param_types TEXT,
    tested_method_return_type TEXT,
    driver_class_path         TEXT,
    driver_class_package      TEXT,
    driver_class_name         TEXT,
    jpf_config_path           TEXT,
    input_specification_path  TEXT,
    output_specification_path TEXT
);

CREATE TABLE generalization
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id                   INTEGER,
    tool                      TEXT,
    generalized_class_path    TEXT,
    generalized_class_package TEXT,
    generalized_class_name    TEXT
);

CREATE TABLE task
(
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    step       INTEGER,
    task       TEXT,
    status     TEXT,
    runtime    REAL,
    error      TEXT
);
