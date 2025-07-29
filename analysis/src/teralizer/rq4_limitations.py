"""RQ4: Limitations - Analysis functions for filtering causes and processing failures.

This module provides functions to analyze the limitations of the test generalization
approach by examining filtering causes in the evaluation dataset and processing
pipeline failures in the extended dataset of open source projects.
"""

import pandas as pd
import re
from typing import Dict, List, Tuple
from collections import OrderedDict

from .formatting import (
    replace_variant_names_with_macros,
    build_latex_table_content,
)
from .exports import get_variant_macro


# =============================================================================
# Data Retrieval Functions (get_*)
# =============================================================================


def get_exclusions_summary_data(conn) -> pd.DataFrame:
    """Get overall exclusions summary by variant and level.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: variant, level, is_included, excluded_by, count
    """
    query = "SELECT * FROM mv_exclusions_all"
    df = pd.read_sql_query(query, conn)
    return df


def get_filtering_exclusions_data(conn) -> pd.DataFrame:
    """Get filtering-based exclusions data where reject > 0.

    Args:
        conn: Database connection

    Returns:
        DataFrame with filtering results by variant, level, and filter
    """
    query = "SELECT * FROM mv_exclusions_filtering WHERE reject > 0"
    df = pd.read_sql_query(query, conn)
    return df


def get_spf_failures_data(conn) -> pd.DataFrame:
    """Get SPF execution failures data.

    Args:
        conn: Database connection

    Returns:
        DataFrame with columns: error_category, count
    """
    query = "SELECT * FROM mv_exclusions_jpf"
    df = pd.read_sql_query(query, conn)
    return df


def get_test_failures_data(conn) -> pd.DataFrame:
    """Get test execution failures data.

    Args:
        conn: Database connection

    Returns:
        DataFrame with test failure counts by failure_type and variant
    """
    query = "SELECT * FROM mv_exclusions_test_fails"
    df = pd.read_sql_query(query, conn)
    return df


def get_processing_failures_summary_data(conn) -> pd.DataFrame:
    """Get processing pipeline failures summary from extended dataset.

    Args:
        conn: Database connection (should be extended dataset connection)

    Returns:
        DataFrame with processing stage failure summary
    """
    summary_query = """
    SELECT NULL as step, NULL as stage, 'Total projects' AS status, 
           (SELECT SUM(count) FROM v_project_failures_summary) + 
           (SELECT COUNT(*) FROM v_projects_successes) AS count
    UNION ALL
    SELECT step, stage, stage, count FROM v_project_failures_summary
    UNION ALL
    SELECT NULL, NULL, 'Successfully processed', COUNT(*) FROM v_projects_successes
    """
    df = pd.read_sql_query(summary_query, conn)
    return df


def get_processing_failure_causes_data(conn) -> pd.DataFrame:
    """Get detailed processing failure causes data.

    Args:
        conn: Database connection (should be extended dataset connection)

    Returns:
        DataFrame with stage and failure info for cause analysis
    """
    query = "SELECT stage, info FROM v_project_failures"
    df = pd.read_sql_query(query, conn)
    return df


# =============================================================================
# Computation Functions (compute_*)
# =============================================================================


def compute_exclusion_percentages(df: pd.DataFrame) -> pd.DataFrame:
    """Compute exclusion percentages and format for display.

    Args:
        df: Raw exclusions data

    Returns:
        DataFrame with computed percentages and formatted columns
    """
    # Map level to Type
    level_map = {
        "1-TEST": "Test",
        "2-ASSERTION": "Assertion",
        "3-GENERALIZATION": "Generalization",
    }
    df["Type"] = df["level"].map(level_map)

    # Get unique (variant, level) pairs in original order
    ordered_pairs = df[["variant", "Type"]].drop_duplicates()

    # Calculate included and excluded counts
    included = (
        df[df["is_included"]].groupby(["variant", "Type"])["count"].sum().reset_index()
    )
    excluded = (
        df[~df["is_included"]].groupby(["variant", "Type"])["count"].sum().reset_index()
    )

    included = included.rename(columns={"count": "included_count"})
    excluded = excluded.rename(columns={"count": "excluded_count"})

    # Merge counts
    result = pd.merge(ordered_pairs, included, on=["variant", "Type"], how="left")
    result = pd.merge(result, excluded, on=["variant", "Type"], how="left")

    # Fill NaNs with 0 and ensure integer type
    result["included_count"] = result["included_count"].fillna(0).astype(int)
    result["excluded_count"] = result["excluded_count"].fillna(0).astype(int)

    # Compute Total column
    result["Total"] = result["included_count"] + result["excluded_count"]

    # Compute percentages
    result["included_pct"] = (result["included_count"] / result["Total"] * 100).round(1)
    result["excluded_pct"] = (result["excluded_count"] / result["Total"] * 100).round(1)

    return result


