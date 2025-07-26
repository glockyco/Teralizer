# Architecture

## Processing Pipeline

The system follows a multi-stage pipeline architecture defined in `src/main/java/teralizer/processing/ProcessingStage.java`:

### Core Processing Stages (ordered sequence):
1. **Project Setup** (stages 0-2): Download, cleanup, and setup target project
2. **Build & Dependencies** (stages 3-4): Add required dependencies and build original project
3. **Test Generation** (stages 5-6): Optional EvoSuite test generation and postprocessing
4. **Analysis Preparation** (stages 7-12): Build Spoon model, execute original tests, collect reports (JUnit, JaCoCo, PIT), filter tests
5. **Test Analysis** (stages 13-15): Analyze and filter tests and assertions for generalization suitability
6. **Constraint Collection** (stages 16-20): SPF instrumentation, build instrumented project, execute SPF constraint collection, analyze results, cleanup
7. **Initial Testing** (stages 21-25): Build and test project before generalization, collect baseline metrics
8. **Test Generalization** (stages 26-33): Generate property-based tests, build generalized project, execute and collect final metrics

### Key Components

- **ProcessingPipeline**: Orchestrates task execution in dependency order
- **TaskContext**: Shared state containing database connections, configuration, and utilities
- **Filters**: Quality gates determining test generalization suitability (in `src/main/java/teralizer/processing/filter/`)
- **Transformers**: Convert between data representations (SPF models ↔ JSON ↔ Java code)

## Key Technical Distinction

While the tool uses Symbolic Pathfinder (SPF), it does NOT perform traditional symbolic execution. Instead, it uses SPF in constraint collection mode to extract constraints from concrete test executions, avoiding the computational expense of full symbolic execution while still capturing input-output relationships.

## Dependencies

### Key Dependencies
- **Symbolic Pathfinder (SPF)**: Custom build in `jpf-symbc/` for constraint collection
- **Spoon**: Java source code analysis and AST manipulation
- **JQwik**: Property-based testing framework for generated tests
- **JOOQ**: Type-safe database queries with PostgreSQL
- **Gradle Tooling API**: Build system integration for target projects
- **EvoSuite**: Automated test generation for projects lacking existing tests
- **PIT**: Mutation testing for effectiveness evaluation
- **JaCoCo**: Code coverage analysis
- **Maven/Gradle**: Target project build system support