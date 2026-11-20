"""Naming and ordering helpers shared by the registered reports.

The helpers map database project and variant names to the LaTeX macros of the
paper and give tables the paper's project and variant order.
"""


def standardize_project_name(project_name: str) -> str:
    """Standardize project name for cross-dataset consistency.

    Maps database project names to display names that match LaTeX macros.

    Args:
        project_name: Raw project name from database

    Returns:
        str: Standardized project name
    """
    # Project name mapping (mirrors LaTeX macro definitions)
    PROJECT_NAME_MAPPING = {
        "eqbench-es-default-1s": "eqbench-es-1s",
        "eqbench-es-default-10s": "eqbench-es-10s",
        "eqbench-es-default-60s": "eqbench-es-60s",
        "commons-utils": "commons-utils-dev",
        "commons-utils-es-default-1s": "commons-utils-es-1s",
        "commons-utils-es-default-10s": "commons-utils-es-10s",
        "commons-utils-es-default-60s": "commons-utils-es-60s",
    }

    # Remove common path prefixes and suffixes
    name = project_name.strip()

    # Handle common patterns
    if name.startswith("/"):
        name = name.split("/")[-1]  # Take last path component

    # Apply explicit mapping if available
    if name in PROJECT_NAME_MAPPING:
        return PROJECT_NAME_MAPPING[name]

    return name


# Dataset name mappings to LaTeX macros
DATASETS = {
    "eqbench": r"\DatasetEqBench{}",
    "eqbench-es-default-1s": r"\DatasetEqBenchA{}",
    "eqbench-es-default-10s": r"\DatasetEqBenchB{}",
    "eqbench-es-default-60s": r"\DatasetEqBenchC{}",
    "commons-utils": r"\DatasetCommonsDev{}",
    "commons-utils-es-default-1s": r"\DatasetCommonsA{}",
    "commons-utils-es-default-10s": r"\DatasetCommonsB{}",
    "commons-utils-es-default-60s": r"\DatasetCommonsC{}",
    "repo-reapers": r"\DatasetRepoReapers{}",
}

# Algorithm variant mappings to LaTeX macros
VARIANTS = {
    "ORIGINAL": r"\VariantOriginal{}",
    "INITIAL": r"\VariantInitial{}",
    "BASELINE": r"\VariantBaseline{}",
    "NAIVE_10_TRIES": r"\VariantNaiveA{}",
    "NAIVE_50_TRIES": r"\VariantNaiveB{}",
    "NAIVE_200_TRIES": r"\VariantNaiveC{}",
    "IMPROVED_10_TRIES": r"\VariantImprovedA{}",
    "IMPROVED_50_TRIES": r"\VariantImprovedB{}",
    "IMPROVED_200_TRIES": r"\VariantImprovedC{}",
    "SHARED": r"\VariantShared{}",
}


def get_dataset_macro(dataset_name: str) -> str:
    """Get LaTeX macro for dataset name.

    Args:
        dataset_name: Dataset identifier

    Returns:
        LaTeX macro string

    Raises:
        KeyError: If dataset name not found in mappings
    """
    return DATASETS[dataset_name]


def get_variant_macro(variant_name: str) -> str:
    """Get LaTeX macro for algorithm variant.

    Args:
        variant_name: Variant identifier

    Returns:
        LaTeX macro string

    Raises:
        KeyError: If variant name not found in mappings
    """
    return VARIANTS[variant_name]


def get_project_type(project_name: str) -> str:
    """Classify project into type groups for proper ordering.

    Args:
        project_name: Project name from database or LaTeX macro name

    Returns:
        Project type classification
    """
    # Handle LaTeX macro names
    if project_name.startswith("\\Dataset"):
        if "EqBench" in project_name:
            return "eqbench"
        elif "Commons" in project_name and "Dev" in project_name:
            return "commons-dev"
        elif "Commons" in project_name:
            return "commons-es"
        elif "RepoReapers" in project_name:
            return "repo-reapers"

    # Handle original project names
    if project_name.startswith("eqbench"):
        return "eqbench"
    elif project_name.startswith("commons-utils-es"):
        return "commons-es"
    elif project_name == "commons-utils":
        return "commons-dev"
    elif project_name.startswith("repo-reapers"):
        return "repo-reapers"
    else:
        return "other"


def get_table_group_order(project_name: str, variant: str) -> int:
    """Get explicit table group order for any project + variant combination.

    This ensures proper paper ordering: EqBench variants, Commons-ES variants, Commons-dev variants.

    Args:
        project_name: Project name from database
        variant: Variant name from database

    Returns:
        Integer sort key for table ordering
    """
    project_type = get_project_type(project_name)

    base_order = {
        "eqbench": 0,
        "commons-es": 100,
        "commons-dev": 200,
        "repo-reapers": 400,
        "other": 500,
    }

    # Special handling for repo-reapers summary rows
    if project_type == "repo-reapers":
        return base_order[project_type]

    # Variant order within each project type
    variant_order = {
        "ORIGINAL": 0,
        "INITIAL": 1,
        "BASELINE": 2,
        "NAIVE_10_TRIES": 3,
        "NAIVE_50_TRIES": 4,
        "NAIVE_200_TRIES": 5,
        "IMPROVED_10_TRIES": 6,
        "IMPROVED_50_TRIES": 7,
        "IMPROVED_200_TRIES": 8,
    }

    return base_order[project_type] + variant_order.get(variant, 99)


def get_project_within_type_order() -> dict:
    """Get project ordering within each project type.

    Returns:
        Dictionary mapping project names to their order within their type
    """
    return {
        # EqBench projects (1s, 10s, 60s)
        "eqbench-es-default-1s": 0,
        "eqbench-es-default-10s": 1,
        "eqbench-es-default-60s": 2,
        # Commons ES projects (1s, 10s, 60s)
        "commons-utils-es-default-1s": 0,
        "commons-utils-es-default-10s": 1,
        "commons-utils-es-default-60s": 2,
        # Commons dev project (only one)
        "commons-utils": 0,
        # Repo-reapers summary rows (total, mean, median)
        "repo-reapers (total)": 0,
        "repo-reapers (mean)": 1,
        "repo-reapers (median)": 2,
    }


__all__ = [
    "standardize_project_name",
    "get_dataset_macro",
    "get_variant_macro",
    "get_project_type",
    "get_table_group_order",
    "get_project_within_type_order",
    "DATASETS",
    "VARIANTS",
]
