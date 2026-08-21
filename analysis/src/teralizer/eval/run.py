"""Functional construction, staging, validation, and promotion for report runs."""

from __future__ import annotations

import json
import os
import tempfile
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from pathlib import Path

from teralizer.eval import inputs, provenance, publish, registry
from teralizer.eval.artifacts import ArtifactSet, RenderedArtifact, RenderTarget
from teralizer.eval.model import BuiltReport
from teralizer.eval.render import csv as csv_renderer
from teralizer.eval.render import figures as figures_renderer
from teralizer.eval.render import latex as latex_renderer
from teralizer.eval.render import manifest as manifest_renderer
from teralizer.eval.render import markdown as markdown_renderer

Replace = Callable[[Path, Path], object]


@dataclass(frozen=True)
class RunPaths:
    analysis_root: Path
    reports_dir: Path
    build_dir: Path

    def __post_init__(self) -> None:
        root = self.analysis_root.resolve()
        for path in (self.reports_dir, self.build_dir):
            if not path.resolve().is_relative_to(root):
                raise ValueError(
                    f"generator output escapes analysis root {root}: {path}"
                )


@dataclass(frozen=True)
class RunResult:
    built_reports: tuple[BuiltReport, ...]
    artifacts: ArtifactSet
    delivered: tuple[Path, ...]


def build_reports(
    report_ids: tuple[str, ...], *, publishing: bool
) -> tuple[BuiltReport, ...]:
    """Build every selected report sequentially before rendering any output."""
    built_reports: list[BuiltReport] = []
    for report_id in report_ids:
        if publishing:
            provenance.require_publishable_tree()
        spec = registry.get(report_id)
        with inputs.resolve_inputs(report_id, spec.inputs) as context:
            report = spec.build(context)
            built = BuiltReport(report, context.snapshots)
        if report.rq != report_id:
            raise ValueError(
                f"registered report {report_id!r} built result {report.rq!r}"
            )
        report.metric_map()
        if publishing:
            provenance.require_publishable_inputs(built.inputs)
        built_reports.append(built)
    return tuple(built_reports)


def _render_reports(
    built_reports: tuple[BuiltReport, ...],
    targets: frozenset[RenderTarget],
    stage_root: Path,
    paths: RunPaths,
    *,
    repo_url: str,
    base_manifest: dict[str, object] | None,
) -> ArtifactSet:
    reports_dir = stage_root / paths.reports_dir.resolve().relative_to(
        paths.analysis_root.resolve()
    )
    build_dir = stage_root / paths.build_dir.resolve().relative_to(
        paths.analysis_root.resolve()
    )
    artifacts = ArtifactSet(stage_root)
    for built in built_reports:
        report = built.report
        if RenderTarget.FIGURES in targets:
            artifacts.merge(
                figures_renderer.materialize(
                    report,
                    reports_dir / "figures" / report.rq,
                    build_dir / "figures" / report.rq,
                    staging_root=stage_root,
                )
            )
        if RenderTarget.MARKDOWN in targets:
            artifacts.merge(
                markdown_renderer.render(
                    built,
                    reports_dir,
                    staging_root=stage_root,
                    repo_url=repo_url,
                )
            )
        if RenderTarget.LATEX in targets:
            artifacts.merge(
                latex_renderer.render(built, build_dir, staging_root=stage_root)
            )
        if RenderTarget.CSV in targets:
            artifacts.merge(
                csv_renderer.render(
                    report,
                    build_dir / report.rq,
                    staging_root=stage_root,
                )
            )
    if RenderTarget.LATEX in targets:
        artifacts.merge(
            latex_renderer.render_aggregate(build_dir, staging_root=stage_root)
        )
    artifacts.merge(
        manifest_renderer.render(
            built_reports,
            artifacts,
            reports_dir,
            staging_root=stage_root,
            repo_url=repo_url,
            base_document=base_manifest,
        )
    )
    return artifacts


def _load_manifest(path: Path) -> dict[str, object]:
    if not path.is_file():
        return {}
    document = json.loads(path.read_text())
    if not isinstance(document, dict):
        raise ValueError(f"provenance manifest must be an object: {path}")
    return document


def _artifact_records(entry: object) -> tuple[dict[str, str], ...]:
    if not isinstance(entry, dict):
        return ()
    records = entry.get("artifacts", [])
    if not isinstance(records, list):
        return ()
    out: list[dict[str, str]] = []
    for record in records:
        if not isinstance(record, dict):
            continue
        if all(
            isinstance(record.get(field), str) for field in ("target", "key", "path")
        ):
            out.append(record)
    return tuple(out)


def _partial_manifest(
    existing: dict[str, object],
    report_ids: tuple[str, ...],
    targets: frozenset[RenderTarget],
) -> dict[str, object]:
    selected = set(report_ids)
    stale = sorted(set(existing) - selected - set(registry.REPORTS))
    if stale:
        raise ValueError(
            "partial run cannot preserve unregistered manifest reports: "
            + ", ".join(stale)
            + ". Run the complete report set"
        )
    return dict(existing)


