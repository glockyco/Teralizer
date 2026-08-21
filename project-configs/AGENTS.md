# Pipeline run configurations

Configs compose through `-Dteralizer.config=<profile>,<project>`. Later files override earlier
files. JVM system properties override both. `reference.conf` fills missing values.

## Ownership

- Profiles define database targets, variants, and run policy.
- Per-project configs define project roots and project-specific limits.
- `extended/` is the source for generated configs under `replication/extended/`. Change the source
  or generator, then regenerate. Do not hand-edit generated configs.
- `verification/` maps one config to each fixture under `verification/fixtures/`.
- Runner scripts define which profile and config directories they consume. Read the runner before
  adding or moving a lane.
- Completed measurement configs are historical inputs. Do not rewrite their database names or limits
  after the run.

## Safety and composition

- `reference.conf` must not define a database name or generalization variant. A profile or runner
  must supply both.
- Do not target a corpus registered in `src/main/resources/db/corpora.toml`. Run profiles target only `scratch_*` databases.
- HOCON merges object keys. A `teralizer.generalizations` block does not replace the earlier block.
  Inspect the merged configuration after changing either side.
- `Configuration` freezes values when its class loads. Tests must put defaults in
  `src/test/resources/reference.conf`. Do not depend on a later `System.setProperty` call.
