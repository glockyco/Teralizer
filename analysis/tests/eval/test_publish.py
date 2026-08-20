import subprocess
from pathlib import Path

import pytest

from teralizer.eval.artifacts import (
    ArtifactId,
    ArtifactSet,
    RenderedArtifact,
    RenderTarget,
)
from teralizer.eval.publish import (
    DECLARATION_NAME,
    FigureDeclaration,
    PublishError,
    deliver,
    read_declaration,
)


def _consumer(tmp_path: Path, declaration: str | None = None) -> Path:
    """A git repository standing in for a consuming repository, with the publish
    destination one level down, as the thesis has it."""
    root = tmp_path / "consumer"
    destination = root / "chapters" / "05-teralizer"
    destination.mkdir(parents=True)
    subprocess.run(["git", "init", "-q", str(root)], check=True)
    if declaration is not None:
        (destination / DECLARATION_NAME).write_text(declaration)
    return destination


def _declaration(destination: Path) -> FigureDeclaration:
    """Read a declaration the caller expects to exist."""
    declaration = read_declaration(destination)
    assert declaration is not None
    return declaration


def _emitted(tmp_path: Path, **keys: str) -> ArtifactSet:
    build = tmp_path / "build"
    build.mkdir(exist_ok=True)
    artifacts = ArtifactSet(tmp_path)
    for key, content in keys.items():
        artifacts.add(
            RenderedArtifact(
                ArtifactId(RenderTarget.FIGURES, key),
                _written(build / f"{key}.pdf", content),
                "rq",
            )
        )
    return artifacts


def _written(path: Path, content: str) -> Path:
    path.write_text(content)
    return path


def _reasons(caught: pytest.ExceptionInfo[BaseException]) -> list[str]:
    error = caught.value
    assert isinstance(error, PublishError)
    return error.reasons


def _commit_all(root: Path) -> None:
    subprocess.run(["git", "-C", str(root), "add", "-A"], check=True)
    subprocess.run(
        ["git", "-C", str(root)]
        + ["-c", "user.email=t@t", "-c", "user.name=t"]
        + ["commit", "-qm", "seed"],
        check=True,
    )


def test_absent_declaration_declares_no_figures(tmp_path: Path):
    assert read_declaration(_consumer(tmp_path)) is None


def test_empty_figures_table_declares_no_figures(tmp_path: Path):
    assert read_declaration(_consumer(tmp_path, "[figures]\n")) is None


def test_declaration_resolves_against_the_consumer_root(tmp_path: Path):
    destination = _consumer(
        tmp_path,
        '[figures]\nteralizer_efficiency = "figures/teralizer-efficiency.pdf"\n',
    )
    declaration = _declaration(destination)
    assert declaration.targets["teralizer_efficiency"] == (
        declaration.root / "figures" / "teralizer-efficiency.pdf"
    )


def test_paths_are_root_relative_not_file_relative(tmp_path: Path):
    """The thesis keeps figures above its publish destination. A root-relative
    path must not be read as relative to the declaration file."""
    declaration = _declaration(_consumer(tmp_path, '[figures]\nk = "figures/k.pdf"\n'))
    assert "chapters" not in str(declaration.targets["k"])


def test_malformed_toml_fails(tmp_path: Path):
    with pytest.raises(PublishError, match="not readable as TOML"):
        read_declaration(_consumer(tmp_path, "[figures\n"))


def test_non_string_path_fails(tmp_path: Path):
    with pytest.raises(PublishError, match="must map to a path string"):
        read_declaration(_consumer(tmp_path, "[figures]\nk = 3\n"))


def test_unresolvable_consumer_root_fails(tmp_path: Path):
    outside = tmp_path / "not-a-repo"
    outside.mkdir()
    (outside / DECLARATION_NAME).write_text('[figures]\nk = "f/k.pdf"\n')
    with pytest.raises(PublishError, match="cannot resolve the consuming repository"):
        read_declaration(outside)


def test_escaping_path_fails_at_construction(tmp_path: Path):
    """Containment is static, so it must fail before a report run, not after."""
    with pytest.raises(PublishError, match="escapes"):
        read_declaration(_consumer(tmp_path, '[figures]\nk = "../../outside.pdf"\n'))


def test_escaping_path_cannot_be_constructed_directly(tmp_path: Path):
    with pytest.raises(PublishError, match="escapes"):
        FigureDeclaration(root=tmp_path / "root", targets={"k": tmp_path / "other.pdf"})


def test_deliver_copies_every_declared_figure(tmp_path: Path):
    destination = _consumer(
        tmp_path,
        '[figures]\na = "figures/a.pdf"\nb = "deeper/nested/b.pdf"\n',
    )
    declaration = _declaration(destination)
    written = deliver(declaration, _emitted(tmp_path, a="A", b="B"))
    assert [p.read_text() for p in written] == ["A", "B"]
    assert (declaration.root / "deeper" / "nested" / "b.pdf").is_file()


def test_undeclared_figure_is_not_an_error_and_is_not_delivered(tmp_path: Path):
    """A figure with no consumer is normal: evosuite_runtime_phases is printed by
    neither the thesis nor the paper."""
    declaration = _declaration(_consumer(tmp_path, '[figures]\na = "figures/a.pdf"\n'))
    written = deliver(
        declaration, _emitted(tmp_path, a="A", evosuite_runtime_phases="E")
    )
    assert written == [declaration.root / "figures" / "a.pdf"]
    assert not (declaration.root / "figures" / "evosuite_runtime_phases.pdf").exists()


def test_missing_key_reports_every_failure(tmp_path: Path):
    destination = _consumer(
        tmp_path,
        '[figures]\ngone = "f/g.pdf"\nalso_gone = "f/a.pdf"\nhere = "f/h.pdf"\n',
    )
    declaration = _declaration(destination)
    with pytest.raises(PublishError) as caught:
        deliver(declaration, _emitted(tmp_path, here="H"))
    reasons = _reasons(caught)
    assert len(reasons) == 2
    assert {"gone", "also_gone"} == {r.split("'")[1] for r in reasons}


def test_nothing_is_copied_when_a_check_fails(tmp_path: Path):
    destination = _consumer(tmp_path, '[figures]\nhere = "f/h.pdf"\ngone = "f/g.pdf"\n')
    declaration = _declaration(destination)
    with pytest.raises(PublishError):
        deliver(declaration, _emitted(tmp_path, here="H"))
    assert not (declaration.root / "f").exists()


def test_uncommitted_consumer_figure_refuses_the_publish(tmp_path: Path):
    """A published figure is generated, so a local edit means the consumer wants
    something the generator does not produce."""
    declaration = _declaration(_consumer(tmp_path, '[figures]\na = "figures/a.pdf"\n'))
    target = declaration.root / "figures" / "a.pdf"
    target.parent.mkdir(parents=True)
    target.write_text("hand edited")
    with pytest.raises(PublishError, match="uncommitted change"):
        deliver(declaration, _emitted(tmp_path, a="A"))
    assert target.read_text() == "hand edited"


def test_committed_consumer_figure_is_overwritten(tmp_path: Path):
    declaration = _declaration(_consumer(tmp_path, '[figures]\na = "figures/a.pdf"\n'))
    target = declaration.root / "figures" / "a.pdf"
    target.parent.mkdir(parents=True)
    target.write_text("previous")
    _commit_all(declaration.root)
    deliver(declaration, _emitted(tmp_path, a="A"))
    assert target.read_text() == "A"
