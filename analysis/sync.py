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
from teralizer.exports import get_figures_output_dir, get_tables_output_dir, get_data_output_dir


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
        env_path = os.getenv('PAPER_REPO_PATH')
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


def sync_files(source_dir: Path, target_dir: Path, pattern: str, file_type: str) -> int:
    """Sync files matching pattern from source to target directory.
    
    Args:
        source_dir: Source directory to copy from
        target_dir: Target directory to copy to
        pattern: Glob pattern to match files (e.g., '*.pdf')
        file_type: Human-readable description of file type
        
    Returns:
        Number of files copied
    """
    # Create target directory if it doesn't exist
    target_dir.mkdir(parents=True, exist_ok=True)
    
    # Find files to sync
    files = list(source_dir.glob(pattern))
    
    if not files:
        print(f"  No {file_type} files found in {source_dir}")
        return 0
    
    print(f"  Syncing {len(files)} {file_type} file(s) to {target_dir}")
    
    copied_count = 0
    for file_path in files:
        try:
            target_path = target_dir / file_path.name
            shutil.copy2(file_path, target_path)
            print(f"    ✓ {file_path.name}")
            copied_count += 1
        except Exception as e:
            print(f"    ✗ Failed to copy {file_path.name}: {e}")
    
    return copied_count


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
        figures_target = paper_repo_path / 'figures'
        tables_target = paper_repo_path / 'tables'
        data_target = paper_repo_path / 'data'
        
        total_copied = 0
        
        # Sync figures (PDFs)
        print("\n📊 Syncing figures...")
        figures_dir = get_figures_output_dir()
        copied = sync_files(figures_dir, figures_target, '*.pdf', 'figure')
        total_copied += copied
        
        # Sync tables (LaTeX)
        print("\n📋 Syncing tables...")
        tables_dir = get_tables_output_dir()
        copied = sync_files(tables_dir, tables_target, '*.tex', 'table')
        total_copied += copied
        
        # Sync CSV data
        print("\n📈 Syncing CSV data...")
        data_dir = get_data_output_dir()
        copied = sync_files(data_dir, data_target, '*.csv', 'CSV data')
        total_copied += copied
        
        # Summary
        print("\n✅ Sync complete!")
        print(f"   Total files copied: {total_copied}")
        print(f"   Target repository: {paper_repo_path}")
        
        return 0
        
    except KeyboardInterrupt:
        print("\n❌ Sync cancelled by user")
        return 1
        
    except Exception as e:
        print(f"\n❌ Sync failed: {e}")
        return 1


if __name__ == '__main__':
    sys.exit(main())