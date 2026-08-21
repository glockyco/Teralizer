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
        from teralizer import corpora
        from teralizer.config import db_config

        connected = 0
        for entry in corpora.load().published_entries:
            print(f"  Testing corpus {entry.id}...")
            try:
                engine = db_config.get_engine(entry.database, validate=False)
                with engine.connect() as connection:
                    corpora.validate_project_count(connection, entry)
                engine.dispose()
                connected += 1
                print(f"  ✓ {entry.id} connection successful and identity valid")
            except ConnectionError:
                print(f"  ℹ {entry.id} not installed on this workstation")
            except RuntimeError as error:
                detail = str(error).split(chr(10))[0]
                print(f"  ✗ {entry.id} identity validation failed: {detail}")
                return False
            except Exception as error:
                print(f"  ✗ {entry.id} unexpected error: {error}")
                return False
        if connected == 0:
            print("  ✗ No registered corpus is reachable")
            return False
        return True
    except Exception as error:
        print(f"  ✗ Database test setup failed: {error}")
        return False


def test_code_quality() -> bool:
    """Run the analysis lint and type checks."""
    print("\nTesting code quality...")
    checks = [
        # --check, because a validator reports state and never changes it.
        ("ruff formatting", ["uv", "run", "ruff", "format", "--check", "."]),
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
