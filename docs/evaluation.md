# Evaluation and Data Organization

## Target Projects

Target projects for evaluation are stored in the `projects/` directory. The evaluation dataset consists of three project types:

### 1. EqBench Benchmark
Well-suited projects for SPF-based processing, focusing on programs with numeric inputs while avoiding recursion and reflection. Uses EvoSuite-generated test suites with different timeout settings (1s, 10s, 60s per class).

### 2. Apache Commons Utils
Utility methods extracted from Apache Commons projects via regex search for public static methods with numeric/boolean inputs and outputs. Includes both original developer-written tests and EvoSuite-generated variants.

### 3. RepoReapers Dataset
1,160 open source Java projects (5K-50K LOC, 20%-80% test code) selected from the RepoReapers dataset for real-world evaluation. Primary focus for limitation analysis (RQ4) as current prototype has limited success with these projects.

Project selection and filtering procedures are implemented in the `dataset/` directory.

## Raw Data Collection

The `data/` directory contains raw data collected during project processing:
- Input/output specifications extracted from constraint collection
- JUnit test execution reports
- PIT mutation testing reports  
- JaCoCo coverage reports
- EvoSuite test generation reports
- Other processing artifacts and intermediate results

## Batch Processing

The `run.sh` script processes all evaluation configurations:
- Executes projects sequentially with individual logging
- Handles timeouts and Java process cleanup
- Generates execution summary with success/failure statistics
- Individual project logs stored in `logs/project-*.txt`

## Research Data Flow

All experimental data flows into the PostgreSQL database for analysis:
- Original vs. generalized test execution results
- Mutation testing effectiveness comparisons
- Runtime and efficiency metrics
- Filtering decisions and generalization success rates