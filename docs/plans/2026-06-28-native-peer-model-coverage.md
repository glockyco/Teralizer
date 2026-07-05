---
title: Native Peer & Model-Class Coverage
type: spec
status: draft
created: 2026-06-28
parent: 2026-06-26-teralizer-overview
---

Improve SPF's symbolic coverage of JDK/library methods whose JPF model classes or
native peers are missing or incomplete, so more RepoReapers paths stay symbolic
instead of crashing or silently concretizing. Spun out of the maxUlps raw-bits lane
(absorbs its `abs` task); the raw-bits ulps chain is archived as bounded-upstream
(`archive/2026-06-28-maxulps-raw-bits-lane.md`).

## Why this is its own lane

The maxUlps lane bundled a `Math`/`FastMath` `abs` task (B-1) with the deep raw-bits
SPF work (`doubleToRawLongBits` Model node, per-variable bit-width, concretization
tagging). That chain is research-grade upstream-SPF work for one bonus probe and is
archived as bounded-upstream (`archive/2026-06-28-maxulps-raw-bits-lane.md`).
Peer/model-class coverage is a separate, broader, partly-tractable concern
with its own corpus evidence — it belongs in its own spec.

## Evidence (DB-grounded, from `2026-06-26-applicability-barriers` #21)

Crash-visible peer/model gaps in the collected `EXECUTE_JPF` failures:

| Cause | dev | real-world | Kind |
|---|---|---|---|
| `class not found` (`NoSuchMethodException`) | 534 | 50 | partly resolvable — add model class |
| native peer / `UnsatisfiedLinkError` (URLDecoder, etc.) | — | ~109 | partly resolvable / partly genuinely native |
| transcendental / `sqrt` at Z3 bridge | 1 | — | not resolvable — no decidable theory |

These are the **observable** gaps: a missing peer/model *crashes* the analysis. This
is barrier #21, rated MED (partial). The dominant applicability wall is the type
ceiling (objects/strings), not peers, so this lane is a targeted set of cheap wins,
not a needle-mover for overall RepoReapers reach.

The beyond-JARVIS census corroborates this row in a second corpus: 154 `EXECUTE_JPF`
per-assertion exclusions in one commons-math run are all `class not found:
java.lang.NoSuchMethodException` — a JDK class absent from JPF's model classpath —
surfacing across seven methods whose symbolic paths reference it: `Precision.round` (55),
`Precision.equals` (32), `Precision.equalsIncludingNaN` (23), `Precision.compareTo` (20),
`ArithmeticUtils.gcd` (11), `ArithmeticUtils.lcm` (11), and `FastMath.getExponent` (2).
These are concrete, ranked per-method targets for the task below; whether each is cheaply
resolvable (model the class) or bounded is for that task to determine. The gap is
corpus-general, not a RepoReapers artifact.

## The unmeasured mode (telemetry prerequisite, not assumed)

A second failure mode is **invisible**: a native peer that intercepts a call and
returns a concrete value produces a narrower-but-valid spec, not a failure, so it is
absent from `EXECUTE_JPF` failures. Examples (`spf-eval` characterization):
`Float.isNaN`/`isInfinite` (JPF-core peer returns a concrete boolean, no fork);
raw-bits under `z3` (bakes in a constant — the maxUlps D-1 case). Counting
this needs concretization-coverage tagging, the general form of that raw-bits
D-1. **Build the observable buckets first**; treat silent-mode measurement as a
separate, gated telemetry task.

## Gap taxonomy (mechanism, from `spf-eval/RESULTS.md`)

JPF resolves a method by model class (`src/classes/`) first; only a `native`-declared
model-class method falls to an MJI peer.

1. **Modelable — pure-Java model class.** `Math.abs/min/max` already work this way
   (pure-Java conditionals executed symbolically; finding 11). Library/user methods
   reachable as pure-Java bytecode can be added the same way, or made reachable by the
   model-classpath fix (e.g. `FastMath.abs` reaching the eps branch). Cheapest, highest-value.
2. **Symbolic-aware peer needed.** `Float.isNaN`/`isInfinite` are intercepted by the
   JPF-core peer before bytecode and return concrete values (findings 14, 46). Forking
   needs a peer that creates a `PCChoiceGenerator` with the right constraint (analogous
   to FCMPL). Medium effort, soundness-sensitive.
3. **Not resolvable.** Transcendentals (`sin`/`cos`/`sqrt`: Z3 has no decidable theory;
   finding 13) and genuinely-native I/O (`URLDecoder`, etc.). Bounded; out of scope.
