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
from pathlib import Path
from dotenv import load_dotenv

def test_imports():
    """Test that all required packages can be imported."""
    print("Testing imports...")
    required_packages = [
        'pandas',
        'matplotlib',
        'sqlalchemy',
        'psycopg2',
        'dotenv',
        'natsort'
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
        importlib.import_module('teralizer')
        print(f"  ✓ teralizer (editable install)")
    except ImportError as e:
        print(f"  ✗ teralizer (editable install): {e}")
        failed_imports.append('teralizer')
    
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
    
    required_vars = ['DB_HOST', 'DB_PORT', 'DB_NAME', 'DB_USER', 'DB_PASSWORD']
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
        from sqlalchemy import text
        
        # Test postgres_dev (eqbench and commons-utils projects)
        print("  Testing postgres_dev...")
        try:
            # Validate=True will check schema automatically
            engine = db_config.get_dev_engine(validate=True)
            print(f"  ✓ postgres_dev connection successful and schema valid")
        except ConnectionError as e:
            print(f"  ✗ postgres_dev connection failed: {e}")
            return False
        except RuntimeError as e:
            print(f"  ✗ postgres_dev schema validation failed:\n    {str(e).replace(chr(10), chr(10) + '    ')}")
            return False
        except Exception as e:
            print(f"  ✗ postgres_dev unexpected error: {e}")
            return False
        
        # Test postgres_test (repo-reapers projects)
        print("  Testing postgres_test...")
        try:
            engine = db_config.get_test_engine(validate=True)
            print(f"  ✓ postgres_test connection successful and schema valid")
        except ConnectionError as e:
            print(f"  ℹ postgres_test not available: Connection failed")
        except RuntimeError as e:
            print(f"  ℹ postgres_test schema incomplete:\n    {str(e).split(chr(10))[0]}...")
        except Exception as e:
            print(f"  ℹ postgres_test unavailable: {type(e).__name__}")
        
        return True
            
    except Exception as e:
        print(f"  ✗ Database test setup failed: {e}")
        return False

def test_notebook_execution():
    """Test that all notebooks can execute without errors."""
    print("\nTesting notebook execution...")
    
    # List of notebooks to test in priority order
    notebooks = [
        'teralizer-mutation-analysis.ipynb',
        'teralizer-runtime-analysis.ipynb',
        'test-runtime-analysis.ipynb',
        'teralizer-exclusion-analysis.ipynb',
        'dataset-analysis.ipynb',
        'evosuite-runtime-analysis.ipynb',
    ]
    
    notebooks_dir = Path("notebooks")
    if not notebooks_dir.exists():
        print(f"  ✗ Notebooks directory not found: {notebooks_dir}")
        return False
    
    failed_notebooks = []
    successful_notebooks = []
    
    for notebook in notebooks:
        notebook_path = notebooks_dir / notebook
        if not notebook_path.exists():
            print(f"  ℹ {notebook}: Not found (skipping)")
            continue
            
        print(f"  Testing {notebook}...")
        
        # Create temporary output file
        with tempfile.NamedTemporaryFile(suffix='.ipynb', delete=False) as temp_file:
            temp_output = temp_file.name
        
        try:
            # Execute notebook using nbconvert
            result = subprocess.run([
                'jupyter', 'nbconvert',
                '--execute',
                '--to', 'notebook',
                '--output', temp_output,
                '--ExecutePreprocessor.timeout=300',  # 5-minute timeout
                str(notebook_path)
            ], capture_output=True, text=True, cwd=notebooks_dir.parent)
            
            if result.returncode == 0:
                print(f"    ✓ {notebook}: Executed successfully")
                successful_notebooks.append(notebook)
            else:
                print(f"    ✗ {notebook}: Execution failed")
                # Show first few lines of error for debugging
                error_lines = result.stderr.split('\n')
                for line in error_lines[:3]:
                    if line.strip():
                        print(f"      {line}")
                if len(error_lines) > 3:
                    print("      ...")
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
    
    print(f"\n  Notebook Execution Summary:")
    print(f"    ✓ Successful: {len(successful_notebooks)}")
    print(f"    ✗ Failed: {len(failed_notebooks)}")
    
    if failed_notebooks:
        print(f"    Failed notebooks: {', '.join(failed_notebooks)}")
        return False
    
    return True

def main():
    """Run all validation tests."""
    print("=== Analysis Directory Validation ===\n")
    
    tests = [
        ("Import Test", test_imports),
        ("Environment Test", test_environment), 
        ("Database Test", test_database_connection),
        ("Notebook Execution Test", test_notebook_execution)
    ]
    
    all_passed = True
    
    for test_name, test_func in tests:
        try:
            if not test_func():
                all_passed = False
        except Exception as e:
            print(f"  ✗ {test_name} crashed: {e}")
            all_passed = False
    
    print(f"\n=== Validation {'PASSED' if all_passed else 'FAILED'} ===")
    return 0 if all_passed else 1

if __name__ == "__main__":
    sys.exit(main())