---
name: python-analysis-conventions
description: How to run and validate the Python analysis project. Use when working in analysis/, running notebooks, validating changes, or adding analysis modules/exports.
---

# Python analysis conventions

Always run from the repo root, tools via `uv`:
```bash
uv sync --directory analysis
uv run --directory analysis python validate.py --changed   # gate before commit
uv run --directory analysis ruff check --fix . && uv run --directory analysis ruff format .
uv run --directory analysis ty check . && uv run --directory analysis pytest
uv run --directory analysis jupyter lab
```
- `validate.py` covers imports/env/DB/notebook-exec/lint/types. Use `--notebook <NAME.ipynb>` to scope.
- Clear notebook outputs before commit; `notebooks/legacy/` is excluded.
- Output via `teralizer.exports` → `analysis/output/{tables,data,figures}`.
- Paper sync is manual: `uv run --directory analysis python sync.py` (needs `PAPER_REPO_PATH`).
