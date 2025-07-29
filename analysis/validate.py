#!/usr/bin/env python3
"""
Basic validation script for the analysis notebooks.
Verifies that essential imports work and database connections are available.
"""

import sys
import importlib
import os
import subprocess
import tempfile
import re
import argparse
from pathlib import Path
from dotenv import load_dotenv


def test_imports():
    """Test that all required packages can be imported."""
    print("Testing imports...")
    required_packages = [
        "pandas",
        "matplotlib",
        "sqlalchemy",
        "psycopg2",
        "dotenv",
        "natsort",
    ]

    failed_imports = []
    for package in required_packages:
        try:
            importlib.import_module(package)
            print(f"  ✓ {package}")
        except ImportError as e:
            print(f"  ✗ {package}: {e}")
            failed_imports.append(package)

    # Test local teralizer package (editable install)
    try:
        importlib.import_module("teralizer")
        print("  ✓ teralizer (editable install)")
    except ImportError as e:
        print(f"  ✗ teralizer (editable install): {e}")
        failed_imports.append("teralizer")

    return len(failed_imports) == 0


def test_environment():
    """Test that environment variables are loaded correctly."""
    print("\nTesting environment...")

    # Load environment from the correct location
    env_path = "../.env"
    if not os.path.exists(env_path):
        print(f"  ✗ Environment file not found at {env_path}")
        return False

    load_dotenv(env_path, override=True)

    required_vars = ["DB_HOST", "DB_PORT", "DB_NAME", "DB_USER", "DB_PASSWORD"]
    missing_vars = []

    for var in required_vars:
        value = os.getenv(var)
        if value:
            print(f"  ✓ {var} = {value}")
        else:
            print(f"  ✗ {var} not found")
            missing_vars.append(var)

    return len(missing_vars) == 0


def test_database_connection():
    """Test database connectivity and schema validation."""
    print("\nTesting database connections...")

    try:
        from teralizer.config import db_config

        # Test postgres_dev (eqbench and commons-utils projects)
        print("  Testing postgres_dev...")
        try:
            # Validate=True will check schema automatically
            db_config.get_dev_engine(validate=True)
            print("  ✓ postgres_dev connection successful and schema valid")
        except ConnectionError as e:
            print(f"  ✗ postgres_dev connection failed: {e}")
            return False
        except RuntimeError as e:
            print(
                f"  ✗ postgres_dev schema validation failed:\n    {str(e).replace(chr(10), chr(10) + '    ')}"
            )
            return False
        except Exception as e:
            print(f"  ✗ postgres_dev unexpected error: {e}")
            return False

        # Test postgres_test (repo-reapers projects)
        print("  Testing postgres_test...")
        try:
            db_config.get_test_engine(validate=True)
            print("  ✓ postgres_test connection successful and schema valid")
        except ConnectionError:
            print("  ℹ postgres_test not available: Connection failed")
        except RuntimeError as e:
            print(
                f"  ℹ postgres_test schema incomplete:\n    {str(e).split(chr(10))[0]}..."
            )
        except Exception as e:
            print(f"  ℹ postgres_test unavailable: {type(e).__name__}")

        return True

    except Exception as e:
        print(f"  ✗ Database test setup failed: {e}")
        return False


def parse_expected_outputs(notebook_path):
    """Parse notebook to discover expected output files from export function calls.

    Args:
        notebook_path: Path to the notebook file

    Returns:
        Dict with 'tables' and 'data' keys containing lists of expected filenames
    """
    import nbformat

    expected = {"tables": [], "data": []}

    # Load notebook
    with open(notebook_path, "r", encoding="utf-8") as f:
        notebook = nbformat.read(f, as_version=4)

    # Search code cells for export function calls
    for cell in notebook.cells:
        if cell.cell_type == "code":
            source = cell.source

            # Find save_latex_table calls
            latex_matches = re.findall(
                r"save_latex_table\s*\([^,]+,\s*['\"]([^'\"]+)['\"]", source
            )
            for match in latex_matches:
                expected["tables"].append(f"{match}.tex")

            # Find save_csv_data calls
            csv_matches = re.findall(
                r"save_csv_data\s*\([^,]+,\s*['\"]([^'\"]+)['\"]", source
            )
            for match in csv_matches:
                expected["data"].append(f"{match}.csv")

    return expected


