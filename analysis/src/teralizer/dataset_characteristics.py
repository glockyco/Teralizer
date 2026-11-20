"""Dataset characteristics analysis for test generalization evaluation.

This module provides functions to analyze dataset characteristics including
project statistics, file counts, code metrics, and test method counts.
Supports both GitHub and non-GitHub projects with proper aggregation.
"""

import os
import re
import pandas as pd
from pathlib import Path
from typing import List, Dict, Any, Tuple
from dotenv import load_dotenv

from .report_basis import resolve_repo_relative_path


# =============================================================================
# File analysis functions (specific to dataset analysis)
# =============================================================================


def _should_exclude_file(file_path: str) -> bool:
    """Check if a file should be excluded from dataset statistics.

    Excludes JPF instrumentation and generalized files but includes EvoSuite tests.
    Based on patterns from CleanupTask.java.

    Args:
        file_path: Path to the Java file

    Returns:
        True if file should be excluded, False otherwise
    """
    file_name = os.path.basename(file_path)

    # Exclude JPF instrumentation files
    if file_name.startswith("_") and (
        "_Driver_" in file_name or "_Instrumented_" in file_name
    ):
        return True

    # Exclude generalized files
    if file_name.startswith("_") and "_Generalized_" in file_name:
        return True

    # Include everything else (including EvoSuite tests ending with ESTest.java)
    return False


def _count_line_types(file_path: str) -> Tuple[int, int, int]:
    """Count different types of lines in a Java file.

    Args:
        file_path: Path to the Java file

    Returns:
        Tuple of (code_lines, comment_lines, empty_lines)
    """
    code_lines = 0
    comment_lines = 0
    empty_lines = 0
    in_block_comment = False

    with open(file_path, encoding="utf-8", errors="ignore") as f:
        for line in f:
            stripped = line.strip()
            if not stripped:
                empty_lines += 1
                continue

            if in_block_comment:
                comment_lines += 1
                if "*/" in stripped:
                    in_block_comment = False
                continue

            if stripped.startswith("/*"):
                comment_lines += 1
                if "*/" not in stripped:
                    in_block_comment = True
                continue

            if stripped.startswith("//"):
                comment_lines += 1
                continue

            if "/*" in stripped:
                before_comment = stripped.split("/*", 1)[0].strip()
                if before_comment:
                    code_lines += 1
                else:
                    comment_lines += 1
                if "*/" not in stripped:
                    in_block_comment = True
                continue

            if "//" in stripped:
                before_comment = stripped.split("//", 1)[0].strip()
                if before_comment:
                    code_lines += 1
                else:
                    comment_lines += 1
                continue

            code_lines += 1

    return code_lines, comment_lines, empty_lines


def _relative_to_projects(directory: str, projects_root: Path) -> tuple[str, ...]:
    """Return the path of a source directory relative to the projects root.

    Every name in this module is anchored to that root. An absolute path also contains the
    directories the repository is checked out under, and any of those can carry a name the
    convention uses, so reading a name out of the absolute path reports the checkout location
    instead of the project.
    """
    return Path(directory).resolve().relative_to(Path(projects_root).resolve()).parts


def _get_project_name(directory: str, projects_root: Path) -> str:
    """Return the project directory name, which is the first component under the root."""
    parts = _relative_to_projects(directory, projects_root)
    if not parts:
        raise ValueError(f"source directory is the projects root itself: {directory}")
    return parts[0]


def _get_source_type(directory: str, projects_root: Path) -> str:
    """Return whether a source directory holds main or test sources."""
    parts = _relative_to_projects(directory, projects_root)
    return "main" if "main" in parts[1:] else "test"


# =============================================================================
# Data retrieval functions
# =============================================================================


