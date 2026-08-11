#!/usr/bin/env python3
"""Validate the analysis environment, database access, and code quality."""

from __future__ import annotations

import argparse
import importlib
import os
import subprocess
import sys

from dotenv import load_dotenv


def test_imports() -> bool:
    """Test that the packages required by the analysis can be imported."""
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
        except ImportError as error:
            print(f"  ✗ {package}: {error}")
            failed_imports.append(package)

    try:
        importlib.import_module("teralizer")
        print("  ✓ teralizer (editable install)")
    except ImportError as error:
        print(f"  ✗ teralizer (editable install): {error}")
        failed_imports.append("teralizer")

    return not failed_imports


def test_environment() -> bool:
    """Test that the repository environment variables are available."""
    print("\nTesting environment...")
    env_path = "../.env"
    if not os.path.exists(env_path):
        print(f"  ✗ Environment file not found at {env_path}")
        return False

    load_dotenv(env_path)
    required_vars = ["DB_HOST", "DB_PORT", "DB_USER", "DB_PASSWORD"]
    missing_vars = []
    for variable in required_vars:
        value = os.getenv(variable)
        if value:
            print(f"  ✓ {variable} = {value}")
        else:
            print(f"  ✗ {variable} not found")
            missing_vars.append(variable)

    return not missing_vars


def test_database_connection() -> bool:
    """Test database connectivity and schema validation."""
    print("\nTesting database connections...")
    try:
        from teralizer.config import db_config

        print("  Testing postgres_dev...")
        try:
            db_config.get_dev_engine(validate=True)
            print("  ✓ postgres_dev connection successful and schema valid")
        except ConnectionError as error:
            print(f"  ✗ postgres_dev connection failed: {error}")
            return False
        except RuntimeError as error:
            detail = str(error).replace(chr(10), chr(10) + "    ")
            print(f"  ✗ postgres_dev schema validation failed:\n    {detail}")
            return False
        except Exception as error:
            print(f"  ✗ postgres_dev unexpected error: {error}")
            return False

        print("  Testing postgres_test...")
        try:
            db_config.get_test_engine(validate=True)
            print("  ✓ postgres_test connection successful and schema valid")
        except ConnectionError:
            print("  ℹ postgres_test not available: Connection failed")
        except RuntimeError as error:
            print(f"  ℹ postgres_test schema incomplete:\n    {str(error).split(chr(10))[0]}...")
        except Exception as error:
            print(f"  ℹ postgres_test unavailable: {type(error).__name__}")

        return True
    except Exception as error:
        print(f"  ✗ Database test setup failed: {error}")
        return False


def test_code_quality() -> bool:
    """Run the analysis lint and type checks."""
    print("\nTesting code quality...")
    checks = [
        ("ruff formatting", ["uv", "run", "ruff", "format", "."]),
        ("ruff linting", ["uv", "run", "ruff", "check", "."]),
        ("type checking", ["uv", "run", "ty", "check", "src/", "tests/"]),
    ]
    all_passed = True
    for name, command in checks:
        print(f"  Running {name}...")
        try:
            result = subprocess.run(command, capture_output=True, text=True, cwd=".")
        except OSError as error:
            print(f"    ✗ {name} failed to run: {error}")
            all_passed = False
            continue
        if result.returncode == 0:
            print(f"    ✓ {name}: passed")
        else:
            print(f"    ✗ {name}: failed")
            output = result.stderr or result.stdout
            if output.strip():
                print(output.rstrip())
            all_passed = False
    return all_passed


def parse_arguments() -> argparse.Namespace:
    """Parse validator options."""
    parser = argparse.ArgumentParser(
        description="Validate the analysis environment and code quality"
    )
    return parser.parse_args()


def main() -> int:
    """Run environment, database, and code-quality checks."""
    parse_arguments()
    print("=== Analysis Directory Validation ===\n")
    checks = [
        ("Import Test", test_imports),
        ("Environment Test", test_environment),
        ("Database Test", test_database_connection),
        ("Code Quality Test", test_code_quality),
    ]
    all_passed = True
    for name, check in checks:
        try:
            if not check():
                all_passed = False
        except Exception as error:
            print(f"  ✗ {name} crashed: {error}")
            all_passed = False

    print(f"\n=== Validation {'PASSED' if all_passed else 'FAILED'} ===")
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