def compute_filtering_exclusions_summary(df: pd.DataFrame) -> pd.DataFrame:
    """Compute filtering exclusions with proper formatting.

    Args:
        df: Raw filtering exclusions data

    Returns:
        DataFrame with computed statistics and formatting
    """
    # Map level to Type
    level_map = {
        "1-TEST": "Test",
        "2-ASSERTION": "Assertion",
        "3-GENERALIZATION": "Generalization",
    }
    df["Type"] = df["level"].map(level_map)

    # Ensure integer columns are int type
    df["total"] = df["total"].astype(int)
    df["accept"] = df["accept"].astype(int)
    df["reject"] = df["reject"].astype(int)

    # Add Defer to Reject and drop Defer
    df["reject"] = df["reject"] + df["defer"]
    df = df.drop(columns=["defer"])

    # Remove Filter suffix from filter names
    df["filter_name"] = df["filter_name"].str.replace(r"Filter$", "", regex=True)

    # Calculate percentages
    df["accept_pct"] = (df["accept"] / df["total"] * 100).round(1)
    df["reject_pct"] = (df["reject"] / df["total"] * 100).round(1)

    return df


def compute_spf_error_categorization(df: pd.DataFrame) -> pd.DataFrame:
    """Categorize SPF errors and compute percentages.

    Args:
        df: Raw SPF failures data

    Returns:
        DataFrame with categorized errors and percentages
    """
    category_map = {
        "ArithmeticException: div by 0": "SPF exception",
        "NoSuchMethodException": "SPF exception",
        "AssertionFailedError": "SPF exception",
        "RuntimeException: symbolic array length": "SPF exception",
        "NoUncaughtExceptionsProperty": "SPF exception",
        "ArrayIndexOutOfBoundsException (setDoubleValue)": "SPF exception",
        "ArrayIndexOutOfBoundsException (simple)": "SPF exception",
        "NullPointerException (queueMark)": "SPF exception",
        "ArrayIndexOutOfBoundsException (setLongValue)": "SPF exception",
        "NullPointerException (writeSpecificationFiles:147)": "Teralizer exception",
        "NullPointerException (writeSpecificationFiles:124)": "Teralizer exception",
        "Failed to collect specification": "Teralizer exception",
        "OutOfMemoryError: Java heap space": "OutOfMemoryError",
        "OutOfMemoryError: GC overhead": "OutOfMemoryError",
    }

    # Apply the mapping, keeping unmapped categories as is
    df["merged_category"] = (
        df["error_category"].map(category_map).fillna(df["error_category"])
    )

    # Group by merged_category and sum the counts
    df_merged = df.groupby("merged_category", as_index=False)["count"].sum()

    # Sort by count in descending order
    df_merged = df_merged.sort_values(by="count", ascending=False).reset_index(
        drop=True
    )

    # Add percent column
    total = df_merged["count"].sum()
    df_merged["percent"] = (df_merged["count"] / total * 100).apply(
        lambda x: f"{x:.2f}"
    )

    # Rename columns for consistency
    df_merged.rename(
        columns={
            "merged_category": "Error Type",
            "count": "Total",
            "percent": "Percent",
        },
        inplace=True,
    )

    return df_merged


