"""Focused extractors for compact, versioned report inputs."""

from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path


def write_atomic(path: Path, document: dict[str, object]) -> None:
    """Write a JSON evidence record without exposing a partial file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    content = json.dumps(document, indent=2, sort_keys=True) + "\n"
    descriptor, temporary_name = tempfile.mkstemp(
        prefix=f".{path.name}.", dir=path.parent, text=True
    )
    temporary = Path(temporary_name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
            handle.write(content)
        temporary.replace(path)
    except BaseException:
        temporary.unlink(missing_ok=True)
        raise