def get_projects_path(raise_if_missing: bool = True) -> Path | None:
    """Get projects directory path from environment variable with fallback.

    Args:
        raise_if_missing: If True, raise FileNotFoundError when not found.
                         If False, return None when not found.

    Returns:
        Path to projects directory, or None if not found and raise_if_missing=False

    Raises:
        FileNotFoundError: If projects directory cannot be found and raise_if_missing=True
    """
    load_dotenv()

    projects_path = os.getenv("PROJECTS_PATH")
    if projects_path:
        configured_path = resolve_repo_relative_path(projects_path)
        if configured_path.exists():
            return configured_path

    fallback_path = resolve_repo_relative_path(Path("../../projects"))
    if fallback_path.exists():
        return fallback_path

    if raise_if_missing:
        raise FileNotFoundError(
            "Projects directory not found. Set PROJECTS_PATH in .env or ensure ../../projects exists."
        )
    return None


def get_source_directories(
    projects_root: Path, excluded_projects: set[str] | None = None
) -> List[str]:
    """Discover all project source directories for analysis.

    Args:
        projects_root: Root path containing all projects
        excluded_projects: Optional set of project directory names to exclude

    Returns:
        List of source directory paths to analyze
    """
    if excluded_projects is None:
        excluded_projects = set()

    directories = []

    # Non-GitHub projects (commons-utils*, eqbench*)
    for pattern in ["commons-utils*", "eqbench*"]:
        for project_dir in projects_root.glob(pattern):
            if project_dir.is_dir():
                # Check if this project should be excluded
                if project_dir.name in excluded_projects:
                    continue

                for src_type in ["main", "test"]:
                    # Try both java and code subdirectories (eqbench uses code, others use java)
                    for subdir in ["java", "code"]:
                        src_dir = project_dir / "src" / src_type / subdir
                        if src_dir.exists():
                            directories.append(str(src_dir))
                            break  # Only add one per src_type

    # GitHub projects (github_com_*)
    for project_dir in projects_root.glob("github_com_*"):
        if project_dir.is_dir():
            # Check if this project should be excluded
            if project_dir.name in excluded_projects:
                continue

            for src_type in ["main", "test"]:
                src_dir = project_dir / "src" / src_type / "java"
                if src_dir.exists():
                    directories.append(str(src_dir))

    return directories


# =============================================================================
# Computation functions
# =============================================================================


def compute_directory_statistics(directory: str) -> Dict[str, Any]:
    """Compute statistics for a single source directory.

    Args:
        directory: Path to source directory

    Returns:
        Dictionary with directory statistics
    """
    num_files = 0
    num_classes = 0
    num_code_lines = 0
    num_comment_lines = 0
    num_empty_lines = 0

    for root, _, files in os.walk(directory):
        for file in files:
            if file.endswith(".java"):
                file_path = os.path.join(root, file)

                # Skip generated files (but include EvoSuite tests)
                if _should_exclude_file(file_path):
                    continue

                num_files += 1
                code, comment, empty = _count_line_types(file_path)
                num_code_lines += code
                num_comment_lines += comment
                num_empty_lines += empty

                with open(file_path, encoding="utf-8", errors="ignore") as f:
                    content = f.read()
                    num_classes += len(re.findall(r"\b(class|enum)\s+\w+", content))

    return {
        "directory": directory,
        "num_files": num_files,
        "num_classes": num_classes,
        "num_code_lines": num_code_lines,
        "num_comment_lines": num_comment_lines,
        "num_empty_lines": num_empty_lines,
    }


def compute_project_statistics(
    projects_root: Path, excluded_projects: set[str] | None = None
) -> pd.DataFrame:
    """Compute comprehensive statistics for all projects.

    Args:
        projects_root: Root path containing all projects
        excluded_projects: Optional set of project directory names to exclude

    Returns:
        DataFrame with detailed project statistics
    """
    directories = get_source_directories(projects_root, excluded_projects)

    stats = []
    for directory in directories:
        stat = compute_directory_statistics(directory)
        stat["project"] = _get_project_name(directory, projects_root)
        stat["type"] = _get_source_type(directory, projects_root)
        stats.append(stat)

    return pd.DataFrame(stats)