def compute_test_failures_pivot(df: pd.DataFrame) -> Tuple[pd.DataFrame, List[str]]:
    """Create pivot table for test failures by variant.

    Args:
        df: Raw test failures data

    Returns:
        Pivoted DataFrame with failures by type and variant
    """
    # Get ordered variants
    ordered_variants = (
        df[["variant", "variant_order"]]
        .drop_duplicates()
        .sort_values("variant_order")["variant"]
        .tolist()
    )

    # Create pivot table
    df_agg = df.groupby(["failure_type", "variant"], as_index=False)["count"].sum()
    pivoted_df = df_agg.pivot(index="failure_type", columns="variant", values="count")
    pivoted_df = pivoted_df[ordered_variants].fillna(0).astype(int)

    return pivoted_df, ordered_variants


def compute_processing_pipeline_statistics(
    summary_df: pd.DataFrame,
) -> Tuple[pd.DataFrame, int, int]:
    """Compute processing pipeline failure statistics.

    Args:
        summary_df: Summary data from processing pipeline

    Returns:
        Tuple of (failures_per_stage DataFrame, total_projects int, success_count int)
    """
    total_projects = int(
        summary_df.loc[summary_df["status"] == "Total projects", "count"].values[0]
    )
    success_count = int(
        summary_df.loc[
            summary_df["status"] == "Successfully processed", "count"
        ].values[0]
    )

    failures_per_stage = summary_df[
        (summary_df["status"] != "Total projects")
        & (summary_df["status"] != "Successfully processed")
    ].copy()

    failures_per_stage["Failures"] = failures_per_stage["count"].astype(int)
    failures_per_stage = failures_per_stage.sort_values("step")
    failures_per_stage["Remaining"] = (
        total_projects - failures_per_stage["Failures"].cumsum()
    )

    return failures_per_stage, total_projects, success_count


