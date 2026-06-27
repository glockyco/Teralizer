"""Verify analysis outputs match reference.

This module compares output variants (original vs verify, original vs replicate)
to confirm reproducibility. It handles:
- Trailing whitespace normalization for text files
- Semantic numeric comparison for CSVs
- File count validation
- Clear pass/fail reporting

Usage:
    python -m teralizer.verify original verify
    python -m teralizer.verify original replicate
"""

import argparse
import sys
from pathlib import Path
from typing import Optional

import pandas as pd

# ANSI color codes for terminal output
GREEN = "\033[0;32m"
RED = "\033[0;31m"
YELLOW = "\033[1;33m"
CYAN = "\033[0;36m"
NC = "\033[0m"  # No Color


def _find_output_dir() -> Path:
    """Find the analysis/output directory.

    Returns:
        Path to output directory

    Raises:
        RuntimeError: If output directory cannot be found
    """
    # Try relative to this file (analysis/src/teralizer/verify.py)
    current = Path(__file__).parent
    for _ in range(5):
        output_dir = current / "analysis" / "output"
        if output_dir.exists():
            return output_dir
        # Also check if we're already in analysis/
        output_dir = current / "output"
        if output_dir.exists():
            return output_dir
        current = current.parent

    # Fallback: assume standard layout
    return Path(__file__).parent.parent.parent / "output"


def normalize_text(content: str) -> str:
    """Normalize text content for comparison.

    Strips trailing whitespace from each line.

    Args:
        content: Text content to normalize

    Returns:
        Normalized text content
    """
    return "\n".join(line.rstrip() for line in content.split("\n"))


def compare_text_files(base_path: Path, target_path: Path) -> tuple[bool, str]:
    """Compare two text files with whitespace normalization.

    Args:
        base_path: Path to base file
        target_path: Path to target file

    Returns:
        Tuple of (match: bool, message: str)
    """
    if not base_path.exists():
        return False, f"Base file missing: {base_path.name}"
    if not target_path.exists():
        return False, f"Target file missing: {target_path.name}"

    base_content = normalize_text(base_path.read_text())
    target_content = normalize_text(target_path.read_text())

    if base_content == target_content:
        return True, "identical"

    # Count differing lines for more info
    base_lines = base_content.split("\n")
    target_lines = target_content.split("\n")

    if len(base_lines) != len(target_lines):
        return False, f"line count differs ({len(base_lines)} vs {len(target_lines)})"

    diff_count = sum(1 for b, t in zip(base_lines, target_lines) if b != t)
    return False, f"{diff_count} lines differ"


def compare_csv_files(
    base_path: Path, target_path: Path, tolerance: float = 1e-6
) -> tuple[bool, str]:
    """Compare two CSV files with numeric tolerance.

    Args:
        base_path: Path to base CSV file
        target_path: Path to target CSV file
        tolerance: Numeric tolerance for floating point comparison

    Returns:
        Tuple of (match: bool, message: str)
    """
    if not base_path.exists():
        return False, f"Base file missing: {base_path.name}"
    if not target_path.exists():
        return False, f"Target file missing: {target_path.name}"

    try:
        base_df = pd.read_csv(base_path)
        target_df = pd.read_csv(target_path)
    except Exception as e:
        return False, f"CSV parse error: {e}"

    # Check shape
    if base_df.shape != target_df.shape:
        return False, f"shape differs ({base_df.shape} vs {target_df.shape})"

    # Check columns
    if list(base_df.columns) != list(target_df.columns):
        return False, "columns differ"

    # Compare data
    for col in base_df.columns:
        base_col = base_df[col]
        target_col = target_df[col]

        # Numeric comparison with tolerance (excluding booleans)
        if pd.api.types.is_numeric_dtype(base_col) and not pd.api.types.is_bool_dtype(
            base_col
        ):
            if not pd.api.types.is_numeric_dtype(target_col):
                return False, f"column '{col}' type mismatch"
            # Use numpy for subtraction to handle edge cases
            diff = (base_col.to_numpy() - target_col.to_numpy()).astype(float)
            if not (abs(diff) <= tolerance).all():
                return False, f"column '{col}' values differ beyond tolerance"
        else:
            # String/boolean comparison
            if not base_col.equals(target_col):
                return False, f"column '{col}' values differ"

    return True, "identical"


def compare_directories(
    base_dir: Path, target_dir: Path, pattern: str, comparator: str = "text"
) -> tuple[int, int, list[str]]:
    """Compare all files matching pattern between two directories.

    Args:
        base_dir: Base directory path
        target_dir: Target directory path
        pattern: Glob pattern for files to compare
        comparator: "text" or "csv" comparison method

    Returns:
        Tuple of (matches, differences, diff_messages)
    """
    matches = 0
    differences = 0
    diff_messages = []

    # Get all files from both directories
    base_files = (
        {f.name: f for f in base_dir.glob(pattern)} if base_dir.exists() else {}
    )
    target_files = (
        {f.name: f for f in target_dir.glob(pattern)} if target_dir.exists() else {}
    )

    all_files = set(base_files.keys()) | set(target_files.keys())

    for filename in sorted(all_files):
        base_path = base_files.get(filename)
        target_path = target_files.get(filename)

        if base_path is None:
            differences += 1
            diff_messages.append(f"  {CYAN}Extra:{NC} {filename}")
            continue

        if target_path is None:
            differences += 1
            diff_messages.append(f"  {RED}Missing:{NC} {filename}")
            continue

        # Compare files
        if comparator == "csv":
            match, msg = compare_csv_files(base_path, target_path)
        else:
            match, msg = compare_text_files(base_path, target_path)

        if match:
            matches += 1
        else:
            differences += 1
            diff_messages.append(f"  {YELLOW}Differs:{NC} {filename} ({msg})")

    return matches, differences, diff_messages