def _get_test_counts_from_db(
    db_conn_dev, db_conn_test, excluded_test_projects: set[str] | None = None
):
    """Get test method counts from database by project name.

    Args:
        db_conn_dev: Database connection for dev database (EqBench, Commons)
        db_conn_test: Database connection for test database (RepoReapers)
        excluded_test_projects: RepoReapers project names excluded from the corpus

    Returns:
        Tuple of (project_counts_dict, repo_reapers_per_project_list)
        - project_counts_dict: Maps project type names to test counts
        - repo_reapers_per_project_list: List of test counts per repo-reapers project
    """
    from sqlalchemy import text
    from .exclusions import get_excluded_project_ids

    counts = {}
    repo_reapers_per_project = []

    # Get counts from dev database (EqBench and Commons projects)
    if db_conn_dev is not None:
        excluded_ids = get_excluded_project_ids(db_conn_dev)
        excluded_ids_str = (
            ",".join(str(id) for id in excluded_ids) if excluded_ids else "0"
        )

        # Extract project name from root_path (e.g., 'projects/eqbench-es-default-60s' -> 'eqbench-es-default-60s')
        query = text(f"""
            SELECT
                regexp_replace(p.root_path, '^projects/', '') as project_name,
                COUNT(t.id) as test_count
            FROM project p
            JOIN test t ON t.project_id = p.id
            WHERE p.use_test_generalization
              AND p.id NOT IN ({excluded_ids_str})
            GROUP BY p.root_path
        """)

        df = pd.read_sql_query(query, db_conn_dev)
        counts.update(dict(zip(df["project_name"], df["test_count"])))

    # Get counts from test database (RepoReapers projects)
    if db_conn_test is not None:
        if excluded_test_projects is None:
            excluded_ids = get_excluded_project_ids(db_conn_test)
            excluded_ids_str = (
                ",".join(str(id) for id in excluded_ids) if excluded_ids else "0"
            )
            exclusion_clause = f"AND p.id NOT IN ({excluded_ids_str})"
            exclusion_params = None
        else:
            exclusion_clause = "AND p.root_path != ALL(:excluded_roots)"
            exclusion_params = {
                "excluded_roots": sorted(
                    f"projects/{name}" for name in excluded_test_projects
                )
            }

        # Get counts per project type
        query = text(f"""
            SELECT
                p.type as project_name,
                COUNT(t.id) as test_count
            FROM project p
            JOIN test t ON t.project_id = p.id
            WHERE p.use_test_generalization
              {exclusion_clause}
            GROUP BY p.type
        """)

        df = pd.read_sql_query(query, db_conn_test, params=exclusion_params)
        counts.update(dict(zip(df["project_name"], df["test_count"])))

        # Get per-project counts for repo-reapers (MAVEN projects)
        repo_reapers_query = text(f"""
            SELECT
                COUNT(t.id) as test_count
            FROM project p
            JOIN test t ON t.project_id = p.id
            WHERE p.use_test_generalization
              {exclusion_clause}
              AND p.type = 'MAVEN'
            GROUP BY p.id
            ORDER BY p.id
        """)

        repo_reapers_df = pd.read_sql_query(
            repo_reapers_query, db_conn_test, params=exclusion_params
        )
        repo_reapers_per_project = repo_reapers_df["test_count"].tolist()

    return counts, repo_reapers_per_project


