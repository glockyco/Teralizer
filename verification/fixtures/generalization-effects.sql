BEGIN;

INSERT INTO project (
  id,
  type,
  root_path,
  data_path,
  use_test_generation,
  use_test_generalization,
  use_test_reduction,
  configuration
) VALUES (
  -100,
  'CI',
  '/fixture/generalization-effects',
  '/data/generalization-effects',
  false,
  true,
  true,
  '{}'
);

INSERT INTO test (
  id,
  project_id,
  test_file_path,
  test_class_qualified_name,
  test_method_qualified_name,
  test_package_name,
  test_class_name,
  test_method_name,
  line_count,
  is_included
) VALUES
  (-101, -100, 'SingleTest.java', 'fixture.SingleTest', 'fixture.SingleTest.single', 'fixture', 'SingleTest', 'single', 10, true),
  (-102, -100, 'CompleteTest.java', 'fixture.CompleteTest', 'fixture.CompleteTest.complete', 'fixture', 'CompleteTest', 'complete', 20, true),
  (-103, -100, 'PartialTest.java', 'fixture.PartialTest', 'fixture.PartialTest.partial', 'fixture', 'PartialTest', 'partial', 30, true),
  (-104, -100, 'RedundantTest.java', 'fixture.RedundantTest', 'fixture.RedundantTest.redundant', 'fixture', 'RedundantTest', 'redundant', 40, true);

INSERT INTO assertion (
  id,
  project_id,
  test_id,
  assertion_name,
  assertion_arguments,
  assertion_source_code,
  assertion_absolute_path,
  assertion_relative_path,
  is_included
) VALUES
  (-201, -100, -101, 'assertEquals', 'single', 'assertEquals(single)', '/fixture/SingleTest.java', 'SingleTest.java', true),
  (-202, -100, -102, 'assertEquals', 'complete-1', 'assertEquals(complete1)', '/fixture/CompleteTest.java', 'CompleteTest.java', true),
  (-203, -100, -102, 'assertEquals', 'complete-2', 'assertEquals(complete2)', '/fixture/CompleteTest.java', 'CompleteTest.java', true),
  (-204, -100, -103, 'assertEquals', 'partial-1', 'assertEquals(partial1)', '/fixture/PartialTest.java', 'PartialTest.java', true),
  (-205, -100, -103, 'assertEquals', 'partial-2', 'assertEquals(partial2)', '/fixture/PartialTest.java', 'PartialTest.java', true),
  (-206, -100, -104, 'assertEquals', 'redundant', 'assertEquals(redundant)', '/fixture/RedundantTest.java', 'RedundantTest.java', true);

INSERT INTO generalization (
  id,
  project_id,
  test_id,
  assertion_id,
  variant,
  file_path,
  class_qualified_name,
  method_qualified_name,
  package_name,
  class_name,
  method_name,
  line_count,
  is_included
) VALUES
  (-301, -100, -101, -201, 'IMPROVED_10_TRIES', 'SingleProperty.java', 'fixture.SingleProperty', 'fixture.SingleProperty.single', 'fixture', 'SingleProperty', 'single', 3, true),
  (-302, -100, -102, -202, 'IMPROVED_10_TRIES', 'CompleteOneProperty.java', 'fixture.CompleteOneProperty', 'fixture.CompleteOneProperty.completeOne', 'fixture', 'CompleteOneProperty', 'completeOne', 4, true),
  (-303, -100, -102, -203, 'IMPROVED_10_TRIES', 'CompleteTwoProperty.java', 'fixture.CompleteTwoProperty', 'fixture.CompleteTwoProperty.completeTwo', 'fixture', 'CompleteTwoProperty', 'completeTwo', 5, true),
  (-304, -100, -103, -204, 'IMPROVED_10_TRIES', 'PartialProperty.java', 'fixture.PartialProperty', 'fixture.PartialProperty.partial', 'fixture', 'PartialProperty', 'partial', 6, true),
  (-305, -100, -104, -206, 'IMPROVED_10_TRIES', 'RedundantProperty.java', 'fixture.RedundantProperty', 'fixture.RedundantProperty.redundant', 'fixture', 'RedundantProperty', 'redundant', 7, true);

