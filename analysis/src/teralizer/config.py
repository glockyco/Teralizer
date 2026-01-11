"""
Database configuration for teralizer analysis.

Provides centralized database connections for both postgres_dev and postgres_test
databases used for different project types:
- postgres_dev: Contains eqbench and commons-utils projects
- postgres_test: Contains repo-reapers projects
"""

import os
import re
from pathlib import Path
from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.exc import OperationalError, ProgrammingError


# Load environment variables from .env file in project root
# Find project root by looking for .env file
def find_project_root():
    current = Path(__file__).parent
    for _ in range(10):  # Prevent infinite loop
        env_file = current / ".env"
        if env_file.exists():
            return env_file
        if current == current.parent:
            break
        current = current.parent
    return ".env"  # Fallback to relative path


load_dotenv(find_project_root())


class DatabaseConfig:
    """Centralized database configuration for teralizer analysis."""

    # Valid dataset variants for replication workflow
    VALID_VARIANTS = ("original", "verify", "replicate")

    def __init__(self):
        self.host = os.getenv("DB_HOST", "localhost")
        self.port = os.getenv("DB_PORT", "5432")
        self.user = os.getenv("DB_USER", "teralizer")
        self.password = os.getenv("DB_PASSWORD", "teralizer")

        # Configurable database names for flexibility during reproduction
        self.db_name_dev = os.getenv("DB_NAME_DEV", "postgres_dev")
        self.db_name_test = os.getenv("DB_NAME_TEST", "postgres_test")

        # Dataset variant: "original", "verify", or "replicate"
        # - original/verify: use original databases
        # - replicate: use *_replication databases
        self.variant = os.getenv("DATASET_VARIANT", "original")
        if self.variant not in self.VALID_VARIANTS:
            raise ValueError(
                f"Invalid DATASET_VARIANT: {self.variant}. "
                f"Must be one of {self.VALID_VARIANTS}"
            )

        # Cache for parsed SQL files
        self._sql_objects_cache = None

    def _get_db_name(self, base_name):
        """Get database name with replication suffix if applicable.

        Args:
            base_name: Base database name (e.g., 'postgres_dev')

        Returns:
            Database name, with '_replication' suffix for replicate variant
        """
        if self.variant == "replicate":
            return f"{base_name}_replication"
        return base_name

    def get_engine(self, database="postgres_dev", validate=True):
        """
        Get SQLAlchemy engine for specified database.

        Args:
            database: Database name ('postgres_dev' or 'postgres_test')
            validate: Whether to validate connection and schema (default: True)

        Returns:
            sqlalchemy.engine.Engine: Database engine

        Raises:
            ConnectionError: If database connection fails
            RuntimeError: If required schema objects are missing
        """
        connection_string = f"postgresql://{self.user}:{self.password}@{self.host}:{self.port}/{database}"
        engine = create_engine(connection_string)

        if validate:
            self._validate_connection(engine, database)

        return engine

    def _validate_connection(self, engine, database):
        """Validate database connection and schema."""
        try:
            # Test basic connection
            with engine.connect() as conn:
                result = conn.execute(text("SELECT 1"))
                result.fetchone()

                # Check all required schema objects
                missing_objects, sql_files = self._check_schema_objects(conn)

                if missing_objects:
                    missing_str = "\n".join(
                        [
                            f"  - {obj_type}: {name}"
                            for obj_type, name in missing_objects
                        ]
                    )

                    # Determine which scripts need to be run based on what's missing
                    scripts_needed = []

                    # Check which SQL files contain the missing objects
                    for filename, file_objects in sql_files.items():
                        file_has_missing = False

                        for obj_type, obj_name in missing_objects:
                            obj_type_key = {
                                "TABLE": "tables",
                                "VIEW": "views",
                                "MATERIALIZED VIEW": "materialized_views",
                                "FUNCTION": "functions",
                            }.get(obj_type)

                            if obj_type_key and obj_name in file_objects[obj_type_key]:
                                file_has_missing = True
                                break

                        if file_has_missing:
                            warning = ""
                            if (
                                filename == "create-tables.sql"
                                and "tables" in file_objects
                                and file_objects["tables"]
                            ):
                                warning = " (WARNING: This will DROP all existing tables and data!)"
                            scripts_needed.append(
                                f"  - src/main/resources/db/{filename}{warning}"
                            )

                    scripts_message = "\n".join(scripts_needed)

                    raise RuntimeError(
                        f"Database '{database}' is missing required schema objects:\n{missing_str}\n\n"
                        f"Please run the following SQL script(s):\n{scripts_message}"
                    )

        except OperationalError as e:
            raise ConnectionError(
                f"Failed to connect to database '{database}' at {self.host}:{self.port}. "
                f"Please ensure PostgreSQL is running. Error: {str(e)}"
            )
        except ProgrammingError as e:
            raise RuntimeError(
                f"Database query error: {str(e)}. "
                f"The database may not be properly initialized."
            )

    def _parse_sql_files(self):
        """Parse SQL files to extract schema objects they create."""
        if self._sql_objects_cache is not None:
            return self._sql_objects_cache

        # Find the SQL files relative to this config file
        config_dir = Path(__file__).parent
        sql_dir = config_dir / ".." / ".." / ".." / "src" / "main" / "resources" / "db"
        sql_dir = sql_dir.resolve()

        sql_files = {
            "create-tables.sql": {
                "tables": set(),
                "views": set(),
                "materialized_views": set(),
                "functions": set(),
            },
            "create-views.sql": {
                "tables": set(),
                "views": set(),
                "materialized_views": set(),
                "functions": set(),
            },
        }

        # Regex patterns for different CREATE statements
        patterns = {
            "tables": re.compile(
                r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)", re.IGNORECASE
            ),
            "views": re.compile(
                r"CREATE\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)", re.IGNORECASE
            ),
            "materialized_views": re.compile(
                r"CREATE\s+MATERIALIZED\s+VIEW\s+(?:IF\s+NOT\s+EXISTS\s+)?(\w+)",
                re.IGNORECASE,
            ),
            "functions": re.compile(
                r"CREATE\s+(?:OR\s+REPLACE\s+)?FUNCTION\s+(\w+)", re.IGNORECASE
            ),
        }

        for filename, objects in sql_files.items():
            file_path = sql_dir / filename
            if file_path.exists():
                content = file_path.read_text()

                for obj_type, pattern in patterns.items():
                    for match in pattern.finditer(content):
                        obj_name = match.group(1).lower()
                        objects[obj_type].add(obj_name)

        self._sql_objects_cache = sql_files
        return sql_files

    def _check_schema_objects(self, conn):
        """Check for all required schema objects and return missing ones."""
        missing = []

        # Parse SQL files to get expected objects
        sql_files = self._parse_sql_files()

        # Combine objects from all SQL files
        expected_objects = {
            "tables": set(),
            "views": set(),
            "materialized_views": set(),
            "functions": set(),
        }

        for file_objects in sql_files.values():
            for obj_type in expected_objects:
                expected_objects[obj_type].update(file_objects[obj_type])

        # Check tables
        for table in expected_objects["tables"]:
            query = text("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_type = 'BASE TABLE'
                    AND table_name = :table_name
                )
            """)
            exists = conn.execute(query, {"table_name": table}).scalar()
            if not exists:
                missing.append(("TABLE", table))

        # Check views
        for view in expected_objects["views"]:
            query = text("""
                SELECT EXISTS (
                    SELECT 1 FROM information_schema.views 
                    WHERE table_schema = 'public' 
                    AND table_name = :view_name
                )
            """)
            exists = conn.execute(query, {"view_name": view}).scalar()
            if not exists:
                missing.append(("VIEW", view))

        # Check materialized views
        for mv in expected_objects["materialized_views"]:
            query = text("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_matviews 
                    WHERE schemaname = 'public' 
                    AND matviewname = :mv_name
                )
            """)
            exists = conn.execute(query, {"mv_name": mv}).scalar()
            if not exists:
                missing.append(("MATERIALIZED VIEW", mv))

        # Check functions
        for func in expected_objects["functions"]:
            query = text("""
                SELECT EXISTS (
                    SELECT 1 FROM pg_proc p
                    JOIN pg_namespace n ON p.pronamespace = n.oid
                    WHERE n.nspname = 'public' 
                    AND p.proname = :func_name
                )
            """)
            exists = conn.execute(query, {"func_name": func}).scalar()
            if not exists:
                missing.append(("FUNCTION", func))

        return missing, sql_files

    def get_dev_engine(self, validate=True):
        """Get engine for dev database (eqbench and commons-utils projects).

        Database name is configurable via DB_NAME_DEV environment variable,
        defaulting to 'postgres_dev'. For replicate variant, uses
        postgres_dev_replication.
        """
        db_name = self._get_db_name(self.db_name_dev)
        return self.get_engine(db_name, validate=validate)

    def get_test_engine(self, validate=True):
        """Get engine for test database (repo-reapers projects).

        Database name is configurable via DB_NAME_TEST environment variable,
        defaulting to 'postgres_test'. For replicate variant, uses
        postgres_test_replication.
        """
        db_name = self._get_db_name(self.db_name_test)
        return self.get_engine(db_name, validate=validate)


# Global instance for easy importing
db_config = DatabaseConfig()
