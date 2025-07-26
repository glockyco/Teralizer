#!/usr/bin/env python3
"""
Basic validation script for the analysis notebooks.
Verifies that essential imports work and database connections are available.
"""

import sys
import importlib
import os
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
    """Test database connectivity."""
    print("\nTesting database connections...")
    
    try:
        from sqlalchemy import create_engine, text
        
        # Test postgres_dev connection (for RQ1-RQ3)
        db_host = os.getenv("DB_HOST", "localhost")
        db_port = os.getenv("DB_PORT", "5432") 
        db_name = os.getenv("DB_NAME", "postgres")
        db_user = os.getenv("DB_USER", "postgres")
        db_password = os.getenv("DB_PASSWORD", "postgres")
        
        connection_string = f"postgresql://{db_user}:{db_password}@{db_host}:{db_port}/{db_name}"
        
        try:
            engine = create_engine(connection_string)
            with engine.connect() as conn:
                result = conn.execute(text("SELECT 1"))
                if result.scalar() == 1:
                    print(f"  ✓ postgres_dev connection successful")
                    return True
        except Exception as e:
            print(f"  ✗ postgres_dev connection failed: {e}")
            return False
            
    except Exception as e:
        print(f"  ✗ Database test setup failed: {e}")
        return False

def main():
    """Run all validation tests."""
    print("=== Analysis Directory Validation ===\n")
    
    tests = [
        ("Import Test", test_imports),
        ("Environment Test", test_environment), 
        ("Database Test", test_database_connection)
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