def get_changed_notebooks():
    """Get list of notebooks that have been modified according to git.

    Returns:
        List of notebook filenames that have uncommitted changes
    """
    try:
        # Get modified files from git
        result = subprocess.run(
            ["git", "diff", "--name-only", "HEAD"],
            capture_output=True,
            text=True,
            cwd="..",
        )

        if result.returncode != 0:
            print("    Warning: git diff failed, testing all notebooks")
            return None

        changed_files = (
            result.stdout.strip().split("\n") if result.stdout.strip() else []
        )

        # Filter for notebooks in analysis/notebooks/
        notebooks_dir = Path("notebooks")
        changed_notebooks = []

        for file_path in changed_files:
            file_path = Path(file_path)
            # Check if it's a notebook in our analysis/notebooks directory
            if (
                file_path.suffix == ".ipynb"
                and len(file_path.parts) >= 2
                and file_path.parts[-2] == "notebooks"
                and file_path.parts[-3] == "analysis"
            ):
                notebook_name = file_path.name
                if (notebooks_dir / notebook_name).exists():
                    changed_notebooks.append(notebook_name)

        return changed_notebooks

    except Exception as e:
        print(f"    Warning: Could not determine changed notebooks: {e}")
        return None


def validate_outputs(notebook_name, expected_outputs):
    """Validate that expected output files exist and are non-empty.

    Args:
        notebook_name: Name of the notebook for reporting
        expected_outputs: Dict with 'tables' and 'data' lists

    Returns:
        True if all expected outputs are valid, False otherwise
    """
    from teralizer.exports import get_tables_output_dir, get_data_output_dir

    all_valid = True

    # Check LaTeX tables
    tables_dir = get_tables_output_dir()
    for table_file in expected_outputs["tables"]:
        table_path = tables_dir / table_file
        if not table_path.exists():
            print(f"    ✗ Missing LaTeX table: {table_file}")
            all_valid = False
        elif table_path.stat().st_size == 0:
            print(f"    ✗ Empty LaTeX table: {table_file}")
            all_valid = False
        else:
            print(f"    ✓ LaTeX table: {table_file}")

    # Check CSV data files
    data_dir = get_data_output_dir()
    for data_file in expected_outputs["data"]:
        data_path = data_dir / data_file
        if not data_path.exists():
            print(f"    ✗ Missing CSV file: {data_file}")
            all_valid = False
        elif data_path.stat().st_size == 0:
            print(f"    ✗ Empty CSV file: {data_file}")
            all_valid = False
        else:
            print(f"    ✓ CSV file: {data_file}")

    return all_valid


def test_notebook_execution(specific_notebook=None, changed_only=False):
    """Test that notebooks can execute without errors.

    Args:
        specific_notebook: If provided, only test this specific notebook
        changed_only: If True, only test notebooks that have git changes
    """
    print("\nTesting notebook execution...")

    notebooks_dir = Path("notebooks")
    if not notebooks_dir.exists():
        print(f"  ✗ Notebooks directory not found: {notebooks_dir}")
        return False

    # Discover available notebooks
    all_notebooks = sorted([f.name for f in notebooks_dir.glob("*.ipynb")])

    # Determine which notebooks to test
    if specific_notebook:
        if not (notebooks_dir / specific_notebook).exists():
            print(f"  ✗ Notebook not found: {specific_notebook}")
            return False
        notebooks = [specific_notebook]
        print(f"  Testing specific notebook: {specific_notebook}")
    elif changed_only:
        changed_notebooks = get_changed_notebooks()
        if changed_notebooks is None:
            # Fall back to testing all notebooks if git detection failed
            notebooks = all_notebooks
        elif not changed_notebooks:
            print("  ℹ No notebook changes detected, skipping notebook execution")
            return True
        else:
            notebooks = changed_notebooks
            print(f"  Testing changed notebooks: {', '.join(notebooks)}")
    else:
        notebooks = all_notebooks
        print(f"  Testing all {len(notebooks)} notebooks")

    failed_notebooks = []
    successful_notebooks = []

    for notebook in notebooks:
        notebook_path = notebooks_dir / notebook
        if not notebook_path.exists():
            print(f"  ℹ {notebook}: Not found (skipping)")
            continue

        print(f"  Testing {notebook}...")

        # Create temporary output file
        with tempfile.NamedTemporaryFile(suffix=".ipynb", delete=False) as temp_file:
            temp_output = temp_file.name

        try:
            # Execute notebook using nbconvert
            result = subprocess.run(
                [
                    "jupyter",
                    "nbconvert",
                    "--execute",
                    "--to",
                    "notebook",
                    "--output",
                    temp_output,
                    "--ExecutePreprocessor.timeout=300",  # 5-minute timeout
                    str(notebook_path),
                ],
                capture_output=True,
                text=True,
                cwd=notebooks_dir.parent,
            )

            if result.returncode == 0:
                print(f"    ✓ {notebook}: Executed successfully")

                # Parse expected outputs and validate them
                expected_outputs = parse_expected_outputs(notebook_path)
                total_expected = len(expected_outputs["tables"]) + len(
                    expected_outputs["data"]
                )

                if total_expected > 0:
                    print(f"    Validating {total_expected} expected output files...")
                    if validate_outputs(notebook, expected_outputs):
                        print(f"    ✓ {notebook}: All outputs validated")
                        successful_notebooks.append(notebook)
                    else:
                        print(f"    ✗ {notebook}: Output validation failed")
                        failed_notebooks.append(notebook)
                else:
                    print(f"    ℹ {notebook}: No output files expected")
                    successful_notebooks.append(notebook)
            else:
                print(f"    ✗ {notebook}: Execution failed")
                print("    STDERR:")
                for line in result.stderr.split("\n"):
                    if line.strip():
                        print(f"      {line}")
                if result.stdout.strip():
                    print("    STDOUT:")
                    for line in result.stdout.split("\n"):
                        if line.strip():
                            print(f"      {line}")
                failed_notebooks.append(notebook)

        except FileNotFoundError:
            print(f"    ✗ {notebook}: jupyter nbconvert not found")
            print("      Make sure Jupyter is installed: uv add jupyter")
            failed_notebooks.append(notebook)
        except Exception as e:
            print(f"    ✗ {notebook}: Unexpected error: {e}")
            failed_notebooks.append(notebook)
        finally:
            # Clean up temporary file
            try:
                os.unlink(temp_output)
            except FileNotFoundError:
                pass

    # Summary
    total_tested = len(successful_notebooks) + len(failed_notebooks)
    if total_tested == 0:
        print("  ℹ No notebooks found to test")
        return True  # Not a failure if no notebooks exist

    print("\n  Notebook Execution Summary:")
    print(f"    ✓ Successful: {len(successful_notebooks)}")
    print(f"    ✗ Failed: {len(failed_notebooks)}")

    if failed_notebooks:
        print(f"    Failed notebooks: {', '.join(failed_notebooks)}")
        return False

    return True


