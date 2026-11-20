"""Release maintenance commands.

uv run --directory analysis python -m teralizer.release inventory [--repo PATH]
uv run --directory analysis python -m teralizer.release report-inputs [--json PATH]
uv run --directory analysis python -m teralizer.release lineage [--staged DIR]
uv run --directory analysis python -m teralizer.release source --out DIR
uv run --directory analysis python -m teralizer.release jarvis-logs --staged DIR [--copy]
uv run --directory analysis python -m teralizer.release declaration [--production]
uv run --directory analysis python -m teralizer.release build --candidate DIR --evidence DIR --corpus-package DIR [--component ID ...] [--production]
uv run --directory analysis python -m teralizer.release preflight WORKFLOW --workspace DIR [--release-manifest FILE]
uv run --directory analysis python -m teralizer.release reference-outputs --analysis DIR --out FILE
uv run --directory analysis python -m teralizer.release verify-outputs --analysis DIR --reference FILE [--json FILE]
"""

from __future__ import annotations

import argparse
import dataclasses
import json
import sys
from pathlib import Path

from teralizer.release import inventory

_REPO_ROOT = Path(__file__).resolve().parents[4]


def _report_problems(problems: list[str], subject: str) -> int:
    for problem in problems:
        print(problem, file=sys.stderr)
    print(f"{len(problems)} {subject} problems", file=sys.stderr)
    return 1


def _inventory(declared: inventory.Inventory, repo: Path) -> int:
    problems = inventory.check(declared, repo)
    if problems:
        return _report_problems(problems, "inventory")
    print(
        f"inventory complete: {len(declared.inputs)} inputs, "
        f"{len(declared.ambient)} ambient rules"
    )
    return 0


def _report_inputs(
    declared: inventory.Inventory, repo: Path, output: Path | None
) -> int:
    import teralizer.eval.reports  # noqa: F401  (registers every report)
    from teralizer.eval.registry import REPORTS
    from teralizer.release import report_inputs

    resolved, problems = report_inputs.resolve(declared, REPORTS, repo)
    if problems:
        return _report_problems(problems, "report-input")
    for item in resolved:
        checksum = item.sha256 or "-"
        print(
            f"{item.report}\t{item.role}\t{item.kind}\t{item.source}\t{item.owner}\t{checksum}\t{item.status}"
        )
    if output is not None:
        output.write_text(
            json.dumps([dataclasses.asdict(item) for item in resolved], indent=2) + "\n"
        )
    return 0


def _lineage(
    declared: inventory.Inventory,
    repo: Path,
    staged: Path | None,
    corpus_package: Path | None,
    with_corpus: bool,
) -> int:
    from teralizer.release import lineage

    sources = lineage.load_sources(repo)
    problems = lineage.closure(declared, sources)
    if staged is not None:
        dump_sha256 = None
        if corpus_package is not None:
            manifest = json.loads((corpus_package / "manifest.json").read_text())
            dump_sha256 = next(
                entry["dump"]["sha256"]
                for entry in manifest["corpora"]
                if entry["corpus_id"] == "real-world"
            )
        problems += lineage.verify_staged(declared, sources, staged, dump_sha256)
        if with_corpus:
            from sqlalchemy import text

            from teralizer.corpora import open_corpus

            revisions = declared.input("reporeapers-checkout-revisions")
            assert revisions.release_path is not None
            rows = lineage.read_checkout_map(staged / revisions.release_path)
            with open_corpus("real-world") as (_, connection):
                corpus_rows = [
                    (str(root), version)
                    for root, version in connection.execute(
                        text("SELECT root_path, git_version FROM project")
                    )
                ]
            problems += lineage.corpus_revision_mismatches(rows, corpus_rows)
    if problems:
        return _report_problems(problems, "lineage")
    scope = "closure and staged evidence" if staged is not None else "closure"
    print(f"RepoReapers lineage complete ({scope}): {len(sources)} collected sources")
    return 0


