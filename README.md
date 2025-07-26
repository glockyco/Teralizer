# Teralizer

Teralizer is a research tool for automated test generalization that transforms existing JUnit tests into property-based tests using jqwik. The tool executes tests with Symbolic Pathfinder (SPF) in constraint collection mode to extract specifications from input partitions, then generates property-based tests that explore more inputs within the same execution paths covered by the original tests.

## Quick Start (Native Development)

### Build and Execution
```bash
./gradlew build                                 # Build project including SPF submodules
./gradlew run -Dteralizer.config=<config-file>  # Run with specific configuration
./run.sh                                        # Batch process all evaluation configs
```

### Database Operations
```bash
./gradlew startPostgres    # Start PostgreSQL container
./gradlew stopPostgres     # Stop PostgreSQL container
```

Database interface available at [http://localhost:18080](http://localhost:18080) (password: `teralizer`)

### Analysis Workflow
```bash
cd analysis/
uv sync                    # Install Python dependencies (uses pyenv Python >=3.11)
uv run jupyter lab         # Launch notebooks
uv run python validate.py  # Validate changes
```

## Docker Environment (Alternative)

For containerized development, use Docker Compose:

```bash
docker compose up          # Full environment (Teralizer + Adminer)
docker compose up adminer  # Database interface only
```

This stores all generated data in the `docker-data` folder. Database interface available at [http://localhost:18080](http://localhost:18080) (password: `teralizer`).

## Documentation

- `docs/architecture.md` - Processing pipeline and system architecture
- `docs/database.md` - Database schema and operations
- `docs/paper-integration.md` - LaTeX table generation and paper workflows
- `docs/evaluation.md` - Evaluation datasets and research data organization
