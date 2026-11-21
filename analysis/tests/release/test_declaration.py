import tomllib

import pytest
from teralizer.release import declaration, inventory


def _document() -> dict[str, object]:
    with declaration.DEFAULT_DECLARATION.open("rb") as handle:
        return tomllib.load(handle)


def _workflows(document: dict[str, object]) -> list[dict[str, object]]:
    workflows = document["workflow"]
    assert isinstance(workflows, list)
    return workflows


def test_repository_declaration_is_valid():
    release = declaration.load()

    assert release.component("core").root == "teralizer"
    assert release.component("corpus-real-world").corpus == "real-world"


def test_workflow_cannot_combine_the_two_real_world_scopes():
    document = _document()
    _workflows(document)[0]["components"] = [
        "core",
        "projects-real-world-sample",
        "projects-real-world-full",
    ]

    with pytest.raises(declaration.DeclarationError, match="alternatives"):
        declaration.parse(document)


def test_every_workflow_needs_core():
    document = _document()
    _workflows(document)[0]["components"] = ["corpus-controlled"]

    with pytest.raises(declaration.DeclarationError, match="needs core"):
        declaration.parse(document)


def test_component_root_must_be_its_own_subtree():
    document = _document()
    components = document["component"]
    assert isinstance(components, list)
    components[1]["root"] = "teralizer/components/shared"

    with pytest.raises(declaration.DeclarationError, match="root must be"):
        declaration.parse(document)


def test_version_doi_must_not_reuse_an_earlier_version():
    document = _document()
    release = document["release"]
    assert isinstance(release, dict)
    release["version_doi"] = release["previous_version_dois"][0]

    with pytest.raises(declaration.DeclarationError, match="new version"):
        declaration.parse(document)


def test_production_requires_a_reserved_version_doi():
    document = _document()
    release = document["release"]
    assert isinstance(release, dict)
    release["version_doi"] = ""

    problems = declaration.production_problems(
        declaration.parse(document), inventory.load()
    )

    assert "version_doi is not reserved" in problems
