To re-run the project selection:

1. Install uv: https://github.com/astral-sh/uv
2. Install pyenv: https://github.com/pyenv/pyenv
3. Install python: `pyenv install 3.13`
4. Install dependencies: `uv sync`
5. Download `dataset.csv`: https://reporeapers.github.io/results/1.html ("Download > Entire Data Set")
6. Place `dataset.csv` in this directory 
7. Create `dataset_java.csv`: `awk -F, 'NR==1 || ($2=="Java" && $10 > 5000 && $10 < 50000 && $11 > 0.2 && $11 < 0.8)' dataset.csv > dataset_java.csv`
8. Create `dataset_java_extended.csv`: `python build-extended-dataset.py`
9. Create `dataset_java_extended_filtered`: `python build-filtered-dataset.py`
10. Create project configuration files for Teralizer: `python build-project-confs.py` 
