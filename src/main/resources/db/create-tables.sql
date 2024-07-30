-- Dialect: SQLite

DROP TABLE IF EXISTS task;
DROP TABLE IF EXISTS generalization;
DROP TABLE IF EXISTS test;
DROP TABLE IF EXISTS project;

CREATE TABLE project
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    path      TEXT NOT NULL,
    classpath TEXT, -- can be null for invalid project paths
    runtime   REAL  -- can be null until the project is fully processed
);

CREATE INDEX idx_project_path ON project (path);

CREATE TABLE test
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id                INTEGER NOT NULL,
    test_class_path           TEXT    NOT NULL,
    test_class_package        TEXT    NOT NULL,
    test_class_name           TEXT    NOT NULL,
    test_method_name          TEXT    NOT NULL,
    tested_class_path         TEXT    NOT NULL,
    tested_class_package      TEXT    NOT NULL,
    tested_class_name         TEXT    NOT NULL,
    tested_method_name        TEXT    NOT NULL,
    tested_method_param_types TEXT    NOT NULL,
    tested_method_return_type TEXT    NOT NULL,
    driver_class_path         TEXT    NOT NULL,
    driver_class_package      TEXT    NOT NULL,
    driver_class_name         TEXT    NOT NULL,
    jpf_config_path           TEXT    NOT NULL,
    input_specification_path  TEXT    NOT NULL,
    output_specification_path TEXT    NOT NULL,
    FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
);

CREATE INDEX idx_test_project_id ON test (project_id);

CREATE TABLE generalization
(
    id                        INTEGER PRIMARY KEY AUTOINCREMENT,
    test_id                   INTEGER NOT NULL,
    tool                      TEXT    NOT NULL,
    generalized_class_path    TEXT    NOT NULL,
    generalized_class_package TEXT    NOT NULL,
    generalized_class_name    TEXT    NOT NULL,
    FOREIGN KEY (test_id) REFERENCES test (id) ON DELETE CASCADE
);

CREATE INDEX idx_generalization_test_id ON generalization (test_id);
CREATE INDEX idx_generalization_tool ON generalization (tool);

CREATE TABLE task
(
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id        INTEGER, -- can be null for cross-project tasks (e.g., cleanup, data analysis)
    test_id           INTEGER, -- can be null for cross-project and project-level tasks
    generalization_id INTEGER, -- can be null for cross-project and project-/test-level tasks
    step              INTEGER, -- can be null for one-off tasks that are not part of the normal processing flow (e.g., cleanup)
    stage             TEXT NOT NULL,
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
CREATE INDEX idx_task_status ON task (status);
