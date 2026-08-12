import pandas as pd

from teralizer.eval.reports.dataset_characteristics import _table


def test_dataset_table_matches_thesis_structure():
    table = _table(
        pd.DataFrame(
            {
                "project": ["eqbench-es-default-1s"],
                "main_files": [544],
                "main_classes": [652],
                "main_sloc": [27871],
                "test_files": [544],
                "test_classes": [544],
                "test_sloc": [35666],
                "test_methods": [4718],
            }
        )
    )
    assert table.key == "tab-dataset-statistics"
    assert (
        table.short_caption
        == "Implementation and test-suite size per evaluation project"
    )
    assert table.body_style == r"\tabstyle"
    assert table.full_width
    assert table.group_header_align == "r"
    assert [column.header for column in table.columns] == [
        "Project",
        "Files",
        "Classes",
        "SLOC",
        "Files",
        "Classes",
        "SLOC",
        "Methods",
    ]
    assert [column.group_header for column in table.columns[1:]] == [
        "Implementation",
        "Implementation",
        "Implementation",
        "Test",
        "Test",
        "Test",
        "Test",
    ]