def test_code_quality():
    """Test code quality using ruff and ty."""
    print("\nTesting code quality...")

    all_passed = True

    # Test ruff linting
    print("  Running ruff linting...")
    try:
        result = subprocess.run(
            ["uv", "run", "ruff", "check", "."], capture_output=True, text=True, cwd="."
        )

        if result.returncode == 0:
            print("    ✓ Ruff linting: All checks passed!")
        else:
            print("    ✗ Ruff linting: Issues found")
            if result.stdout.strip():
                for line in result.stdout.split("\n"):
                    if line.strip():
                        print(f"      {line}")
            all_passed = False
    except Exception as e:
        print(f"    ✗ Ruff linting failed to run: {e}")
        all_passed = False

    # Test type checking
    print("  Running type checking...")
    try:
        result = subprocess.run(
            ["uv", "run", "ty", "check", "src/", "tests/", "validate.py"],
            capture_output=True,
            text=True,
            cwd=".",
        )

        if result.returncode == 0:
            print("    ✓ Type checking: All checks passed!")
        else:
            print("    ✗ Type checking: Issues found")
            if result.stdout.strip():
                for line in result.stdout.split("\n"):
                    if line.strip():
                        print(f"      {line}")
            all_passed = False
    except Exception as e:
        print(f"    ✗ Type checking failed to run: {e}")
        all_passed = False

    return all_passed


def parse_arguments():
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Validate analysis notebooks and environment"
    )
    parser.add_argument(
        "--notebook", type=str, help="Validate only the specified notebook"
    )
    parser.add_argument(
        "--changed",
        action="store_true",
        help="Validate only notebooks with git changes",
    )

    return parser.parse_args()


def main():
    """Run all validation tests."""
    args = parse_arguments()

    print("=== Analysis Directory Validation ===\n")

    # Always run basic tests
    basic_tests = [
        ("Import Test", test_imports),
        ("Environment Test", test_environment),
        ("Database Test", test_database_connection),
    ]

    all_passed = True

    for test_name, test_func in basic_tests:
        try:
            if not test_func():
                all_passed = False
        except Exception as e:
            print(f"  ✗ {test_name} crashed: {e}")
            all_passed = False

    # Run notebook execution test with appropriate parameters
    try:
        if not test_notebook_execution(
            specific_notebook=args.notebook, changed_only=args.changed
        ):
            all_passed = False
    except Exception as e:
        print(f"  ✗ Notebook Execution Test crashed: {e}")
        all_passed = False

    # Always run code quality tests (type checking and linting)
    try:
        if not test_code_quality():
            all_passed = False
    except Exception as e:
        print(f"  ✗ Code Quality Test crashed: {e}")
        all_passed = False

    print(f"\n=== Validation {'PASSED' if all_passed else 'FAILED'} ===")
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