def _stale_paths(
    existing: dict[str, object],
    current: ArtifactSet,
    report_ids: tuple[str, ...],
    targets: frozenset[RenderTarget],
    *,
    full: bool,
    final_root: Path,
) -> tuple[Path, ...]:
    current_paths = {artifact.path.relative_to(current.root) for artifact in current}
    selected = set(report_ids)
    target_names = {target.value for target in targets} | {RenderTarget.MANIFEST.value}
    stale: set[Path] = set()
    for owner, entry in existing.items():
        owner_removed = full and owner not in selected
        if not owner_removed and owner not in selected:
            continue
        for record in _artifact_records(entry):
            if not owner_removed and record["target"] not in target_names:
                continue
            relative = Path(record["path"])
            final = (final_root / relative).resolve()
            if not final.is_relative_to(final_root.resolve()):
                raise ValueError(
                    f"manifest artifact path escapes generator root: {relative}"
                )
            if relative not in current_paths:
                stale.add(final)
    return tuple(sorted(stale))


def _validate_artifacts(
    artifacts: ArtifactSet,
    built_reports: tuple[BuiltReport, ...],
    declaration: publish.FigureDeclaration | None,
) -> None:
    owners = {built.report.rq for built in built_reports}
    for artifact in artifacts:
        if (
            isinstance(artifact.owner, str)
            and artifact.owner not in owners
            and artifact.owner != "run"
        ):
            raise ValueError(
                f"artifact {artifact.id.target.value}/{artifact.id.key} has "
                f"unselected owner {artifact.owner!r}"
            )
        if not artifact.path.is_file():
            raise ValueError(
                f"rendered artifact disappeared before promotion: {artifact.path}"
            )
    if declaration is not None:
        publish.validate(declaration, artifacts)


def promote(
    artifacts: ArtifactSet,
    final_root: Path,
    stale_paths: Iterable[Path] = (),
    *,
    replace: Replace = os.replace,
) -> ArtifactSet:
    """Atomically replace generator files and roll every replacement back on error."""
    final_root = final_root.resolve()
    backup_root = artifacts.root / ".promotion-backup"
    backup_root.mkdir(parents=True, exist_ok=True)
    backups: list[tuple[Path, Path]] = []
    installed: list[Path] = []
    created_dirs: list[Path] = []

    def ensure_parent(path: Path) -> None:
        missing: list[Path] = []
        parent = path.parent
        while parent != final_root and not parent.exists():
            missing.append(parent)
            parent = parent.parent
        for directory in reversed(missing):
            directory.mkdir()
            created_dirs.append(directory)

    staged = sorted(
        artifacts, key=lambda artifact: str(artifact.path.relative_to(artifacts.root))
    )
    destinations: dict[Path, RenderedArtifact] = {}
    for artifact in staged:
        relative = artifact.path.relative_to(artifacts.root)
        destination = (final_root / relative).resolve()
        if not destination.is_relative_to(final_root):
            raise ValueError(f"artifact promotion escapes generator root: {relative}")
        if destination in destinations:
            raise ValueError(f"two artifacts promote to {destination}")
        destinations[destination] = artifact
    stale = sorted(set(path.resolve() for path in stale_paths) - set(destinations))
    if any(not path.is_relative_to(final_root) for path in stale):
        raise ValueError("stale generator path escapes the output root")

    try:
        for index, (destination, artifact) in enumerate(destinations.items()):
            ensure_parent(destination)
            if destination.exists():
                backup = backup_root / f"{index:06d}"
                replace(destination, backup)
                backups.append((destination, backup))
            replace(artifact.path, destination)
            installed.append(destination)
        offset = len(destinations)
        for index, destination in enumerate(stale, start=offset):
            if destination.exists():
                backup = backup_root / f"{index:06d}"
                replace(destination, backup)
                backups.append((destination, backup))
    except Exception:
        for destination in reversed(installed):
            if destination.exists():
                destination.unlink()
        for destination, backup in reversed(backups):
            if backup.exists():
                replace(backup, destination)
        for directory in reversed(created_dirs):
            try:
                directory.rmdir()
            except OSError:
                pass
        raise

    promoted = ArtifactSet(final_root)
    for destination, artifact in destinations.items():
        promoted.add(RenderedArtifact(artifact.id, destination, artifact.owner))
    return promoted


def execute(
    report_ids: tuple[str, ...],
    targets: frozenset[RenderTarget],
    paths: RunPaths,
    *,
    repo_url: str,
    full: bool,
    declaration: publish.FigureDeclaration | None = None,
    publishing: bool = False,
    replace: Replace = os.replace,
) -> RunResult:
    """Execute one coherent report run and optionally deliver declared figures."""
    if full and set(report_ids) != set(registry.REPORTS):
        raise ValueError("a full run must select every registered report")
    # Capture source state before generated outputs can make the checkout appear dirty.
    provenance.checkout_snapshot()
    built_reports = build_reports(report_ids, publishing=publishing)
    final_root = paths.analysis_root.resolve()
    manifest_path = paths.reports_dir / "provenance.json"
    existing = _load_manifest(manifest_path)
    base_manifest = None if full else _partial_manifest(existing, report_ids, targets)
    with tempfile.TemporaryDirectory(
        prefix=".report-stage-", dir=final_root
    ) as stage_name:
        stage_root = Path(stage_name).resolve()
        artifacts = _render_reports(
            built_reports,
            targets,
            stage_root,
            paths,
            repo_url=repo_url,
            base_manifest=base_manifest,
        )
        _validate_artifacts(artifacts, built_reports, declaration)
        stale = _stale_paths(
            existing,
            artifacts,
            report_ids,
            targets,
            full=full,
            final_root=final_root,
        )
        promoted = promote(artifacts, final_root, stale, replace=replace)
    delivered = (
        tuple(publish.deliver(declaration, promoted)) if declaration is not None else ()
    )
    return RunResult(built_reports, promoted, delivered)
