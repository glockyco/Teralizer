---
title: Oracle-Refusal Taxonomy on the RQ6 Corpus
type: audit
status: active
created: 2026-08-04
parent: 2026-06-26-teralizer-overview
superseded_by:
archived:
---

# Oracle-Refusal Taxonomy on the RQ6 Corpus

What the widening license refuses, why, and which recovery directions carry
real leverage. The bucket structure is the durable finding; every count below
is a measurement from the superseded pre-fix corpora (`postgres_reporeapers_rq6`,
measured 2026-08-04 over `generalization.variant = 'IMPROVED_200_TRIES'`, 611
eligible projects; the bucket shares reproduced on the also-superseded v2
corpus: state-derived 866 of 2,445 refusals, string composition 713, boxing-only
772, other unmodeled 94, with no `SYMBOLIC` or `CONSTANT` oracle refused).

None of these counts may be cited for the thesis. The citable numbers come from
re-running the bucket queries on `postgres_reporeapers_rq6_v4` once its
collection finishes: the JUnit 3 assertion-analysis fix enlarges the analyzed
assertion population by roughly a fifth, so the refusal population and shares
must be re-derived, not assumed stable.

## Where the corpus stands

| Level | Total | Included | Filtering | Failures |
|---|---|---|---|---|
| Test | 85,372 | 36,118 (42.3%) | 42,965 | 6,289 |
| Assertion | 135,628 | 4,528 (3.3%) | 125,187 | 5,913 |
| Generalization | 4,121 | 1,061 filter-passed (25.7%) | 3,019 | 41 |

Generalization lifecycle, per attempt: 1,412 source created, 1,386 compiled,
1,371 executed, 1,348 report collected, 1,102 `is_included`, 1,061
`generated_filter_passed`, 510 `final_usable`. Projects: 149 with at least one
attempt, 86 with at least one licensed generalization, 73 with at least one
filter-passed generalization, 42 through reduction.

The 3,019 filtering exclusions are 2,708 `ORACLE_NOT_WIDENABLE` plus 311
generalization-filter rejects (287 `NonPassingTest`, 22 `ExcludedTest`, 1
unattributed). The 41 failures are 26 `OTHER_COMPILE_FAILURE` (7 projects) and 15
`EXECUTE_TESTS_GENERALIZED` failures (6 projects). Stage 4 is therefore
88.5% license refusal, 10.1% filter rejection, 1.3% engineering failure.

## Refusal is a NULL_CONCRETE phenomenon

| `output_spec_class` | Attempts | Refused | Refusal rate |
|---|---|---|---|
| `NULL_CONCRETE` | 3,341 | 2,706 | 81.0% |
| `SYMBOLIC` | 695 | 0 | 0% |
| `EXCEPTION` | 83 | 2 | 2.4% |
| `CONSTANT` | 2 | 0 | 0% |

No `SYMBOLIC` oracle is refused. Only 695 of 4,121 attempts (16.9%) obtain a
symbolic output expression at all, so the Stage-4 loss is an extraction
deficiency surfaced at generalization time, not a defect of the widening rule.
1,751 of the 2,708 refusals (64.7%) sit on paths with at least one concretization
event.

## Four refusal patterns

Buckets are disjoint, computed from `assertion.concretized_methods` and
`concretization_events`.

| Pattern | Refusals | Share | Projects |
|---|---|---|---|
| No symbolic output — value derived from receiver, array, or collection state (`concretization_events = 0`) | 957 | 35.3% | 74 |
| String composition — `StringBuilder`, `StringBuffer`, or `java.lang.String` calls concretized | 881 | 32.5% | 78 |
| Boxing only — every concretized call is a wrapper `valueOf` | 767 | 28.3% | 16 |
| Other unmodeled calls — reflection, `DecimalFormat`, `System.arraycopy` | 103 | 3.8% | 12 |

Most-concretized methods on refused paths: `Long.valueOf` 679 (2 projects),
`StringBuilder.<init>()` 595 (68), `StringBuilder.append(String)` 580 (63),
`StringBuilder.toString()` 343 (41), `StringBuilder.append(char)` 130 (29),
`String.split` 108 (4), `Integer.valueOf` 102 (17).

