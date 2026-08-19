# RQ2 - Constraint complexity

_Source database: `postgres_dev`._

## Constraint complexity

**Model properties of mutants that are (not) detected by the \VariantImprovedC{} variant.**

| Project | Detected | Mutants | Mean | Median | Mean | Median | Mean | Median |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| eqbench-es-default-1s | yes | 11,145 | 147 | 9 | 6 | 2 | 47% | 80% |
| eqbench-es-default-1s | no | 10,347 | 224 | 16 | 11 | 5 | 24% | 50% |
| eqbench-es-default-10s | yes | 11,658 | 139 | 9 | 6 | 2 | 62% | 100% |
| eqbench-es-default-10s | no | 9,999 | 231 | 15 | 8 | 2 | 58% | 100% |
| eqbench-es-default-60s | yes | 12,052 | 137 | 9 | 5 | 2 | 70% | 100% |
| eqbench-es-default-60s | no | 9,958 | 218 | 11 | 6 | 2 | 68% | 100% |
| commons-utils-es-default-1s | yes | 4,390 | 290 | 15 | 7 | 5 | 44% | 85% |
| commons-utils-es-default-1s | no | 3,183 | 389 | 45 | 12 | 6 | 12% | 50% |
| commons-utils-es-default-10s | yes | 4,660 | 467 | 23 | 6 | 5 | 47% | 86% |
| commons-utils-es-default-10s | no | 3,309 | 507 | 46 | 8 | 6 | 10% | 56% |
| commons-utils-es-default-60s | yes | 4,821 | 374 | 20 | 6 | 5 | 47% | 86% |
| commons-utils-es-default-60s | no | 3,288 | 423 | 41 | 10 | 6 | 11% | 55% |
| commons-utils | yes | 4,193 | 107 | 11 | 4 | 4 | 26% | 75% |
| commons-utils | no | 1,022 | 173 | 10 | 4 | 4 | 20% | 75% |

source: [`compute_mutation_model_complexity`](https://github.com/glockyco/Teralizer/blob/26a2c2ae6c0c02bd9182cb8749facb49b5d1fd99/analysis/src/teralizer/rq1_mutation_detection.py#L455)
