---
description: Python analysis conventions (uv, ruff, ty, eval reports)
globs:
  - "analysis/**/*.py"
---

# Python analysis conventions

- Manage env/deps with `uv` (never bare `pip`); run tools via `uv run --directory analysis ...`.
- Lint+fix `ruff check --fix`, format `ruff format`, type-check `ty check`, test `pytest`.
- Tests split on the `db` marker. `pytest -m "not db"` is the inner loop and runs on every commit.
  The full suite queries a real corpus and runs on push. CI runs only the marker-free set, because
  it has no corpus.
- A `db` test skips when PostgreSQL is unreachable, so a green run on a machine without the corpus
  proves nothing about it. Use `-ra` to see what skipped.
- Marking is automatic for anything requesting a database fixture, see
  `pytest_collection_modifyitems` in `tests/conftest.py`. Mark a module explicitly only when it
  opens a connection in the test body.
- Build reports with `uv run --directory analysis python -m teralizer.eval all` before publishing.
- Export through the eval renderers and `teralizer.exports` (`save_latex_table`/`save_csv_data`/`save_figure`), not ad-hoc writes.
