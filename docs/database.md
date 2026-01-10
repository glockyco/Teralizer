# Database Schema

## Database Structure

PostgreSQL database with schema defined in `src/main/resources/db/create-tables.sql`:

### Core Tables
- `project` - Project metadata, configuration, and runtime statistics
- `test` - Individual test method information and metadata
- `assertion` - Test assertions and their characteristics
- `generalization` - Generated property-based test information
- `task` - Processing pipeline task execution tracking

### Data Collection Tables
- `junit_test_report` - Test execution results across all processing stages
- `jacoco_coverage_report` - Code coverage data
- `pit_mutation_report` - Mutation testing results
- `pit_coverage_report` - PIT coverage information
- `filter_result` - Test filtering decisions and reasons

### Analysis Views

Materialized views defined in `src/main/resources/db/create-views.sql` provide aggregated analysis data for evaluation.

## Database Configuration

- **postgres_dev**: Contains eqbench and commons-utils projects
- **postgres_test**: Contains repo-reapers projects
- Centralized config in `analysis/src/teralizer/config.py` provides `db_config.get_dev_engine()` and `db_config.get_test_engine()`

## Database Operations

```bash
./gradlew startPostgres    # Start PostgreSQL container
./gradlew stopPostgres     # Stop PostgreSQL container
docker compose up adminer  # Database interface at http://localhost:18080 (password: teralizer)
```