4. **String-handler gaps** (`SymbolicStringHandler` missing methods → hard crash) are a
   distinct subsystem (string interception, not peers); cross-reference, do not absorb.

## Tasks (evidence-gated)

- [x] Rank crash-visible gaps by corpus frequency. Decoded from the 368 `EXECUTE_JPF`
  failures in `postgres_test` (155 attribute to a specific missing method):

  | count | projects | kind | target |
  |---:|---:|---|---|
  | 67 | 1 | model method | `java.nio.StringCharBuffer.nextGetIndex` |
  | 43 | 4 | native peer | `java.lang.reflect.Method.getGenericParameterTypes` |
  | 26 | 4 | native peer | `java.util.zip.ZipFile.initIDs` |
  | 6 | 1 | model method | `java.util.Scanner.useLocale` |
  | 6 | 1 | native peer | `java.util.zip.Inflater.initIDs` |
  | 4 | 1 | model method | `java.nio.ByteBuffer.arrayOffset` |
  | 2 | 1 | model method | `sun.misc.Unsafe.getAndAddInt` |
  | 1 | 1 | model method | `java.nio.ByteBuffer.order` |

  Ranking reading: multi-project targets outrank single-project counts.
  `Method.getGenericParameterTypes` (4 projects, java.beans introspection chains) and
  `ZipFile.initIDs` (4 projects) are the top candidates. `StringCharBuffer.nextGetIndex`
  is one project's charset decoding hot loop. The zip/reflection targets sit near the
  genuinely-native boundary, so each needs the modelable-versus-bounded call from
  taxonomy 1 versus 3 before any work starts.

  The non-peer remainder for context: 48 target-level reflection
  `NoSuchMethodException`, 34 SPF solver debug-option crashes, 24 depth/PC-limit aborts
  via `searchConstraintHit`, 18 solver div-by-0, 17 unattributed
  `UnsupportedOperationException`, 13 slf4j `StaticLoggerBinder` initialization, 12
  symbolic-length `NEWARRAY`, 47 other. These are separate failure classes, not peer
  gaps. The commons-math `Precision.*` targets from the census corroboration do not
  appear here because `postgres_test` is the RepoReapers corpus.
- [ ] For the top **modelable** targets (taxonomy 1): add pure-Java model classes (the
  `Math.abs` pattern) or fix model-classpath reachability. Each addition must be
  behaviorally equivalent (soundness) and justified by its corpus-frequency rank.
- [ ] B-1 (from the maxUlps lane): make `abs` stay symbolic regardless of model-class
  reachability — `MathFunction.ABS` + Z3 translation, or a branch-equivalent model class
  — so `abs(xInt - yInt)`-style steps do not depend on which `abs` (`java.lang.Math` vs
  `FastMath` vs user) is on the path.
- [ ] (Secondary, gated) concretization-coverage tagging to measure the silent mode
  (taxonomy 2 + raw-bits), only after the observable buckets are addressed and the
  generation-coverage shape telemetry exists.

## Out of scope

- Transcendentals and genuinely-native I/O (taxonomy 3) — bounded, not fixable via peers.
- The raw-bits maxUlps chain (`doubleToRawLongBits` Model node, per-variable bit-width) —
  being retired as bounded-upstream-SPF (`2026-06-28-maxulps-raw-bits-lane`), pending the refresh.
- String-handler coverage (taxonomy 4) — distinct subsystem.

## Relationship to existing docs

- **Evidence:** `2026-06-26-applicability-barriers` #21 + SPF-stage failure breakdown.
- **Absorbs:** B-1 from `2026-06-28-maxulps-raw-bits-lane` (being retired).
- **Mechanism reference:** `~/Projects/phd-thesis/projects/spf-eval/RESULTS.md` (model-class
  vs native-peer dispatch; findings 11, 13, 14, 46).
- **Bounds:** peers are a MED-partial lever; the type ceiling (objects/strings) is the
  dominant applicability wall — see `2026-06-26-applicability-barriers`.

## Acceptance criteria

- Each model-class/peer addition is justified by a corpus-frequency rank and is
  behaviorally equivalent (no unsound narrowing).
- Crash-visible gaps addressed are removed from the `EXECUTE_JPF` failure buckets on a rerun.
- Genuinely-unfixable gaps are documented as bounded, not left as silent concessions.
- `omp-plans check` and any touched Java tests pass.
