# RQ2 - Constraint complexity

_Source database: `postgres_dev`._

## Constraint complexity

**Model properties of mutants that are (not) detected by the Improved (200 tries) variant.**

| Project | Detected | Mutants | Mean | Median | Mean | Median | Mean | Median |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| EqBench-ES (1 s) | yes | 11,145 | 147 | 9 | 6 | 2 | 47% | 80% |
| EqBench-ES (1 s) | no | 10,347 | 224 | 16 | 11 | 5 | 23% | 50% |
| EqBench-ES (10 s) | yes | 11,658 | 139 | 9 | 6 | 2 | 62% | 100% |
| EqBench-ES (10 s) | no | 9,999 | 231 | 15 | 8 | 2 | 57% | 100% |
| EqBench-ES (60 s) | yes | 12,052 | 137 | 9 | 5 | 2 | 69% | 100% |
| EqBench-ES (60 s) | no | 9,958 | 218 | 11 | 6 | 2 | 67% | 100% |
| Commons-ES (1 s) | yes | 4,390 | 290 | 15 | 7 | 5 | 43% | 84% |
| Commons-ES (1 s) | no | 3,183 | 389 | 45 | 12 | 6 | 11% | 50% |
| Commons-ES (10 s) | yes | 4,660 | 467 | 23 | 6 | 5 | 46% | 85% |
| Commons-ES (10 s) | no | 3,309 | 507 | 46 | 8 | 6 | 10% | 56% |
| Commons-ES (60 s) | yes | 4,821 | 374 | 20 | 6 | 5 | 47% | 85% |
| Commons-ES (60 s) | no | 3,288 | 423 | 41 | 10 | 6 | 11% | 54% |
| Commons Utils | yes | 4,193 | 107 | 11 | 4 | 4 | 25% | 75% |
| Commons Utils | no | 1,022 | 173 | 10 | 4 | 4 | 19% | 75% |

source: [`compute_mutation_model_complexity`](https://github.com/glockyco/Teralizer/blob/b04623b632b0539dc516c39839662b43bc0c80ea/analysis/src/teralizer/rq1_mutation_detection.py#L459)
