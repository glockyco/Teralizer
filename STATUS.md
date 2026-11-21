# Artifact status

We apply for the ACM badges Artifacts Available, Artifacts Evaluated Functional, and Artifacts Evaluated Reusable.

## Available

The release is archived on Zenodo under the concept DOI [10.5281/zenodo.17950380](https://doi.org/10.5281/zenodo.17950380), which resolves to the latest version. Each version has its own DOI, listed in [CITATION.cff](CITATION.cff).

## Functional

- **Documented:** [README.md](README.md) describes every workflow, [INSTALL.md](INSTALL.md) the download and the smoke test, [REQUIREMENTS.md](REQUIREMENTS.md) the host, resources and times, and `RELEASE.md` on Zenodo the archives each workflow needs.
- **Consistent:** the registered reports generate every table, figure and number of the paper's evaluation from the published corpora.
- **Complete:** the release holds Teralizer's source, the four evaluation corpora, every analyzed project, the reference results, and the evidence behind RQ0's comparison with JARVIS ([replication/RQ0.md](replication/RQ0.md)).
- **Exercisable:** `./artifact smoke` rebuilds RQ0 in a few minutes, and `./artifact reproduce-results` rebuilds every report and compares each output with the reference. `./artifact collect-reduced` reruns the pipeline on a reduced scope of each corpus.

## Reusable

- [replication/DATA-DICTIONARY.md](replication/DATA-DICTIONARY.md) describes every table, the views the reports read, and every result CSV file.
- `scripts/try-example.sh` runs Teralizer on an example project or on a Maven or Gradle project of your own, in its own database.
- Every service runs in a container with pinned images, so the results do not depend on host Java or Python installations.
- Source code is MIT licensed, data and documentation CC BY 4.0. The analyzed projects keep their own licenses.
