import pandas as pd

from teralizer.rq2_test_suite_effects import compute_runtime_change_statistics


def test_runtime_change_statistics_replace_text_columns_with_numeric_values():
    rows = pd.DataFrame(
        {
            "project_name": ["commons-lang", "commons-math"],
            "runtime_before": ["1.0", "1.0"],
            "added_runtime": ["1.0", "1.0"],
            "removed_runtime": ["0.0", "0.0"],
            "runtime_after": ["2.0", "2.0"],
            "runtime_delta": ["1.0", "1.0"],
            "runtime_delta_pct": ["100.0", "9.0"],
        }
    )

    result = compute_runtime_change_statistics(rows)

    assert pd.api.types.is_float_dtype(result["runtime_before"])
    assert pd.api.types.is_float_dtype(result["runtime_delta_pct"])
    assert result.sort_values("runtime_delta_pct")["runtime_delta_pct"].tolist() == [
        9.0,
        100.0,
    ]