def compare_outputs(base: str, target: str, output_dir: Optional[Path] = None) -> bool:
    """Compare analysis outputs between two variants.

    Args:
        base: Base variant name (e.g., "original")
        target: Target variant name (e.g., "verify")
        output_dir: Optional output directory path

    Returns:
        True if all outputs match, False otherwise
    """
    if output_dir is None:
        output_dir = _find_output_dir()

    base_dir = output_dir / base
    target_dir = output_dir / target

    # Validate directories exist
    if not base_dir.exists():
        print(f"{RED}Error: Base directory does not exist: {base_dir}{NC}")
        return False

    if not target_dir.exists():
        print(f"{RED}Error: Target directory does not exist: {target_dir}{NC}")
        return False

    print()
    print("==========================================")
    print("  Output Comparison")
    print(f"  Base:   {base}")
    print(f"  Target: {target}")
    print("==========================================")
    print()

    total_errors = 0

    # Compare tables (LaTeX files)
    print(f"{CYAN}Tables (*.tex):{NC}")
    matches, diffs, msgs = compare_directories(
        base_dir / "tables", target_dir / "tables", "*.tex", "text"
    )
    if diffs == 0:
        print(f"  {GREEN}✓{NC} All {matches} tables identical")
    else:
        print(f"  {YELLOW}!{NC} {diffs} of {matches + diffs} tables differ")
        for msg in msgs:
            print(msg)
        total_errors += diffs
    print()

    # Compare data (CSV files)
    print(f"{CYAN}Data (*.csv):{NC}")
    matches, diffs, msgs = compare_directories(
        base_dir / "data", target_dir / "data", "*.csv", "csv"
    )
    if diffs == 0:
        print(f"  {GREEN}✓{NC} All {matches} data files identical")
    else:
        print(f"  {YELLOW}!{NC} {diffs} of {matches + diffs} data files differ")
        for msg in msgs:
            print(msg)
        total_errors += diffs
    print()

    # Compare figures (PDF files - just check existence, can't compare binary)
    print(f"{CYAN}Figures (*.pdf):{NC}")
    base_figs = (
        set(f.name for f in (base_dir / "figures").glob("*.pdf"))
        if (base_dir / "figures").exists()
        else set()
    )
    target_figs = (
        set(f.name for f in (target_dir / "figures").glob("*.pdf"))
        if (target_dir / "figures").exists()
        else set()
    )

    if base_figs == target_figs:
        print(f"  {GREEN}✓{NC} All {len(base_figs)} figures present")
    else:
        missing = base_figs - target_figs
        extra = target_figs - base_figs
        if missing:
            print(f"  {RED}Missing:{NC} {', '.join(sorted(missing))}")
            total_errors += len(missing)
        if extra:
            print(f"  {CYAN}Extra:{NC} {', '.join(sorted(extra))}")
    print()

    # Summary
    print("==========================================")
    if total_errors == 0:
        print(f"{GREEN}All outputs match{NC}")

        # Provide context based on comparison type
        if base == "original" and target == "verify":
            print()
            print("Verification successful: re-running analysis on")
            print("original data produces identical results.")
    else:
        print(f"{YELLOW}{total_errors} difference(s) found{NC}")

        # Provide context based on comparison type
        if base == "original" and target == "verify":
            print()
            print("Note: original vs verify should be identical.")
            print("Any differences indicate a bug in the analysis.")
        elif base == "original" and target == "replicate":
            print()
            print("Note: original vs replicate may differ due to")
            print("non-deterministic factors in the pipeline (randomized")
            print("test generation, timeouts, resource limits, etc.).")
    print("==========================================")
    print()

    return total_errors == 0


def main() -> int:
    """Main entry point for verification CLI.

    Returns:
        Exit code (0 for success, 1 for failure)
    """
    parser = argparse.ArgumentParser(
        description="Compare analysis outputs between variants",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s original verify      # Verify re-run matches original
  %(prog)s original replicate   # Compare replication results

Expected results:
  original vs verify:    Should be IDENTICAL (same data, same analysis)
  original vs replicate: May differ (non-deterministic pipeline factors)
""",
    )
    parser.add_argument(
        "base", help="Base variant to compare against (original, verify, replicate)"
    )
    parser.add_argument(
        "target", help="Target variant to compare (original, verify, replicate)"
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        help="Output directory (default: auto-detect)",
    )

    args = parser.parse_args()

    # Validate variant names
    valid_variants = {"original", "verify", "replicate"}
    if args.base not in valid_variants:
        print(f"{RED}Error: Invalid base variant '{args.base}'{NC}")
        print(f"Valid variants: {', '.join(sorted(valid_variants))}")
        return 1
    if args.target not in valid_variants:
        print(f"{RED}Error: Invalid target variant '{args.target}'{NC}")
        print(f"Valid variants: {', '.join(sorted(valid_variants))}")
        return 1

    success = compare_outputs(args.base, args.target, args.output_dir)
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