INSERT INTO junit_test_report (
  id,
  project_id,
  test_id,
  generalization_id,
  step,
  stage,
  variant,
  test_package_name,
  test_class_name,
  test_method_name,
  test_case_name,
  result,
  runtime,
  report_file_path
) VALUES
  (-401, -100, -101, NULL, 9, 'COLLECT_JUNIT_REPORTS_ORIGINAL', NULL, 'fixture', 'SingleTest', 'single', 'single', 'PASSED', 1.0, 'original.xml'),
  (-402, -100, -102, NULL, 9, 'COLLECT_JUNIT_REPORTS_ORIGINAL', NULL, 'fixture', 'CompleteTest', 'complete', 'complete', 'PASSED', 2.0, 'original.xml'),
  (-403, -100, -103, NULL, 9, 'COLLECT_JUNIT_REPORTS_ORIGINAL', NULL, 'fixture', 'PartialTest', 'partial', 'partial', 'PASSED', 3.0, 'original.xml'),
  (-404, -100, -104, NULL, 9, 'COLLECT_JUNIT_REPORTS_ORIGINAL', NULL, 'fixture', 'RedundantTest', 'redundant', 'redundant', 'PASSED', 4.0, 'original.xml'),
  (-411, -100, NULL, -301, 26, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_10_TRIES', 'fixture', 'SingleProperty', 'single', 'single', 'PASSED', 0.1, 'generalized.xml'),
  (-412, -100, NULL, -302, 26, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_10_TRIES', 'fixture', 'CompleteOneProperty', 'completeOne', 'completeOne', 'PASSED', 0.2, 'generalized.xml'),
  (-413, -100, NULL, -303, 26, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_10_TRIES', 'fixture', 'CompleteTwoProperty', 'completeTwo', 'completeTwo', 'PASSED', 0.3, 'generalized.xml'),
  (-414, -100, NULL, -304, 26, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_10_TRIES', 'fixture', 'PartialProperty', 'partial', 'partial', 'PASSED', 0.4, 'generalized.xml'),
  (-415, -100, NULL, -305, 26, 'COLLECT_JUNIT_REPORTS_GENERALIZED', 'IMPROVED_10_TRIES', 'fixture', 'RedundantProperty', 'redundant', 'redundant', 'PASSED', 0.5, 'generalized.xml');

INSERT INTO pit_mutation_report (
  id,
  project_id,
  killing_test_id,
  killing_generalization_id,
  step,
  stage,
  variant,
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
  killing_package_name,
  killing_class_name,
  killing_method_name,
  description
)
SELECT
  -500 - pair.mutation * 2 - run.variant_offset,
  -100,
  CASE WHEN pair.mutation = 5 AND run.variant_offset = 0 THEN -104 ELSE NULL END,
  CASE WHEN run.variant_offset = 1 THEN -300 - pair.mutation ELSE NULL END,
  CASE WHEN run.variant_offset = 0 THEN 30 ELSE 35 END,
  CASE WHEN run.variant_offset = 0 THEN 'COLLECT_PIT_DATA_ORIGINAL' ELSE 'COLLECT_PIT_DATA_GENERALIZED' END,
  CASE WHEN run.variant_offset = 0 THEN NULL ELSE 'IMPROVED_10_TRIES' END,
  pair.mutation = 5 OR run.variant_offset = 1,
  CASE WHEN pair.mutation = 5 OR run.variant_offset = 1 THEN 'KILLED' ELSE 'SURVIVED' END,
  1,
  'Subject.java',
  'fixture',
  'Subject',
  'method',
  '()V',
  pair.mutation,
  'org.pitest.mutationtest.engine.gregor.mutators.ConditionalsBoundaryMutator',
  pair.mutation::text,
  pair.mutation::text,
  CASE WHEN pair.mutation = 5 OR run.variant_offset = 1 THEN 'fixture' ELSE NULL END,
  CASE WHEN pair.mutation = 5 OR run.variant_offset = 1 THEN 'Killer' ELSE NULL END,
  CASE WHEN pair.mutation = 5 OR run.variant_offset = 1 THEN 'kills' ELSE NULL END,
  'fixture mutation ' || pair.mutation
FROM generate_series(1, 5) AS pair(mutation)
CROSS JOIN generate_series(0, 1) AS run(variant_offset);

REFRESH MATERIALIZED VIEW mv_test_extension;
REFRESH MATERIALIZED VIEW mv_generalization_extension;
REFRESH MATERIALIZED VIEW mv_pit_mutation_report;
REFRESH MATERIALIZED VIEW mv_mutation_variant_comparison;
REFRESH MATERIALIZED VIEW mv_mutation_status_changes;
REFRESH MATERIALIZED VIEW mv_generalization_effects;

DO $$
DECLARE
  effects mv_generalization_effects%ROWTYPE;
BEGIN
  SELECT *
  INTO STRICT effects
  FROM mv_generalization_effects
  WHERE project_id = -100
    AND a_variant = 'ORIGINAL'
    AND b_variant = 'IMPROVED_10_TRIES';

  IF effects.killed_mutations <> 4
      OR effects.tests_before <> 4
      OR effects.added_tests <> 4
      OR effects.removed_tests <> 2
      OR effects.tests_after <> 6
      OR effects.lines_before <> 100
      OR effects.added_lines <> 18
      OR effects.removed_lines <> 30
      OR effects.lines_after <> 88
      OR abs(effects.runtime_before - 10.0) > 0.0001
      OR abs(effects.added_runtime - 1.0) > 0.0001
      OR abs(effects.removed_runtime - 3.0) > 0.0001
      OR abs(effects.runtime_after - 8.0) > 0.0001 THEN
    RAISE EXCEPTION 'unexpected generalization effects: %', row_to_json(effects);
  END IF;
END
$$;

ROLLBACK;