def _source(declared: inventory.Inventory, repo: Path, revision: str, out: Path) -> int:
    from teralizer.release import source

    try:
        nested, lfs = source.materialize(repo, revision, out)
    except source.SourceError as error:
        return _report_problems([str(error)], "source")
    declared_paths = {
        item.path for item in declared.inputs if item.location == "submodule"
    }
    undeclared = [item.path for item in nested if item.path not in declared_paths]
    if undeclared:
        return _report_problems(
            [f"nested source {path} has no submodule input" for path in undeclared],
            "source",
        )
    for item in nested:
        print(f"nested\t{item.path}\t{item.commit}")
    for path in lfs:
        print(f"lfs\t{path}")
    print(f"source materialized at {out}")
    return 0


def _jarvis_logs(repo: Path, staged: Path, copy: bool) -> int:
    from contextlib import ExitStack

    from teralizer import corpora
    from teralizer.release import jarvis

    with ExitStack() as stack:
        _, scenarios = stack.enter_context(corpora.open_corpus("jarvis-scenarios"))
        _, benchmark = stack.enter_context(corpora.open_corpus("jarvis-benchmark"))
        selection = jarvis.selected_value_logs(scenarios, benchmark, repo)
    if copy:
        jarvis.stage(selection, repo, staged)
    problems = jarvis.verify(selection, staged, repo / jarvis.FACTS)
    if problems:
        return _report_problems(problems, "JARVIS value-log")
    counts = ", ".join(f"{len(paths)} {key}" for key, paths in selection.items())
    print(f"JARVIS value logs match the facts: {counts}")
    return 0


def _declaration(
    declared: inventory.Inventory, path: Path | None, production: bool
) -> int:
    from teralizer.release import declaration

    try:
        release = declaration.load(path or declaration.DEFAULT_DECLARATION)
    except declaration.DeclarationError as error:
        return _report_problems([str(error)], "declaration")
    if production:
        problems = declaration.production_problems(release, declared)
        if problems:
            return _report_problems(problems, "production")
    print(
        f"declaration valid: {len(release.components)} components, "
        f"{len(release.workflows)} workflows, {len(release.platforms)} platforms"
    )
    return 0


def _build(
    declared: inventory.Inventory,
    repo: Path,
    candidate: Path,
    evidence: Path,
    corpus_package: Path,
    components: list[str] | None,
    production: bool,
) -> int:
    from teralizer.release import build, declaration

    release = declaration.load()
    if production:
        problems = declaration.production_problems(release, declared)
        if components:
            problems.append("a production candidate contains every component")
        if problems:
            return _report_problems(problems, "production")
    selected = components or [component.id for component in release.components]
    try:
        built = build.build_candidate(
            release,
            declared,
            repo=repo,
            evidence=evidence,
            corpus_package=corpus_package,
            candidate=candidate,
            components=selected,
        )
    except build.BuildError as error:
        return _report_problems([str(error)], "build")
    for item in built:
        print(f"{item.path.name}\t{item.bytes}\t{item.unpacked_bytes}\t{item.sha256}")
    print(f"candidate written to {candidate}")
    return 0


def _preflight(workflow: str, workspace: Path, release_manifest: Path | None) -> int:
    from teralizer.release import preflight

    result = preflight.run(
        workspace, release_manifest or workspace / "release-manifest.json", workflow
    )
    if not result.passed:
        return _report_problems(list(result.problems), "preflight")
    print(f"preflight passed for {workflow}: {', '.join(result.components)}")
    return 0


def _reference_outputs(analysis: Path, out: Path) -> int:
    from teralizer.release import outputs

    document = outputs.reference(analysis)
    out.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n")
    print(f"reference of {len(document['artifacts'])} artifacts written to {out}")
    return 0


