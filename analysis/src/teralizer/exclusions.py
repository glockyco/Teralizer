"""
Dataset exclusion criteria for evaluation.

Projects are excluded if they fail basic setup requirements that are
beyond the scope of test generalization research.
"""

import pandas as pd


def get_excluded_project_ids(conn) -> set[int]:
    """
    Get set of project IDs that should be excluded from evaluation.

    Args:
        conn: Database connection

    Returns:
        Set of project IDs to exclude
    """
    import re

    query = """
        SELECT DISTINCT pf.project_id, pf.stage, pf.info
        FROM v_project_failures pf
    """

    df = pd.read_sql_query(query, conn)

    # Apply regex patterns to identify excluded projects
    def matches_dependency_error(info):
        if pd.isna(info):
            return False
        patterns = [
            r"artifacts could not be resolved",
            r"Could not find artifact",
            r"PluginVersionResolutionException",
            r"Could not resolve dependencies",
            r"Unresolveable build extension",
            r"Detected the following recursive expression cycle",
            r"must be a valid version",
            r"must specify an absolute path",
            r"Could not find goal 'build-classpath' in plugin",
            r"Error injecting:",
        ]
        return any(re.search(pattern, str(info)) for pattern in patterns)

    def matches_sources_not_found(info):
        if pd.isna(info):
            return False
        patterns = [
            r"No supported test framework identified",
            r"Test source path .+ does not exist\.",
            r"Main source path .+ does not exist\.",
        ]
        return any(re.search(pattern, str(info)) for pattern in patterns)

    def matches_compilation_error(info):
        if pd.isna(info):
            return False
        return bool(re.search(r"teralizer\.util\.ConsoleCommandException", str(info)))

    # Filter projects based on exclusion criteria
    excluded_mask = (
        df["info"].apply(matches_dependency_error)
        | df["info"].apply(matches_sources_not_found)
        | (
            (df["stage"] == "BUILD_PROJECT_ORIGINAL")
            & df["info"].apply(matches_compilation_error)
        )
    )

    excluded_projects = set(df[excluded_mask]["project_id"].unique().tolist())

    # Add projects with zero actual coverage
    # These have coverage records but all classes show 0 instructions/branches/lines covered
    no_coverage_query = """
        SELECT DISTINCT p.id as project_id
        FROM project p
        WHERE EXISTS (
            SELECT 1 FROM jacoco_coverage_report jcr
            WHERE jcr.project_id = p.id
        )
        AND NOT EXISTS (
            SELECT 1 FROM jacoco_coverage_report jcr
            WHERE jcr.project_id = p.id
            AND (jcr.instruction_covered > 0 OR jcr.branch_covered > 0 OR jcr.line_covered > 0)
        )
    """
    no_coverage_df = pd.read_sql_query(no_coverage_query, conn)
    excluded_projects.update(no_coverage_df["project_id"].tolist())

    return excluded_projects
