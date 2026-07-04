---
title: R1 Viability Spike — Expression-Slice Shapes Through SPF
type: audit
status: active
created: 2026-07-04
parent: 2026-07-02-recipe-seam-review
---

# R1 Viability Spike — which T2 shapes yield symbolic specs

**One concern:** the topology spike's R1 opportunity numbers (~7-8k assertions) are upper
bounds on *attempts*; this spike measures which T2 expression-slice shapes actually produce
usable specs when run through today's extraction machinery, so the R1 spec is scoped by
realized value, not hope.

## Method

Nine hand-built wrappers (`verification/spikes/r1-viability/`, config
`project-configs/spikes/r1-viability.conf`), each the exact method R1 instrumentation would
emit for one topology bucket: the asserted expression as body, input sites lifted to
parameters. Run as ordinary T0 MUTs through the unmodified pipeline
(`postgres_r1spike_verify` scratch DB, verification profile, IMPROVED_100_TRIES). One
gradle-green run; all rows below are that run's first-pass observations.

## Per-shape outcomes

| Shape (bucket) | Wrapper | output_spec_class | Outcome |
|---|---|---|---|
| T0 control | `a + b` | SYMBOLIC | included, FULL 100/100 |
| Chain → project inspector | `Box.of(a).value()` | SYMBOLIC | included, FULL 100/100 |
| Chain → `List.size()` | `buildList(n).size()` | NULL_CONCRETE (int) | **refused** ORACLE_NOT_WIDENABLE |
| Operator composite over calls | `intCompare(a,b) > 0` | NULL_CONCRETE (boolean) | included via license, FULL 100/100 |
| `compareTo` comparison | `new Pair(a).compareTo(new Pair(5)) < 0` | NULL_CONCRETE (boolean) | included via license, FULL 100/100 |
| Ctor-only equality | `new Pair(a,b).equalsPair(new Pair(a,5))` | NULL_CONCRETE (boolean) | included via license, FULL 100/100 |
| Cast-wrapped call | `(long) timesTwo(a)` | SYMBOLIC | included, FULL 100/100 |
| Arithmetic composite | `timesTwo(a) + timesTwo(b)` | SYMBOLIC | included, FULL 100/100 |
| Two-hop project chain | `Box.of(a).twice().value()` | SYMBOLIC | included, FULL 100/100 |

## Findings

1. **Pure-dataflow slices stay symbolic end to end.** Constructor fields, project-method
   hops, casts, and arithmetic composition all preserve the symbolic attr through SPF and
   render as SYMBOLIC expected sides. No new extraction machinery is needed for these.
2. **Comparison-shaped composites land as boolean-in-PC and the widening license already
   handles them.** `>`, `compareTo`-based, and field-equality shapes produce NULL_CONCRETE
   boolean specs whose relation lives in the path condition; the license admits them
   (params named in the PC, zero concretizations), and all pass FULL 100/100. R1 gains this
   family without touching the license.
3. **Control-flow-mediated values degrade exactly as predicted, and safely.** The
   `List.size()` chain concretizes (loop counter, no attr) → NULL_CONCRETE int → refused by
   the license. The failure mode of the weakest bucket is a typed refusal, not an unsound
   emitted property. Finding 3 of the recipe-seam review is confirmed with the bound
   landing on the safe side.

## Bucket → viability mapping (upper bounds from the topology spike)

- OPERATOR_COMPOSITE with calls (4,964): viable as licensed boolean-in-PC where the
  compared values are pure dataflow; SYMBOLIC where arithmetic.
- CHAINED with input sites + supported end (2,229): viable (SYMBOLIC) when the chain stays
  in project code; library-inspector ends refuse safely.
- CTOR_ONLY equality (722): mechanism viable; real-world `equals(Object)` adds virtual
  dispatch and often `instanceof` (a degenerate type-check oracle) — count this bucket
  only after a real-`equals` probe.

## Caveats

- The wrappers are pure dataflow by construction; real corpus chains hit library
  boundaries at unknown rates. The realized share needs the `actual_shape` +
  `receiver_provenance` telemetry on a rerun, as the topology spike already states.
- `equalsPair` is monomorphic; a real `equals(Object)` may concretize at dispatch or
  degenerate to `instanceof`.
- All input sites are `int`; String sites compose with the string sound set instead.

## Verdict

**Write the R1 spec.** 8/9 shapes produce sound included properties through unmodified
extraction, generation, and licensing; the ninth refuses typed. The spec's required
content, confirmed by this spike: capture at the wrapper exit instead of the tested-method
exit, `ReturnTypeFilter` gating on the expression type, `GeneralizableInput.derive` walking
the whole actual expression — and no new soundness machinery, because the widening license
already covers the degenerate class.
