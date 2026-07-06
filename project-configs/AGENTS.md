# Pipeline run configurations

Configs compose via `-Dteralizer.config=<profile>,<project>` — later files override earlier
ones, JVM system properties override both, `reference.conf` fills the rest. `reference.conf`
deliberately defines no database name and no generalization variants: a profile (or the
driver's system properties) must supply them.

## Directory roles

| Path | Species | Consumed by |
|---|---|---|
| `reporeapers-rerun.conf`, `verification.conf` | Composable profiles (DB, variants, PIT policy) | `scripts/run-reporeapers-rerun.sh`, `scripts/run-verification-corpus.sh` |
| `example-*.conf`, `eqbench.conf` | Self-contained runnable configs | manual `./gradlew run` (AGENTS.md command table) |
| `replication/extended/` (1,161) | Per-project corpus, local roots | reporeapers driver default; `replication/scripts/run.sh` |
| `extended/` (1,161) | SOURCE corpus, remote GitHub URLs | `replication/scripts/generate-replication-configs.sh` regenerates `replication/extended/` from it; `replication/scripts/run.sh` falls back to it |
| `primary/{generation,generalization}/` | Primary-dataset lane (EqBench + commons-utils) | `replication/scripts/run.sh --dataset primary` |
| `verification/` (16) | Fixture corpus configs | verification driver |
| `sentinel/` (5) | Tier-2 real-project verification subset | reporeapers driver via `REPOREAPERS_CONFIG_DIR` |
| `jarvis-scoreboard/` (6) | JARVIS scorecard + census lane | `scripts/run-jarvis-scoreboard.sh`, `scripts/run-jarvis-census.sh` |
| `fusion-spike/` (23) | Corpus-claims verification tier (~1 h) | verifying-pipeline-changes skill, top tier |
| `hotspot/` (1) | Concretization-census hotspot | manual; retires when the queued antiaction NPE trace lands |
| `spikes/` (1) | R1-viability spike | retires with the R1/R2 verdict |
| `timeout-retry*.conf` (untracked) | Stale July retry one-offs | nothing; regenerated from the next timeout list, then deleted |

## Don't / Instead

| Don't | Instead |
|---|---|
| Treat `extended/` as a dead duplicate of `replication/extended/` and delete it | It is the generation SOURCE and the replication runner's fallback. Regenerate `replication/extended/` from it via `replication/scripts/generate-replication-configs.sh`. |
| Point a run at `postgres_dev`/`postgres_test`/other protected DBs from a config | Scratch DBs only. The protected list is `src/main/resources/db/protected-databases.txt`, enforced by the Java startup guard and `scripts/lib/db-guard.sh`. |
| Add a generalization variant to `reference.conf` | Variants are profile-only (`teralizer.generalizations` blocks). `reference.conf` stays variant-free by design. |
| Hand-edit files under `replication/extended/` | Fix the source config under `extended/` (or the generator) and regenerate. |