def _verify_outputs(analysis: Path, reference: Path, output: Path | None) -> int:
    from teralizer.release import outputs

    summary = outputs.summary(
        outputs.compare(json.loads(reference.read_text()), analysis)
    )
    if output is not None:
        output.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n")
    for target, counts in summary["targets"].items():
        print(
            f"{target}: "
            + ", ".join(f"{n} {status}" for status, n in sorted(counts.items()))
        )
    for failure in summary["failures"]:
        print(f"{failure['status']}: {failure['artifact']}", file=sys.stderr)
    print("all outputs match the reference" if summary["passed"] else "outputs differ")
    return 0 if summary["passed"] else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m teralizer.release")
    parser.add_argument("--repo", type=Path, default=_REPO_ROOT)
    parser.add_argument("--inventory", type=Path, default=inventory.DEFAULT_INVENTORY)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser(
        "inventory", help="check the release input inventory against a checkout"
    )
    closure = commands.add_parser(
        "report-inputs",
        help="resolve every registered report input to its component and status",
    )
    closure.add_argument("--json", type=Path, dest="output")
    lineage_command = commands.add_parser(
        "lineage",
        help="account for every RepoReapers reconstruction source and verify staged evidence",
    )
    lineage_command.add_argument("--staged", type=Path)
    lineage_command.add_argument("--corpus-package", type=Path)
    lineage_command.add_argument(
        "--with-corpus",
        action="store_true",
        help="also compare the staged checkout map with the real-world corpus",
    )
    materialize = commands.add_parser(
        "source",
        help="export the committed tree with nested sources and LFS objects",
    )
    materialize.add_argument("--revision", default="HEAD")
    materialize.add_argument("--out", type=Path, required=True)
    logs = commands.add_parser(
        "jarvis-logs",
        help="verify, and with --copy stage, the value logs that the JARVIS facts bind",
    )
    logs.add_argument("--staged", type=Path, required=True)
    logs.add_argument("--copy", action="store_true")
    declaration_command = commands.add_parser(
        "declaration", help="validate the release declaration against the inventory"
    )
    declaration_command.add_argument("--declaration", type=Path)
    declaration_command.add_argument(
        "--production",
        action="store_true",
        help="also require the completeness rules of a publishable candidate",
    )
    build_command = commands.add_parser(
        "build", help="build a release candidate into a fresh directory"
    )
    build_command.add_argument("--candidate", type=Path, required=True)
    build_command.add_argument("--evidence", type=Path, required=True)
    build_command.add_argument("--corpus-package", type=Path, required=True)
    build_command.add_argument(
        "--component",
        action="append",
        dest="components",
        help="build only these components, for development",
    )
    build_command.add_argument("--production", action="store_true")
    preflight_command = commands.add_parser(
        "preflight", help="verify the extracted components that a workflow needs"
    )
    preflight_command.add_argument("workflow")
    preflight_command.add_argument("--workspace", type=Path, required=True)
    preflight_command.add_argument("--release-manifest", type=Path)
    reference_command = commands.add_parser(
        "reference-outputs", help="record the reference manifest of a report run"
    )
    reference_command.add_argument("--analysis", type=Path, required=True)
    reference_command.add_argument("--out", type=Path, required=True)
    verify_command = commands.add_parser(
        "verify-outputs", help="compare a report run with the reference manifest"
    )
    verify_command.add_argument("--analysis", type=Path, required=True)
    verify_command.add_argument("--reference", type=Path, required=True)
    verify_command.add_argument("--json", type=Path, dest="summary")
    args = parser.parse_args(argv)

    # Reviewer workspaces have no inventory to load, only release documents.
    if args.command == "preflight":
        return _preflight(args.workflow, args.workspace, args.release_manifest)
    if args.command == "reference-outputs":
        return _reference_outputs(args.analysis, args.out)
    if args.command == "verify-outputs":
        return _verify_outputs(args.analysis, args.reference, args.summary)
    try:
        declared = inventory.load(args.inventory)
    except inventory.InventoryError as error:
        print(f"invalid inventory: {error}", file=sys.stderr)
        return 2
    if args.command == "inventory":
        return _inventory(declared, args.repo)
    if args.command == "lineage":
        return _lineage(
            declared, args.repo, args.staged, args.corpus_package, args.with_corpus
        )
    if args.command == "source":
        return _source(declared, args.repo, args.revision, args.out)
    if args.command == "jarvis-logs":
        return _jarvis_logs(args.repo, args.staged, args.copy)
    if args.command == "declaration":
        return _declaration(declared, args.declaration, args.production)
    if args.command == "build":
        return _build(
            declared,
            args.repo,
            args.candidate,
            args.evidence,
            args.corpus_package,
            args.components,
            args.production,
        )
    return _report_inputs(declared, args.repo, args.output)


if __name__ == "__main__":
    sys.exit(main())
