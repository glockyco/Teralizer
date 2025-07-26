# Paper Integration

## Paper Repository Location
- **Path**: `~/projects/test-generalization-paper`
- **Main file**: `main.tex` (contains LaTeX macro definitions)
- **Content**: `sections/` directory with research question sections
- **Macros**: Dataset, variant, and tool macros defined in `main.tex`

## Paper Table Structure

Tables are organized by research question in corresponding section files:

### Dataset Statistics (`04-evaluation-02-dataset.tex`)
- Project statistics (files, classes, SLOC, test methods per project)

### RQ1: Mutation Detection (`04-evaluation-04-rq1.tex`)
- Mutants per project (total, covered, uncovered)
- Detections per mutator with improvement rates
- Model properties of detected vs undetected mutants

### RQ2: Test Suite Changes (`04-evaluation-05-rq2.tex`)
- Test count before/after generalization per project
- Test lines before/after generalization per project
- Test runtime before/after generalization per project

### RQ3: Efficiency Analysis (`04-evaluation-06-rq3.tex`)
- Total Teralizer runtimes per project
- Pareto efficiency points for commons-utils and EqBench
- Filtering results and execution failure analysis

### RQ4: Limitations (`04-evaluation-07-rq4.tex`)
- Processing failures per stage
- Failure causes per stage  
- Extended dataset filtering results

### Technical Details (`03-approach.tex`)
- PIT mutators in DEFAULTS group

## Table Ordering Guidelines

The analysis system uses strict multi-level ordering to ensure consistent table presentation:

### Primary Ordering (Project Types)
- **EqBench projects** (order 0-99): `eqbench-es-default-1s`, `eqbench-es-default-10s`, `eqbench-es-default-60s`
- **Commons-ES projects** (order 100-199): `commons-utils-es-default-1s`, `commons-utils-es-default-10s`, `commons-utils-es-default-60s`  
- **Commons-dev project** (order 200+): `commons-utils` (always appears last)

### Secondary Ordering (Algorithm Variants)
Within each project type, variants are ordered as: `ORIGINAL`, `INITIAL`, `BASELINE`, `NAIVE_10_TRIES`, `NAIVE_50_TRIES`, `NAIVE_200_TRIES`, `IMPROVED_10_TRIES`, `IMPROVED_50_TRIES`, `IMPROVED_200_TRIES`

### Implementation
```python
from teralizer.mappings import get_table_group_order, get_project_within_type_order
from natsort import natsorted

df_sorted = df.reindex(
    index=natsorted(df.index, key=lambda x: (
        get_table_group_order(df.loc[x, 'project_name'], df.loc[x, 'variant']),
        get_project_within_type_order().get(df.loc[x, 'project_name'], 99)
    ))
)
```

## Cross-Project Workflow
- **Macro verification**: Always check `main.tex` for current LaTeX macro definitions
- **Table alignment**: Use `analysis/src/teralizer/mappings.py` for consistent macro usage
- **Output generation**: Save LaTeX tables to `analysis/output/tables/` for paper inclusion
- **Validation**: Compare notebook table structure with paper sections before committing