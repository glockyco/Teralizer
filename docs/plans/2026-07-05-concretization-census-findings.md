---
title: Concretization Census — Load-Bearing Blockers and the License Over-Refusal Finding
type: audit
status: active
created: 2026-07-05
parent: 2026-07-04-concretization-census
---

# Concretization Census — Load-Bearing Blockers and the License Over-Refusal Finding

**One finding:** ranked by the generalizations they actually block, the concretizing methods split into a large incidental majority the license never consults, a small bounded set of genuine native-peer gaps, and one dominant blocker that is not an SPF gap at all but a widening-license over-refusal on exception-message construction. Fixing SPF peers pays far less than the naive count suggests, and the highest-ROI lever is the license, not the model.

## Method

Three runs on the identity telemetry committed as `147aedc4` (the `concretized_methods` JSON map beside `concretization_events`), no full corpus run:

- Fixture corpus (`postgres_verification`, 12 fixtures).
- Five-project sentinel subset (`postgres_sentinel_verify`): svdrp4j, MarkupTagScanner, TDD-Katas, unicrypt, JadConfig.
- antiaction single-project hotspot (`postgres_census_hotspot`, `project-configs/hotspot/project-933.conf` against the local checkout).

**Load-bearing filter.** A concretization event blocks a refused generalization only when the widening license actually consults the event count for that output shape (`WideningLicense.evaluate`, gate order):

- `EXCEPTION`: the event count is checked first (line 81), so events are load-bearing.
- `NULL_CONCRETE`: a non-boolean return is refused at line 89 *before* the event check at line 92. Events are load-bearing only for `boolean`/`java.lang.Boolean` returns.
- `SYMBOLIC`/`CONSTANT`: always widen; never refused for events.

Every other refused-with-events row is incidental: zeroing the event would not change the verdict. Ranking on the naive count (assertions × refused gens) — the metric the census spec proposed — counts these incidental rows and badly over-attributes. Each method is scored by load-bearing blocked generalizations, split into **gross** (credit shared across co-occurring methods on an assertion) and **sole-blocker** (the method is the only concretizer, a guaranteed per-fix conversion floor).

## Refusal accounting

| Source | total refused | refused w/ events | load-bearing | incidental |
|---|---|---|---|---|
| sentinel (5 projects) | 966 | 838 | 28 | 810 |
| fixture corpus (12) | 3 | 1 | 1 | 0 |
| antiaction hotspot | 129 | 104 | 74 | 30 |

The naive count would rank `Long.valueOf(J)` first at 678 sentinel refusals. All 678 are `NULL_CONCRETE` with an `int` return and are refused by the non-boolean-oracle gate. Events never enter their verdict. They are incidental.

## Load-bearing ranking (union of the three runs)

| Method | load-bearing gens | sole-blocker | spec class | bucket |
|---|---|---|---|---|
| `StringBuilder.append(String)` + `StringBuilder.<init>()` | 74 (antiaction) | 0 (always co-occur) | EXCEPTION | license over-refusal |
| `String.matches(String)` | 16 (sentinel) | 16 | NULL_CONCRETE (bool) | research-grade (regex) |
| `String.hashCode()` | 5 (sentinel) | 5 | NULL_CONCRETE (bool) | research-grade (string content) |
| `Character.isWhitespace(char)` | 4 (sentinel) | 4 | NULL_CONCRETE (bool) | bounded |
| `Boolean.valueOf(boolean)` | 2 (sentinel) | 2 | NULL_CONCRETE (bool) | bounded |
| `String.lastIndexOf(int)` | 1 (sentinel) | 1 | NULL_CONCRETE (bool) | bounded |
| `System.arraycopy(...)` | 1 (fixture) | 1 | EXCEPTION | fixture arm (by design) |

## Findings

### 1. The valueOf family is incidental, not the bounded target

The census spec named the `Long`/`Boolean.valueOf` attr fix (`b5e3b06`) as the worked bounded candidate and treated the bounded bucket as pre-satisfied. The data contradicts the premise. The `Long.valueOf(J)` peer preserves the argument expression on the box's `value` field, yet 678 sentinel refusals still carry the event, because the tested methods return `int`: a symbolic `long` is boxed (attr preserved on the box) and then unboxed back to a primitive, and the unbox side drops the field attr, so the captured output is `NULL_CONCRETE`. Those refusals are not caused by the event count. They are caused by a concrete non-boolean oracle with no symbolic evidence. Zeroing the events converts none of them. The boxing round-trip attr recovery is a real but separate lever (it would upgrade `NULL_CONCRETE` to `SYMBOLIC`, which always widens); it is medium-grade, not the bounded quick win the spec assumed.

### 2. The dominant load-bearing blocker is a license over-refusal, not an SPF gap

antiaction's 74 THROWN refusals — the concrete hotspot the spec cited — all carry exactly `{StringBuilder.<init>: 1, StringBuilder.append(String): 1}`. The source is `throw new JSONException("Unknown conveter name: " + converterName)`. String concatenation in the exception message compiles to a `StringBuilder` append, and that concretization happens *after* the throw-determining branch (`id == null`, a map-lookup path condition). The message never affects throw reachability, and the `EXCEPTION` oracle checks only the exception type. The license refuses anyway because it applies a blanket `concretization_events > 0` refusal to `EXCEPTION` oracles, unable to distinguish concretization that happens before the last reachability-relevant branch from concretization that happens after it. This is the single largest load-bearing blocker found and it is a license-precision question, not a native-peer gap. It is not a cheap tweak: a sound refinement needs a mechanism to establish that no path-condition-relevant branch followed the concretization. Recorded here with its weight so the ceiling is honest.

### 3. The genuine bounded native-peer gaps are small

The load-bearing bounded items are `Character.isWhitespace(char)` (4 sole-blocker), `Boolean.valueOf(boolean)` (2), and `String.lastIndexOf(int)` (1). `Character.isWhitespace` is a pure character predicate returning boolean, directly analogous to the already-shipped sound `String.isEmpty` model, and is the top of the bounded bucket. The counts are small because the sentinel is five projects; they are real conversions, and they establish the refusal-to-licensed measurement the acceptance asks for.

## Triage buckets

- **Bounded (fix as normal tasks with fixture coverage):** `Character.isWhitespace(char)` (top, 4 sole-blocker), then `Boolean.valueOf(boolean)` (2), `String.lastIndexOf(int)` (1). Each is a sound-modelable predicate or a bounded native peer adjacent to the shipped sound set.
- **Medium (spec individually if ranked high):** the boxing round-trip attr recovery behind Finding 1 (upgrades the 678-strong `Long.valueOf` NULL_CONCRETE-int class to SYMBOLIC); bounded-index `String.charAt`/`substring` as already deferred in the string plan.
- **Research-grade (recorded, not attempted):** `String.matches` (16, regex), `String.hashCode` (5, symbolic string content). Listed with weights; not scheduled.
- **License-precision (new, outside the SPF-gap framing):** the exception-message over-refusal of Finding 2 (74 in antiaction alone, the largest single load-bearing blocker). Needs a pre-decision-versus-post-decision concretization mechanism before the license can widen soundly. Highest potential ROI of any item found.

## What this changes

The census spec's acceptance names "the top bounded-bucket item fixed with a fixture, and the refusal-to-licensed conversion measured on the sentinel subset." The top bounded item is `Character.isWhitespace`, not the valueOf family. The larger strategic result is that closing SPF native-peer gaps pays far less than the naive concretization count implied, and the two highest-value levers are the widening license (Finding 2) and boxing attr round-trip recovery (Finding 1), neither of which is a native-peer model.
