#!/usr/bin/env python3
"""
Sync analysis outputs to paper repository.

This script copies all generated analysis outputs (figures, tables, CSV data)
from their respective output directories to the paper repository with organized structure.

Usage:
    python sync.py                           # Uses PAPER_REPO_PATH from .env
    python sync.py /path/to/paper/repo       # Uses explicit path

    # With uv (recommended):
    uv run --directory analysis python sync.py
    uv run --directory analysis python sync.py /path/to/paper/repo
"""

import sys
import os
from pathlib import Path
from dotenv import load_dotenv
import shutil

# Import the output directory functions we need
from teralizer.exports import (
    get_figures_output_dir,
    get_tables_output_dir,
    get_data_output_dir,
)


def get_paper_repo_path() -> Path:
    """Get paper repository path from environment or command line argument.

    Returns:
        Path: Path to paper repository

    Raises:
        ValueError: If no path provided and environment variable not set
        FileNotFoundError: If specified path doesn't exist
    """
    # Check command line argument first
    if len(sys.argv) > 1:
        paper_repo_path = Path(sys.argv[1])
    else:
        # Fall back to environment variable
        env_path = os.getenv("PAPER_REPO_PATH")
        if not env_path:
            raise ValueError(
                "No paper repository path provided. Either:\n"
                "  1. Set PAPER_REPO_PATH in your .env file, or\n"
                "  2. Provide path as argument: python sync.py /path/to/paper/repo"
            )
        paper_repo_path = Path(env_path)

    # Validate path exists
    if not paper_repo_path.exists():
        raise FileNotFoundError(
            f"Paper repository not found at: {paper_repo_path}\n"
            f"Please check the path and ensure the repository exists."
        )

    if not paper_repo_path.is_dir():
        raise FileNotFoundError(
            f"Path exists but is not a directory: {paper_repo_path}"
        )

    return paper_repo_path


def sync_files(
    source_dir: Path, target_dir: Path, pattern: str, file_type: str
) -> dict:
    """Sync files matching pattern from source to target directory.

    Args:
        source_dir: Source directory to copy from
        target_dir: Target directory to copy to
        pattern: Glob pattern to match files (e.g., '*.pdf')
        file_type: Human-readable description of file type

    Returns:
        Dict with 'copied', 'cleaned', 'warnings' counts and 'unexpected_files' list
    """
    # Create target directory if it doesn't exist
    target_dir.mkdir(parents=True, exist_ok=True)

    # Find files to sync
    files = list(source_dir.glob(pattern))

    if not files:
        print(f"  No {file_type} files found in {source_dir}")
        return {"copied": 0, "cleaned": 0, "warnings": 0, "unexpected_files": []}

    # Clean existing files matching pattern
    existing_files = list(target_dir.glob(pattern))
    cleaned_count = 0

    if existing_files:
        print(f"  Removed {len(existing_files)} existing {file_type} file(s)")
        for existing_file in existing_files:
            try:
                existing_file.unlink()
                cleaned_count += 1
            except Exception as e:
                print(f"    ✗ Failed to remove {existing_file.name}: {e}")

    # Copy new files
    print(f"  Copied {len(files)} {file_type} file(s) to {target_dir}")

    copied_count = 0
    synced_filenames = set()

    for file_path in files:
        try:
            target_path = target_dir / file_path.name
            shutil.copy2(file_path, target_path)
            print(f"    ✓ {file_path.name}")
            copied_count += 1
            synced_filenames.add(file_path.name)
        except Exception as e:
            print(f"    ✗ Failed to copy {file_path.name}: {e}")

    # Check for unexpected files (files that don't match our pattern but exist in target)
    unexpected_files = []
    if target_dir.exists():
        all_target_files = [f for f in target_dir.iterdir() if f.is_file()]
        for target_file in all_target_files:
            # Check if this file matches our pattern and was synced by us
            if (
                target_file.suffix == pattern.replace("*", "")
                and target_file.name not in synced_filenames
            ):
                unexpected_files.append(target_file.name)

    warnings_count = len(unexpected_files)
    if unexpected_files:
        print(
            f"  ⚠️  Found {warnings_count} unexpected {file_type} file(s): {', '.join(unexpected_files)}"
        )

    return {
        "copied": copied_count,
        "cleaned": cleaned_count,
        "warnings": warnings_count,
        "unexpected_files": unexpected_files,
    }


def main():
    """Main sync function."""
    try:
        # Load environment variables
        load_dotenv()

        print("=== Analysis Output Sync ===")

        # Get paper repository path
        paper_repo_path = get_paper_repo_path()
        print(f"Target repository: {paper_repo_path}")

        # Prepare target directories
        figures_target = paper_repo_path / "figures"
        tables_target = paper_repo_path / "tables"
        data_target = paper_repo_path / "data"

        total_stats = {"copied": 0, "cleaned": 0, "warnings": 0, "unexpected_files": []}

        # Sync figures (PDFs)
        print("\n📊 Syncing figures...")
        figures_dir = get_figures_output_dir()
        stats = sync_files(figures_dir, figures_target, "*.pdf", "figure")
        total_stats["copied"] += stats["copied"]
        total_stats["cleaned"] += stats["cleaned"]
        total_stats["warnings"] += stats["warnings"]
        total_stats["unexpected_files"].extend(stats["unexpected_files"])

        # Sync tables (LaTeX)
        print("\n📋 Syncing tables...")
        tables_dir = get_tables_output_dir()
        stats = sync_files(tables_dir, tables_target, "*.tex", "table")
        total_stats["copied"] += stats["copied"]
        total_stats["cleaned"] += stats["cleaned"]
        total_stats["warnings"] += stats["warnings"]
        total_stats["unexpected_files"].extend(stats["unexpected_files"])

        # Sync CSV data
        print("\n📈 Syncing CSV data...")
        data_dir = get_data_output_dir()
        stats = sync_files(data_dir, data_target, "*.csv", "CSV data")
        total_stats["copied"] += stats["copied"]
        total_stats["cleaned"] += stats["cleaned"]
        total_stats["warnings"] += stats["warnings"]
        total_stats["unexpected_files"].extend(stats["unexpected_files"])

        # Summary
        print("\n✅ Sync complete!")
        print(f"   Cleaned: {total_stats['cleaned']} old files")
        print(f"   Copied: {total_stats['copied']} new files")
        if total_stats["warnings"] > 0:
            print(f"   ⚠️  Warnings: {total_stats['warnings']} unexpected files found")
            if len(total_stats["unexpected_files"]) <= 10:  # Don't spam if too many
                print(
                    f"   Unexpected files: {', '.join(total_stats['unexpected_files'])}"
                )
        print(f"   Target repository: {paper_repo_path}")

        return 0

    except KeyboardInterrupt:
        print("\n❌ Sync cancelled by user")
        return 1

    except Exception as e:
        print(f"\n❌ Sync failed: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
