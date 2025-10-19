"""Stage mapping utilities for Teralizer processing pipeline.

This module provides functions to map between internal processing stage names
(as stored in the database) and reader-facing stage names (as used in the paper).
"""

from typing import Optional


def map_internal_stage_to_paper_stage(internal_stage: str) -> Optional[str]:
    """Map internal processing stage name to reader-facing paper stage.

    The Teralizer processing pipeline consists of many internal stages that are
    grouped into 5 main stages for presentation in the paper:
    - Stage 1: Test and Assertion Analysis
    - Stage 2: Tested Method Identification
    - Stage 3: Specification Extraction + Baseline Validation
    - Stage 4: Generalized Test Creation + Validation
    - Stage 5: Test Suite Reduction (Mutation Testing + Coverage)

    Note: Stages 1 and 2 cannot be distinguished in the collected data because
    their core logic is implemented in the ANALYZE_TESTS task, so they are
    combined as "1 + 2" in the output.

    Args:
        internal_stage: Internal stage name (e.g., 'SETUP_PROJECT', 'EXECUTE_JPF')

    Returns:
        Paper stage identifier ('1 + 2', '3', '4', '5') or None if stage not recognized
    """
    # Stage 1 + 2: Test and Assertion Analysis + Tested Method Identification
    # (Combined because they cannot be distinguished in the data)
    stage_1_2 = {
        "SETUP_PROJECT",
        "ADD_DEPENDENCIES",
        "BUILD_PROJECT_ORIGINAL",
        "BUILD_SPOON_MODEL",
        "EXECUTE_TESTS_ORIGINAL",
        "COLLECT_JUNIT_REPORTS_ORIGINAL",
        "COLLECT_JACOCO_DATA_ORIGINAL",
        "FILTER_TESTS_ORIGINAL",
        "ANALYZE_TESTS",
        "FILTER_TESTS",
        "FILTER_ASSERTIONS",
    }

    # Stage 3: Specification Extraction + Baseline Validation
    stage_3 = {
        "ADD_JPF_INSTRUMENTATION",
        "BUILD_PROJECT_INSTRUMENTED",
        "EXECUTE_JPF",
        "ANALYZE_JPF",
        "CLEANUP_JPF_INSTRUMENTATION",
        "BUILD_PROJECT_INITIAL",
        "EXECUTE_TESTS_INITIAL",
        "COLLECT_JUNIT_REPORTS_INITIAL",
    }

    # Stage 4: Generalized Test Creation + Validation
    stage_4 = {
        "CLEANUP_GENERALIZATION",
        "GENERALIZE_TESTS",
        "BUILD_PROJECT_GENERALIZED",
        "EXECUTE_TESTS_GENERALIZED",
        "COLLECT_JUNIT_REPORTS_GENERALIZED",
        "FILTER_GENERALIZATIONS",
    }

    # Stage 5: Test Suite Reduction (Mutation Testing + Coverage)
    stage_5 = {
        "COLLECT_PIT_DATA_ORIGINAL",
        "COLLECT_JACOCO_DATA_INITIAL",
        "COLLECT_PIT_DATA_INITIAL",
        "COLLECT_JACOCO_DATA_GENERALIZED",
        "COLLECT_PIT_DATA_GENERALIZED",
    }

    if internal_stage in stage_1_2:
        return "1 + 2"
    elif internal_stage in stage_3:
        return "3"
    elif internal_stage in stage_4:
        return "4"
    elif internal_stage in stage_5:
        return "5"
    else:
        return None


def get_stage_group_sql_case() -> str:
    """Generate SQL CASE statement for stage group mapping.

    Returns SQL CASE statement that maps internal stage names to paper stage groups.
    Useful for database queries and views.

    Returns:
        SQL CASE statement as string
    """
    return """CASE
        WHEN stage IN (
            'SETUP_PROJECT',
            'ADD_DEPENDENCIES',
            'BUILD_PROJECT_ORIGINAL',
            'BUILD_SPOON_MODEL',
            'EXECUTE_TESTS_ORIGINAL',
            'COLLECT_JUNIT_REPORTS_ORIGINAL',
            'COLLECT_JACOCO_DATA_ORIGINAL',
            'FILTER_TESTS_ORIGINAL',
            'ANALYZE_TESTS',
            'FILTER_TESTS',
            'FILTER_ASSERTIONS'
        ) THEN 'Stage 1 + 2'
        WHEN stage IN (
            'ADD_JPF_INSTRUMENTATION',
            'BUILD_PROJECT_INSTRUMENTED',
            'EXECUTE_JPF',
            'ANALYZE_JPF',
            'CLEANUP_JPF_INSTRUMENTATION',
            'BUILD_PROJECT_INITIAL',
            'EXECUTE_TESTS_INITIAL',
            'COLLECT_JUNIT_REPORTS_INITIAL'
        ) THEN 'Stage 3'
        WHEN stage IN (
            'CLEANUP_GENERALIZATION',
            'GENERALIZE_TESTS',
            'BUILD_PROJECT_GENERALIZED',
            'EXECUTE_TESTS_GENERALIZED',
            'COLLECT_JUNIT_REPORTS_GENERALIZED',
            'FILTER_GENERALIZATIONS'
        ) THEN 'Stage 4'
        WHEN stage IN (
            'COLLECT_PIT_DATA_ORIGINAL',
            'COLLECT_JACOCO_DATA_INITIAL',
            'COLLECT_PIT_DATA_INITIAL',
            'COLLECT_JACOCO_DATA_GENERALIZED',
            'COLLECT_PIT_DATA_GENERALIZED'
        ) THEN 'Stage 5'
    END"""


def get_stage_order() -> dict[str, int]:
    """Get ordering for paper stages.

    Returns:
        Dictionary mapping stage names to sort order
    """
    return {
        "1 + 2": 1,
        "3": 2,
        "4": 3,
        "5": 4,
    }
