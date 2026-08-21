INSERT INTO project (
  type,
  root_path,
  data_path,
  use_test_generation,
  use_test_generalization,
  use_test_reduction,
  configuration
)
SELECT
  'CI',
  '/fixture/' || n,
  '/data/' || n,
  false,
  true,
  false,
  '{}'
FROM generate_series(1, 13) AS n;
