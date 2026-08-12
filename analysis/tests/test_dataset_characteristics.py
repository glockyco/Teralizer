"""Names in the dataset report are anchored to the projects root.

A source directory is identified by where it sits under `projects/`, never by where the repository
itself was cloned. A checkout under a directory named `src` used to make every project report the
name of the checkout's parent instead of its own.
"""

import pytest

from teralizer.dataset_characteristics import _get_project_name, _get_source_type


def source_dir(tmp_path, *checkout_parts):
    """Build `<tmp>/<checkout_parts>/projects/commons-utils/src/main/java` and return root and dir."""
    root = tmp_path.joinpath(*checkout_parts, "projects")
    directory = root / "commons-utils" / "src" / "main" / "java"
    directory.mkdir(parents=True)
    return root, directory


@pytest.mark.parametrize(
    "checkout",
    [
        ("Projects", "test-generalization"),
        ("src", "github.com", "owner", "test-generalization"),
        ("src", "src", "main", "test-generalization"),
    ],
)
def test_project_name_does_not_depend_on_the_checkout_path(tmp_path, checkout):
    root, directory = source_dir(tmp_path, *checkout)
    assert _get_project_name(str(directory), root) == "commons-utils"


def test_source_type_reads_the_project_layout(tmp_path):
    root = tmp_path / "projects"
    main = root / "eqbench-es-default-1s" / "src" / "main" / "java"
    test = root / "eqbench-es-default-1s" / "src" / "test" / "java"
    main.mkdir(parents=True)
    test.mkdir(parents=True)
    assert _get_source_type(str(main), root) == "main"
    assert _get_source_type(str(test), root) == "test"


def test_source_type_ignores_a_checkout_named_main(tmp_path):
    root = tmp_path / "main" / "test-generalization" / "projects"
    directory = root / "commons-utils" / "src" / "test" / "java"
    directory.mkdir(parents=True)
    assert _get_source_type(str(directory), root) == "test"


def test_the_projects_root_itself_is_not_a_project(tmp_path):
    root = tmp_path / "projects"
    root.mkdir(parents=True)
    with pytest.raises(ValueError, match="projects root"):
        _get_project_name(str(root), root)