def compute_project_aggregates(
    df: pd.DataFrame,
    db_conn_dev,
    db_conn_test,
    excluded_test_projects: set[str] | None = None,
) -> pd.DataFrame:
    """Compute aggregated project statistics.

    Uses database counts for test methods. Filesystem analysis detects all @Test
    annotated methods including those explicitly excluded by developers through
    surefire patterns, @Ignore/@Disabled annotations, assumptions, etc. The
    database reflects only tests that were actually executed by JUnit and
    processed by Teralizer.

    Args:
        df: DataFrame with detailed project statistics from filesystem
        db_conn_dev: Database connection for dev database (EqBench, Commons)
        db_conn_test: Database connection for test database (RepoReapers)
        excluded_test_projects: RepoReapers project names excluded from the corpus

    Returns:
        DataFrame with aggregated statistics per project
    """
    summary = []

    # Get database test counts
    db_test_counts, repo_reapers_per_project = _get_test_counts_from_db(
        db_conn_dev,
        db_conn_test,
        excluded_test_projects=excluded_test_projects,
    )

    # All non-github projects individually
    non_github_df = df[~df["project"].str.startswith("github_com_")]
    for project in non_github_df["project"].unique():
        main = non_github_df[
            (non_github_df["project"] == project) & (non_github_df["type"] == "main")
        ]
        test = non_github_df[
            (non_github_df["project"] == project) & (non_github_df["type"] == "test")
        ]

        # ALWAYS use database count for test methods (never filesystem)
        # Filesystem counts include tests excluded by developers via @Ignore, surefire patterns, etc.
        test_methods = db_test_counts.get(project, 0)

        summary.append(
            {
                "project": project,
                "main_files": int(main["num_files"].sum()),
                "main_classes": int(main["num_classes"].sum()),
                "main_sloc": int(main["num_code_lines"].sum()),
                "test_files": int(test["num_files"].sum()),
                "test_classes": int(test["num_classes"].sum()),
                "test_sloc": int(test["num_code_lines"].sum()),
                "test_methods": test_methods,
            }
        )

    # GitHub projects (total, mean, median)
    github_df = df[df["project"].str.startswith("github_com_")]

    if not github_df.empty:
        # Group by project and type
        github_projects = github_df["project"].unique()
        github_rows = []
        for project in github_projects:
            main = github_df[
                (github_df["project"] == project) & (github_df["type"] == "main")
            ]
            test = github_df[
                (github_df["project"] == project) & (github_df["type"] == "test")
            ]
            github_rows.append(
                {
                    "main_files": int(main["num_files"].sum()),
                    "main_classes": int(main["num_classes"].sum()),
                    "main_sloc": int(main["num_code_lines"].sum()),
                    "test_files": int(test["num_files"].sum()),
                    "test_classes": int(test["num_classes"].sum()),
                    "test_sloc": int(test["num_code_lines"].sum()),
                }
            )

        # Convert to DataFrame for easy stats
        github_stats = pd.DataFrame(github_rows)

        # ALWAYS use database counts for test methods (per-project list from database)
        # NEVER use filesystem counts as they include excluded tests
        if repo_reapers_per_project:
            test_total = sum(repo_reapers_per_project)
            test_mean = int(
                sum(repo_reapers_per_project) / len(repo_reapers_per_project)
            )
            test_median = int(pd.Series(repo_reapers_per_project).median())
        else:
            # If no database connection provided, we cannot compute test methods
            test_total = 0
            test_mean = 0
            test_median = 0

        # Total
        summary.append(
            {
                "project": "repo-reapers (total)",
                "main_files": int(github_stats["main_files"].sum()),
                "main_classes": int(github_stats["main_classes"].sum()),
                "main_sloc": int(github_stats["main_sloc"].sum()),
                "test_files": int(github_stats["test_files"].sum()),
                "test_classes": int(github_stats["test_classes"].sum()),
                "test_sloc": int(github_stats["test_sloc"].sum()),
                "test_methods": test_total,
            }
        )
        # Mean
        summary.append(
            {
                "project": "repo-reapers (mean)",
                "main_files": int(github_stats["main_files"].mean()),
                "main_classes": int(github_stats["main_classes"].mean()),
                "main_sloc": int(github_stats["main_sloc"].mean()),
                "test_files": int(github_stats["test_files"].mean()),
                "test_classes": int(github_stats["test_classes"].mean()),
                "test_sloc": int(github_stats["test_sloc"].mean()),
                "test_methods": test_mean,
            }
        )
        # Median
        summary.append(
            {
                "project": "repo-reapers (median)",
                "main_files": int(github_stats["main_files"].median()),
                "main_classes": int(github_stats["main_classes"].median()),
                "main_sloc": int(github_stats["main_sloc"].median()),
                "test_files": int(github_stats["test_files"].median()),
                "test_classes": int(github_stats["test_classes"].median()),
                "test_sloc": int(github_stats["test_sloc"].median()),
                "test_methods": test_median,
            }
        )

    return pd.DataFrame(summary)