Refusals by tested-method return type: `int` 1,034 (777 concretized),
`java.lang.String` 819 (562), `boolean` 537 (319), `char` 54, `long` 52,
`java.lang.Double` 45, `double` 40, then a tail of project types. 325 refusals
involve lifted constructor arguments.

Representative cases:

- Boxing only — `Size.compareTo`, seed `Size.gigabytes(1).compareTo(Size.terabytes(0))`,
  asserted `assertTrue(… > 0)`; the only concretization is `java.lang.Long.valueOf`.
- String composition — `FizzBuzz.getValue(int)`, asserted `assertEquals("3", fizzBuzz.getValue(3))`,
  concretized at `Integer.toString`. The expected side stays the literal `"3"`, so an
  unlicensed widening would claim `∀n` in the partition that the result is `"3"` — false
  for every admitted input except the seed. Also `Inflector.singularize("people")`
  (56 `StringBuilder` events) and `ImageName.getRegistry` (`String.split` parsing).
- No symbolic output — `assertFalse(department.containsSupervisor(USER_UID))`,
  `assertEquals(0, result.getColumnIndex("column 1"))`, and predicates over lifted
  constructor arguments such as `assertThat(TEHUtils.equals(new Pojo(1, "2"), new Pojo(1, "3")), is(false))`,
  where the comparison reads heap fields and leaves no clause naming the widened inputs.

## Leverage: assertion-weighted and project-weighted rankings disagree

For the 57 projects that lose every generalization to the license:

| Pattern | Refusals in blocked projects | Blocked projects touched |
|---|---|---|
| String composition | 414 | 40 of 57 |
| No symbolic output | 382 | 33 of 57 |
| Boxing only | 33 | 6 of 57 |
| Other unmodeled | 51 | 5 of 57 |

Symbolic string coverage is the broadest recovery direction. The boxing bucket is
28.3% of refused assertions but reaches at most 6 blocked projects, because 679 of
its 767 refusals sit in two projects that other assertions already carry.

Boxed-output capture is already shipped (`TestGeneralizationListener.java:289-300,451-483`;
`archive/2026-07-03-boxed-output-capture` is `implemented`) and does not address this
bucket: the wrapper `valueOf` calls are unmodeled calls hit mid-path, so operands are
concretized and no symbolic output survives to capture. Recovering them needs symbolic
models or native peers for wrapper factory and comparison methods — the same family as
`2026-06-28-native-peer-model-coverage`, not an output-plumbing change.

The bucket is also narrower than its count suggests, which is why it is deprioritized:

| Wrapper | Refusals | Projects |
|---|---|---|
| `Long.valueOf(J)` | 679 | 2 |
| `Integer.valueOf(I)` | 76 | 12 |
| `Character.valueOf(C)` | 10 | 2 |
| `Boolean.valueOf(Z)` | 2 | 1 |

678 of the 679 `Long.valueOf` refusals belong to `github_com_joschi_JadConfig`, which
already holds 79 validated generalizations and is therefore already applicable. Recovering
them would add oracle coverage inside a project that succeeds, and would require modeling
`Long.compareTo` as well as `valueOf`, since the seeds are comparison chains such as
`Size.gigabytes(1).compareTo(Size.terabytes(0))`. Across the six projects the bucket could
actually unblock, it accounts for roughly 20 to 40 refusals.

The state-derived bucket is where a property is often plausibly true yet unprovable
from the extracted evidence. Those are candidates for human review rather than
automated recovery.

## Stage-5 attrition is baseline-side

Stage-5 exclusions are recorded here because they are easy to misread as
generalization failures. Of the 31 projects that reach reduction with a validated
generalized test and are still excluded, 28 fail while measuring the *original*
suite: 15 PIT non-zero exits and 5 PIT timeouts at `COLLECT_PIT_DATA_INITIAL`, and
8 missing `jacoco.csv` at `COLLECT_JACOCO_DATA_INITIAL`. Only 3 fail on the
generalized side — 2 PIT non-zero exits and 1 `Unexpected test name format: …[engine:jqwik]`,
which is a report-parser defect in Teralizer itself. Of the 551 validated
generalizations lost at Stage 5, 349 sit in the baseline-failed projects.
