"""Inventory and verify registered evaluation corpora."""

from __future__ import annotations

import argparse
from collections.abc import Callable, Iterable, Sequence
from dataclasses import dataclass

from sqlalchemy import text

from teralizer import corpora
from teralizer.config import db_config
from teralizer.corpus_preparation import require_current_revision
from teralizer.report_basis import (
    open_report_connection,
    require_complete_corpus,
    resolve_repo_relative_path,
)

EntryVerifier = Callable[[corpora.CorpusEntry], int]


@dataclass(frozen=True)
class VerificationReport:
    """Result of inventorying a server and optionally verifying selected corpora."""

    classifications: tuple[corpora.DatabaseClassification, ...]
    missing: tuple[corpora.CorpusEntry, ...]
    verified: tuple[tuple[corpora.CorpusEntry, int], ...]
    errors: tuple[str, ...]

    @property
    def ok(self) -> bool:
        return not self.errors


def observed_databases() -> tuple[str, ...]:
    """Return every connectable, non-template database on the configured server."""
    engine = db_config.get_engine("postgres", validate=False)
    try:
        with engine.connect() as conn:
            rows = conn.execute(
                text(
                    "SELECT datname FROM pg_database "
                    "WHERE datallowconn AND NOT datistemplate ORDER BY datname"
                )
            )
            return tuple(str(row.datname) for row in rows)
    finally:
        engine.dispose()


def verify_entry(entry: corpora.CorpusEntry) -> int:
    """Verify one installed corpus against its registry entry and checked inputs."""
    with open_report_connection(entry.database) as conn:
        if entry.derived_views:
            require_current_revision(conn, entry.id)
        observed = corpora.validate_project_count(conn, entry)
        if entry.data_dir is not None and entry.config_dir is not None:
            require_complete_corpus(
                conn,
                data_dir=resolve_repo_relative_path(entry.data_dir),
                config_dir=resolve_repo_relative_path(entry.config_dir),
            )
        return observed


def verify(
    registry: corpora.CorpusRegistry,
    databases: Iterable[str],
    *,
    required: Sequence[str] = (),
    published: bool = False,
    entry_verifier: EntryVerifier = verify_entry,
) -> VerificationReport:
    """Inventory a server or fully verify an explicitly selected corpus set."""
    if required and published:
        raise ValueError(
            "required corpus ids and published mode are mutually exclusive"
        )

    observed = tuple(sorted(set(databases)))
    observed_set = frozenset(observed)
    classifications = registry.classify_all(observed)

    if not required and not published:
        missing = tuple(
            entry for entry in registry.entries if entry.database not in observed_set
        )
        return VerificationReport(classifications, missing, (), ())

    selected = (
        registry.published_entries
        if published
        else tuple(registry.get(corpus_id) for corpus_id in required)
    )
    missing: list[corpora.CorpusEntry] = []
    verified: list[tuple[corpora.CorpusEntry, int]] = []
    errors: list[str] = []
    for entry in selected:
        if entry.database not in observed_set:
            missing.append(entry)
            errors.append(f"corpus {entry.id!r} is missing database {entry.database!r}")
            continue
        try:
            verified.append((entry, entry_verifier(entry)))
        except Exception as error:
            errors.append(f"corpus {entry.id!r} failed verification: {error}")

    return VerificationReport(
        classifications,
        tuple(missing),
        tuple(verified),
        tuple(errors),
    )


def format_report(report: VerificationReport) -> str:
    """Render a stable human-readable verification report."""
    lines = ["Observed databases:"]
    if not report.classifications:
        lines.append("  (none)")
    for classification in report.classifications:
        corpus = f" ({classification.corpus_id})" if classification.corpus_id else ""
        lines.append(
            f"  {classification.database}: {classification.kind.value}{corpus}"
        )

    if report.missing:
        lines.append("Missing registered corpora:")
        lines.extend(f"  {entry.id}: {entry.database}" for entry in report.missing)
    if report.verified:
        lines.append("Verified corpora:")
        lines.extend(
            f"  {entry.id}: {observed} projects" for entry, observed in report.verified
        )
    if report.errors:
        lines.append("Errors:")
        lines.extend(f"  {error}" for error in report.errors)
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="verify-corpora")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument(
        "--require",
        nargs="+",
        metavar="CORPUS_ID",
        default=(),
        help="fully verify only the named installed corpora",
    )
    mode.add_argument(
        "--published",
        action="store_true",
        help="require and fully verify every published corpus",
    )
    args = parser.parse_args(argv)

    try:
        report = verify(
            corpora.load(),
            observed_databases(),
            required=args.require,
            published=args.published,
        )
    except (KeyError, RuntimeError, ValueError) as error:
        parser.exit(1, f"verify-corpora: {error}\n")

    print(format_report(report))
    if not report.ok:
        parser.exit(1)


if __name__ == "__main__":
    main()
