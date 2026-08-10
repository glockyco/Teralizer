# RQ2 - Constraint complexity

_Source database: `postgres_dev`._

## Constraint complexity

The comparison contains 14 project and detection groups.

**Model properties of mutants that are (not) detected by the improved variant.**

| Project | Detected | Mutants | Share | Operations mean | Operations median | Constraints mean | Constraints median | Used constraints mean | Used constraints median |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| eqbench-es-default-1s | yes | 11,145 | 51.86 | 147.13 | 9.00 | 6.10 | 2.00 | 47.05 | 80.00 |
| eqbench-es-default-1s | no | 10,347 | 48.14 | 224.83 | 16.00 | 11.56 | 5.00 | 23.56 | 50.00 |
| eqbench-es-default-10s | yes | 11,658 | 53.83 | 139.13 | 9.00 | 6.20 | 2.00 | 62.13 | 100.00 |
| eqbench-es-default-10s | no | 9,999 | 46.17 | 231.44 | 15.00 | 8.29 | 2.00 | 57.53 | 100.00 |
| eqbench-es-default-60s | yes | 12,052 | 54.76 | 137.70 | 9.00 | 5.50 | 2.00 | 69.99 | 100.00 |
| eqbench-es-default-60s | no | 9,958 | 45.24 | 218.93 | 11.00 | 6.78 | 2.00 | 67.79 | 100.00 |
| commons-utils-es-default-1s | yes | 4,390 | 57.97 | 290.06 | 15.00 | 7.32 | 5.00 | 43.94 | 84.62 |
| commons-utils-es-default-1s | no | 3,183 | 42.03 | 389.17 | 45.00 | 12.51 | 6.00 | 11.82 | 50.00 |
| commons-utils-es-default-10s | yes | 4,660 | 58.48 | 467.73 | 23.00 | 6.72 | 5.00 | 46.78 | 85.71 |
| commons-utils-es-default-10s | no | 3,309 | 41.52 | 507.45 | 46.00 | 8.98 | 6.00 | 10.24 | 56.25 |
| commons-utils-es-default-60s | yes | 4,821 | 59.45 | 374.58 | 20.00 | 6.36 | 5.00 | 47.20 | 85.71 |
| commons-utils-es-default-60s | no | 3,288 | 40.55 | 423.55 | 41.00 | 10.35 | 6.00 | 11.14 | 54.55 |
| commons-utils | yes | 4,193 | 80.40 | 107.74 | 11.00 | 4.98 | 4.00 | 25.56 | 75.00 |
| commons-utils | no | 1,022 | 19.60 | 173.74 | 10.00 | 4.98 | 4.00 | 19.75 | 75.00 |

source: [`compute_mutation_model_complexity`](https://github.com/glockyco/Teralizer/blob/b46ff655-dirty/analysis/src/teralizer/rq1_mutation_detection.py#L451)
