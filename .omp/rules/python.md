---
description: Python analysis conventions (uv, ruff, ty, notebooks)
globs:
  - "analysis/**/*.py"
  - "**/*.ipynb"
---

# Python analysis conventions

- Manage env/deps with `uv` (never bare `pip`); run tools via `uv run --directory analysis ...`.
- Lint+fix `ruff check --fix`, format `ruff format`, type-check `ty check`, test `pytest`.
- Run `validate.py --changed` before committing analysis changes.
- Clear notebook outputs before committing; `notebooks/legacy/` is excluded via `pyproject.toml`.
- Export via `teralizer.exports` (`save_latex_table`/`save_csv_data`/`save_figure`), not ad-hoc writes.
