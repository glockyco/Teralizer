# Database Schema

## Database Structure

PostgreSQL database with schema defined in `src/main/resources/db/create-tables.sql`:

### Core Tables
- `project` - Project metadata, configuration, and runtime statistics
- `test` - Individual test method information and metadata
- `assertion` - Test assertions, resolved MUT columns (`tested_*`), and extraction telemetry:
  `output_spec_class` (SYMBOLIC | CONSTANT | NULL_CONCRETE | EXCEPTION),
  `concretization_events` (symbolic values entering unmodeled native methods),
  `generalization_recipe` (JSON contract consumed by instrumentation and generation)
- `mut_resolution_observation` - MUT-id resolver provenance (confidence tier, deciding signal,
  candidates, input topology) for every assertion, including unresolved ones
- `generalization` - Generated property-based test information. `is_included` and
  `exclusion_info` carry typed exclusion labels (e.g. `ORACLE_NOT_WIDENABLE`)
- `task` - Processing pipeline task execution tracking (stage, variant, status, runtime)

### Data Collection Tables
- `junit_test_report` - Test execution results across all processing stages
- `jqwik_execution_run` / `jqwik_property_execution` - per-property jqwik diagnostics for
  generalized runs: `diagnostic_kind` (FULL | ASSERTION_FAILED | LIMITED_TOO_MANY_FILTER_MISSES
  | FILTER_EXHAUSTED_SEED_ONLY | DIAGNOSTIC_MISSING), `tries`, `distinct_tuples`, seed, sidecar
  paths
- `jacoco_coverage_report` - Code coverage data
- `pit_mutation_report` - Mutation testing results
- `pit_coverage_report` - PIT coverage information
- `filter_result` - Test filtering decisions and reasons

### Analysis Views

Materialized views defined in `src/main/resources/db/create-views.sql` provide aggregated analysis data for evaluation.

## Database Configuration

- **postgres_dev**: Contains eqbench and commons-utils projects
- **postgres_test**: Contains repo-reapers projects
- **postgres_reporeapers_rerun**: pre-fusion baseline corpus, protected (comparisons join on
  `root_path`, never `id`)
- Scratch databases (`postgres_verification`, `postgres_<purpose>_verify`) are created and
  dropped by runner scripts. Never experiment on the databases above
- Centralized config in `analysis/src/teralizer/config.py` provides `db_config.get_dev_engine()` and `db_config.get_test_engine()`

## Database Operations

```bash
./gradlew startPostgres    # Start PostgreSQL container
./gradlew stopPostgres     # Stop PostgreSQL container
docker compose up adminer  # Database interface at http://localhost:18080 (password: teralizer)
```
