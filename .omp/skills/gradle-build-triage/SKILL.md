---
name: gradle-build-triage
description: Build/run the Teralizer Gradle pipeline and triage failures. Use when ./gradlew build fails, the SPF/jpf-symbc submodule is missing, or a run config errors.
---

# Gradle build triage

## Build & run
```bash
./gradlew build                                   # includes SPF submodules
./gradlew run -Dteralizer.config=project-configs/example-maven-junit5.conf
```

## Triage
1. **Submodule errors** (`jpf-symbc` missing / classpath): `git submodule update --init --recursive`, rebuild.
2. **DB connection refused**: `./gradlew startPostgres` first; confirm `postgres-teralizer` is up.
3. **Config not found**: pass an existing `project-configs/example-*.conf` (HOCON).
4. Read the Gradle stacktrace from the bottom up; rerun the single failing task with `--stacktrace`.
