"""
Dataset exclusion criteria for evaluation.

Projects are excluded if they fail basic setup requirements that are
beyond the scope of test generalization research.
"""

import pandas as pd

# Failure causes that indicate projects unsuitable for evaluation
EXCLUDED_FAILURE_CAUSES = {
    "dependency resolution error",  # 329 projects
    "sources / tests not found",  # 26 projects
    "no actual coverage",  # 3 projects - tests exist but don't exercise any code
}

# Stage-specific exclusions (compilation errors in BUILD_PROJECT_ORIGINAL only)
EXCLUDED_STAGE_CAUSE_PAIRS = {
    ("BUILD_PROJECT_ORIGINAL", "compilation error"),  # 171 projects
}


def is_project_excluded(stage: str, cause: str) -> bool:
    """
    Check if a project should be excluded based on failure stage and cause.

    Args:
        stage: Processing stage name (e.g., 'SETUP_PROJECT', 'BUILD_PROJECT_ORIGINAL')
        cause: Failure cause description (e.g., 'dependency resolution error')

    Returns:
        True if project should be excluded from evaluation, False otherwise
    """
    # Check global exclusion causes
    if cause in EXCLUDED_FAILURE_CAUSES:
        return True

    # Check stage-specific exclusions
    if (stage, cause) in EXCLUDED_STAGE_CAUSE_PAIRS:
        return True

    return False


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


def get_excluded_project_names(conn) -> set[str]:
    """
    Get set of project directory names that should be excluded from evaluation.

    Args:
        conn: Database connection

    Returns:
        Set of project directory names to exclude (e.g., 'github_com_user_repo')
    """
    excluded_ids = get_excluded_project_ids(conn)

    if not excluded_ids:
        return set()

    excluded_ids_str = ",".join(str(id) for id in excluded_ids)

    query = f"""
        SELECT root_path
        FROM project
        WHERE id IN ({excluded_ids_str})
    """

    df = pd.read_sql_query(query, conn)

    # Extract directory names from root_path (e.g., 'projects/github_com_foo' -> 'github_com_foo')
    project_names = set()
    for path in df["root_path"]:
        if pd.notna(path):
            # Extract the last component of the path
            dir_name = path.split("/")[-1]
            project_names.add(dir_name)

    return project_names


def filter_excluded_projects(df: pd.DataFrame, excluded_ids: set[int]) -> pd.DataFrame:
    """
    Filter out excluded projects from a DataFrame.

    Args:
        df: DataFrame with 'project_id' column
        excluded_ids: Set of project IDs to exclude

    Returns:
        Filtered DataFrame
    """
    if "project_id" not in df.columns:
        raise ValueError("DataFrame must have 'project_id' column")

    filtered_df = df[~df["project_id"].isin(excluded_ids)].copy()
    return filtered_df  # type: ignore[return-value]


def get_dataset_size_after_exclusions(conn) -> int:
    """
    Get total number of projects after applying exclusion criteria.

    Args:
        conn: Database connection

    Returns:
        Count of eligible projects
    """
    excluded_ids = get_excluded_project_ids(conn)

    query = "SELECT COUNT(DISTINCT id) as total FROM project"
    total = pd.read_sql_query(query, conn)["total"][0]

    return total - len(excluded_ids)