def compute_failure_causes_by_stage(failures_df: pd.DataFrame) -> Dict[str, str]:
    """Classify failure causes by processing stage using regex patterns.

    Args:
        failures_df: DataFrame with stage and info columns

    Returns:
        Dictionary mapping stage to formatted cause descriptions
    """
    # Define cause patterns as established in legacy notebook
    cause_patterns = pd.DataFrame(
        [
            # SETUP_PROJECT
            (
                "SETUP_PROJECT",
                r"artifacts could not be resolved",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not find artifact",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"PluginVersionResolutionException",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not resolve dependencies",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Unresolveable build extension",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Detected the following recursive expression cycle",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"must be a valid version",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"must specify an absolute path",
                "dependency resolution error",
            ),
            (
                "SETUP_PROJECT",
                r"Could not find goal 'build-classpath' in plugin",
                "dependency resolution error",
            ),
            ("SETUP_PROJECT", r"Error injecting:", "dependency resolution error"),
            (
                "SETUP_PROJECT",
                r"No supported test framework identified",
                "sources / tests not found",
            ),
            (
                "SETUP_PROJECT",
                r"Test source path .+ does not exist.",
                "sources / tests not found",
            ),
            (
                "SETUP_PROJECT",
                r"Main source path .+ does not exist.",
                "sources / tests not found",
            ),
            # BUILD_PROJECT_ORIGINAL
            (
                "BUILD_PROJECT_ORIGINAL",
                r"teralizer.util.ConsoleCommandException",
                "compilation error",
            ),
            (
                "BUILD_PROJECT_ORIGINAL",
                r"Main compiled path .+ does not exist.",
                "compilation outputs not found",
            ),
            (
                "BUILD_PROJECT_ORIGINAL",
                r"Test compiled path .+ does not exist.",
                "compilation outputs not found",
            ),
            # BUILD_SPOON_MODEL
            (
                "BUILD_SPOON_MODEL",
                r"Modules are only available since Java 9.",
                "Spoon execution error",
            ),
            (
                "BUILD_SPOON_MODEL",
                r"The type package-info is already defined",
                "Spoon execution error",
            ),
            ("BUILD_SPOON_MODEL", None, "Spoon execution error"),
            # EXECUTE_TESTS_ORIGINAL
            (
                "EXECUTE_TESTS_ORIGINAL",
                r"teralizer.util.ConsoleCommandException",
                "JUnit execution error",
            ),
            (
                "EXECUTE_TESTS_ORIGINAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            # COLLECT_JUNIT_REPORTS_ORIGINAL
            (
                "COLLECT_JUNIT_REPORTS_ORIGINAL",
                r"Report directory .+ does not exist.",
                "JUnit outputs not found",
            ),
            (
                "COLLECT_JUNIT_REPORTS_ORIGINAL",
                r"Test file .+ does not exist.",
                "JUnit outputs not found",
            ),
            # BUILD_PROJECT_INSTRUMENTED
            (
                "BUILD_PROJECT_INSTRUMENTED",
                r"teralizer.util.ConsoleCommandException",
                "compilation error",
            ),
            # EXECUTE_TESTS_INITIAL
            (
                "EXECUTE_TESTS_INITIAL",
                r"All tests of the project are excluded.",
                "all tests excluded",
            ),
            (
                "EXECUTE_TESTS_INITIAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            # COLLECT_JACOCO_DATA_INITIAL
            (
                "COLLECT_JACOCO_DATA_INITIAL",
                r"Report file .+ does not exist.",
                "JaCoCo outputs not found",
            ),
            (
                "COLLECT_JACOCO_DATA_INITIAL",
                r"teralizer.util.ConsoleCommandException",
                "JaCoCo execution error",
            ),
            # COLLECT_PIT_DATA_INITIAL
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Command execution timeout exceeded.",
                "timeout exceeded",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"All classes of the project are excluded.",
                "all classes excluded",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Report file .+ does not exist.",
                "PIT outputs not found",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"teralizer.util.ConsoleCommandException",
                "PIT execution error",
            ),
            (
                "COLLECT_PIT_DATA_INITIAL",
                r"Failed to map coverage record to a test / generalization.",
                "failed to map PIT data to a test",
            ),
            # COLLECT_PIT_DATA_GENERALIZED
            (
                "COLLECT_PIT_DATA_GENERALIZED",
                r"All generalized tests of the project are excluded.",
                "all generalizations excluded",
            ),
            (
                "COLLECT_PIT_DATA_GENERALIZED",
                r"Failed to map coverage record to a test / generalization.",
                "failed to map PIT data to a generalization",
            ),
        ],
        columns=["stage", "pattern", "desc"],
    )

    # Classify each failure
    def match_cause(row):
        info = row["info"]
        for _, pat in cause_patterns.iterrows():
            # Handle null pattern: match if info is null
            if (
                pat["pattern"] is None
                and row["stage"] == pat["stage"]
                and pd.isnull(info)
            ):
                return pat["desc"]
            # Handle normal regex pattern: match if info is string and pattern is not None
            if (
                row["stage"] == pat["stage"]
                and pat["pattern"] is not None
                and isinstance(info, str)
                and re.search(pat["pattern"], info)
            ):
                return pat["desc"]
        return "other"

    failures_df["cause_desc"] = failures_df.apply(match_cause, axis=1)

    # Aggregate cause counts per stage
    stage_cause_counts = (
        failures_df.groupby(["stage", "cause_desc"])
        .size()
        .to_frame("count")
        .reset_index()
    )

    # Build stage -> "desc1 (n1), desc2 (n2), ..." mapping
    def format_causes(df):
        return ", ".join(
            f"{row['cause_desc']} ({row['count']})" for _, row in df.iterrows()
        )

    stage_to_causes = (
        stage_cause_counts.groupby("stage")[["cause_desc", "count"]]
        .apply(format_causes)
        .to_dict()
    )

    return stage_to_causes


# =============================================================================
# Generation Functions (generate_*)
# =============================================================================


def generate_exclusions_summary_table(result_df: pd.DataFrame) -> str:
    """Generate LaTeX table for overall exclusions summary.

    Args:
        result_df: DataFrame with exclusion percentages computed

    Returns:
        LaTeX table string
    """

    # Format percentages for LaTeX display
    def format_pct(val):
        return ("\\phantom{0}" if val < 10 else "") + f"{val:.1f}"

    result_df["included_pct_str"] = result_df["included_pct"].apply(format_pct)
    result_df["excluded_pct_str"] = result_df["excluded_pct"].apply(format_pct)

    # Format Included and Excluded columns as "count (pct%)"
    result_df["Included"] = result_df.apply(
        lambda row: f"{row['included_count']}\\; ({row['included_pct_str']}\\%)", axis=1
    )
    result_df["Excluded"] = result_df.apply(
        lambda row: f"{row['excluded_count']}\\; ({row['excluded_pct_str']}\\%)", axis=1
    )

    # Use variant macros for LaTeX output
    result_df = replace_variant_names_with_macros(result_df, "variant")

    # Use build_latex_table_content for consistent formatting
    columns = ["variant", "Type", "Total", "Included", "Excluded"]
    latex_content = build_latex_table_content(
        result_df[columns],
        caption="Included and excluded counts by variant and level.",
        label="tab:exclusions-summary",
        column_spec="llrrr",
        header_rows=[
            "Variant & Type & Total & \\multicolumn{1}{c}{Included} & \\multicolumn{1}{c}{Excluded} \\\\"
        ],
    )

    return latex_content


def generate_filtering_results_table(df: pd.DataFrame, label: str, caption: str) -> str:
    """Generate LaTeX table for filtering results using shared formatting functions.

    Args:
        df: Processed filtering data
        label: LaTeX label for table
        caption: LaTeX caption for table

    Returns:
        LaTeX table string
    """

    def format_count_pct(count, pct):
        phantom = "\\phantom{0}" if pct < 10 else ""
        return f"{count}\\; ({phantom}{pct:.1f}\\%)"

    df["Accept"] = df.apply(
        lambda row: format_count_pct(row["accept"], row["accept_pct"]), axis=1
    )
    df["Reject"] = df.apply(
        lambda row: format_count_pct(row["reject"], row["reject_pct"]), axis=1
    )

    # Use variant macros
    df = replace_variant_names_with_macros(df, "variant")

    # Use build_latex_table_content for consistent formatting
    columns = ["variant", "Type", "filter_name", "total", "Accept", "Reject"]
    df_display = df[columns].copy()
    df_display.columns = ["Variant", "Type", "Filter Name", "Total", "Accept", "Reject"]

    latex_content = build_latex_table_content(
        df_display,
        caption=caption,
        label=label,
        column_spec="lllrrr",
        header_rows=[
            "Variant & Type & Filter Name & Total & \\multicolumn{1}{c}{Accept} & \\multicolumn{1}{c}{Reject} \\\\"
        ],
        add_midrules=True,
    )

    return latex_content


def generate_spf_failures_table(df_merged: pd.DataFrame) -> str:
    """Generate LaTeX table for SPF failures using shared formatting functions.

    Args:
        df_merged: Categorized SPF failures data

    Returns:
        LaTeX table string
    """
    latex_content = build_latex_table_content(
        df_merged,
        caption="Number of SPF execution failures by error type.",
        label="tab:exclusions-spf",
        column_spec="l" + "r" * (len(df_merged.columns) - 1),
        add_midrules=False,
    )

    return latex_content


def generate_test_failures_table(
    pivoted_df: pd.DataFrame, ordered_variants: List[str]
) -> str:
    """Generate LaTeX table for test execution failures with complex header.

    Args:
        pivoted_df: Pivoted test failures data
        ordered_variants: List of variants in order

    Returns:
        LaTeX table string
    """
    # Build automated header generation for complex variant structure
    variant_info = []
    for v in ordered_variants:
        m = re.match(r"([A-Z]+)(?:_(\d+)_TRIES)?", v)
        if m:
            base = m.group(1)
            tries = m.group(2) if m.group(2) else "-"
            variant_info.append((v, base, tries))
        else:
            variant_info.append((v, v, "-"))

    # Group columns by base variant
    grouped = OrderedDict()
    for v, base, tries in variant_info:
        grouped.setdefault(base, []).append((v, tries))

    # Build header rows
    header1 = ["Variant"]
    header2 = ["Tries"]
    cmidrules = []
    col_idx = 2  # LaTeX columns start at 1, first is 'Variant'

    for base, cols in grouped.items():
        n = len(cols)
        if n == 1:
            header1.append(base)
            header2.append("-")
            col_idx += 1
        else:
            header1 += [f"\\multicolumn{{{n}}}{{c}}{{{base}}}"]
            header2 += [tries for _, tries in cols]
            # cmidrule for this group
            start = col_idx
            end = col_idx + n - 1
            cmidrules.append(f"\\cmidrule(lr){{{start}-{end}}}")
            col_idx += n

    header1_line = " & ".join(header1) + r" \\"
    header2_line = " & ".join(header2) + r" \\"
    cmidrules_line = "\n    ".join(cmidrules)

    # Build the table manually due to complex header structure
    latex_table = (
        r"""\begin{table}[H]
  \caption{Number of test execution failures by exception type and (generalization) variant.}
  \label{tab:exclusions-test-fails}
  \begin{tabular}{l"""
        + "r" * (len(pivoted_df.columns))
        + r"""}
    \toprule
    """
        + header1_line
        + "\n    "
        + cmidrules_line
        + "\n    "
        + header2_line
        + r"""
    \midrule
"""
    )

    # Data rows
    for failure_type, row in pivoted_df.iterrows():
        row_str = (
            "    "
            + str(failure_type)
            + " & "
            + " & ".join(str(x) for x in row.values)
            + r" \\"
        )
        latex_table += row_str + "\n"

    latex_table += r"""    \bottomrule
  \end{tabular}
\end{table}
"""

    return latex_table


def generate_processing_failures_tables(
    failures_per_stage: pd.DataFrame,
    total_projects: int,
    success_count: int,
    stage_to_causes: Dict[str, str],
) -> Tuple[str, str]:
    """Generate both processing failures tables using shared formatting.

    Args:
        failures_per_stage: DataFrame with failure statistics
        total_projects: Total number of projects
        success_count: Number of successfully processed projects
        stage_to_causes: Dictionary mapping stages to cause descriptions

    Returns:
        Tuple of (summary_table, causes_table) LaTeX strings
    """

    def format_remaining(n, total):
        percent = 100 * n / total if total else 0
        percent_str = f"{percent:.1f}"
        if percent == 100:
            percent_str = "\\phantom{.}100"
        if percent < 10:
            percent_str = f"\\phantom{{0}}{percent_str}"
        return f"{n}\\; ({percent_str} \\%)"

    def escape_latex(s):
        return str(s).replace("&", "\\&").replace("%", "\\%").replace("_", "\\_")

    # Build summary table data
    summary_data = []

    # Total projects row
    summary_data.append(
        {
            "Processing Stage": "Total projects",
            "Failures": "-",
            "Remaining Projects": format_remaining(total_projects, total_projects),
        }
    )

    # Failure rows
    for _, row in failures_per_stage.iterrows():
        summary_data.append(
            {
                "Processing Stage": escape_latex(row["status"]),
                "Failures": int(row["Failures"]),
                "Remaining Projects": format_remaining(
                    int(row["Remaining"]), total_projects
                ),
            }
        )

    # Success row
    summary_data.append(
        {
            "Processing Stage": "Successfully processed",
            "Failures": "-",
            "Remaining Projects": format_remaining(success_count, total_projects),
        }
    )

    summary_df = pd.DataFrame(summary_data)

    # Use build_latex_table_content for summary table
    summary_table = build_latex_table_content(
        summary_df,
        caption="Number of processing failures and remaining projects per processing stage.",
        label="tab:processing-failures-per-stage",
        column_spec="l r r",
        add_midrules=True,
    )

    # Build causes table
    stage_order = (
        failures_per_stage[["stage", "step"]]
        .drop_duplicates()
        .sort_values("step")
        .set_index("stage")
        .index.tolist()
    )

    causes_data = []
    for stage in stage_order:
        if stage in stage_to_causes:
            causes_data.append(
                {
                    "Processing Stage": escape_latex(stage),
                    "Causes of Processing Failures": stage_to_causes[stage],
                }
            )

    causes_df = pd.DataFrame(causes_data)

    # Manual table for causes due to tabularx requirement
    causes_table = r"""\begin{table}[H]
  \caption{Causes of processing failures per processing stage.}
  \label{tab:processing-failure-causes}
  \begin{tabularx}{\textwidth}{l X}
    \toprule
    Processing Stage & Causes of Processing Failures \\
    \midrule
"""
    for _, row in causes_df.iterrows():
        causes_table += f"    {row['Processing Stage']} & {row['Causes of Processing Failures']} \\\\\n"
    causes_table += r"""    \bottomrule
  \end{tabularx}
\end{table}
"""

    return summary_table, causes_table


# =============================================================================
# CSV Generation Functions (generate_*_csv)
# =============================================================================


def generate_exclusions_summary_csv(result_df: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for exclusions summary.

    Args:
        result_df: DataFrame with exclusion data

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in result_df.iterrows():
        csv_data.append(
            {
                "variant": get_variant_macro(row["variant"]),
                "exclusion_level": row["Type"],
                "total_count": int(row["Total"]),
                "included_count": int(row["included_count"]),
                "excluded_count": int(row["excluded_count"]),
                "included_percentage": float(row["included_pct"]),
                "excluded_percentage": float(row["excluded_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_filtering_results_csv(df: pd.DataFrame, dataset_type: str) -> pd.DataFrame:
    """Generate CSV data for filtering results.

    Args:
        df: Filtering data
        dataset_type: Type of dataset ('main' or 'extended')

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df.iterrows():
        csv_data.append(
            {
                "dataset": dataset_type,
                "variant": get_variant_macro(row["variant"]),
                "exclusion_level": row["Type"],
                "filter_name": row["filter_name"],
                "total_count": int(row["total"]),
                "accept_count": int(row["accept"]),
                "reject_count": int(row["reject"]),
                "accept_percentage": float(row["accept_pct"]),
                "reject_percentage": float(row["reject_pct"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_spf_failures_csv(df_merged: pd.DataFrame) -> pd.DataFrame:
    """Generate CSV data for SPF failures.

    Args:
        df_merged: Categorized SPF failures data

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for _, row in df_merged.iterrows():
        csv_data.append(
            {
                "error_type": row["Error Type"],
                "failure_count": int(row["Total"]),
                "percentage": float(row["Percent"]),
            }
        )
    return pd.DataFrame(csv_data)


def generate_test_failures_csv(
    pivoted_df: pd.DataFrame, ordered_variants: List[str]
) -> pd.DataFrame:
    """Generate CSV data for test failures.

    Args:
        pivoted_df: Pivoted test failures data
        ordered_variants: List of variants in order

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for failure_type, row in pivoted_df.iterrows():
        for variant in ordered_variants:
            csv_data.append(
                {
                    "failure_type": failure_type,
                    "variant": get_variant_macro(variant),
                    "failure_count": int(row[variant]),
                }
            )
    return pd.DataFrame(csv_data)


def generate_processing_failures_csv(
    failures_per_stage: pd.DataFrame, total_projects: int, success_count: int
) -> pd.DataFrame:
    """Generate CSV data for processing failures summary.

    Args:
        failures_per_stage: DataFrame with failure statistics
        total_projects: Total number of projects
        success_count: Number of successfully processed projects

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []

    # Add total projects row
    csv_data.append(
        {
            "processing_stage": "Total projects",
            "failure_count": None,
            "remaining_count": total_projects,
            "remaining_percentage": 100.0,
        }
    )

    # Add failure rows
    for _, row in failures_per_stage.iterrows():
        remaining_count = int(row["Remaining"])
        remaining_percentage = 100 * remaining_count / total_projects
        csv_data.append(
            {
                "processing_stage": row["status"],
                "failure_count": int(row["Failures"]),
                "remaining_count": remaining_count,
                "remaining_percentage": round(remaining_percentage, 1),
            }
        )

    # Add success row
    csv_data.append(
        {
            "processing_stage": "Successfully processed",
            "failure_count": None,
            "remaining_count": success_count,
            "remaining_percentage": round(100 * success_count / total_projects, 1),
        }
    )

    return pd.DataFrame(csv_data)


def generate_processing_failure_causes_csv(
    stage_to_causes: Dict[str, str],
) -> pd.DataFrame:
    """Generate CSV data for processing failure causes.

    Args:
        stage_to_causes: Dictionary mapping stages to causes

    Returns:
        DataFrame formatted for CSV export
    """
    csv_data = []
    for stage, causes in stage_to_causes.items():
        csv_data.append({"processing_stage": stage, "failure_causes": causes})
    return pd.DataFrame(csv_data)
