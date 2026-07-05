---
title: Boxing Round-Trip Attr Recovery — Keep Symbolic Attrs Across Unbox
type: spec
status: draft
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
---

# Boxing Round-Trip Attr Recovery — Keep Symbolic Attrs Across Unbox

**One concern:** the `valueOf` peers preserve a symbolic argument on the boxed object's `value` field, but the matching unbox methods (`intValue`, `longValue`, and peers) drop it at the native boundary, so a symbolic primitive that is boxed and then unboxed comes back concrete. The census found the boxed-then-unboxed round trip is why 678 sentinel refusals capture a `NULL_CONCRETE` `int` oracle instead of a `SYMBOLIC` one. Recover the attr on the unbox side so the round trip is transparent.

## Why now

- The concretization census (`2026-07-05-concretization-census-findings`, Finding 1) showed `Long.valueOf(J)` is the largest concretization source by raw count but is incidental to the license verdict: its refusals come from a concrete non-boolean oracle, not the event count. The upstream cause is the lost symbolic attr. Recovering it upgrades those oracles from `NULL_CONCRETE` to `SYMBOLIC`, which the license always widens.
- The forward half already exists and is the template: `JPF_java_lang_Long.valueOf__J__Ljava_lang_Long_2` (commit `b5e3b06`) allocates a fresh box and attaches the argument expression via `env.addFieldAttr(result, "value", attrs[0])`. The unbox peer is the mirror: read that field attr back onto the returned primitive.

## Design

1. **Unbox peers.** Add symbolic peers for the unbox accessors that mirror the `valueOf` peers: `Long.longValue`, `Integer.intValue`, `Boolean.booleanValue`, and the others in the shipped `valueOf` set. Each reads the box's `value` field attr with `env.getFieldAttr(objRef, "value")`; if present, it returns the concrete primitive with that expression set as the return attr; if absent, it defers to the core peer (unchanged concrete behavior). Confirm the exact unbox site behind the 678 (explicit `longValue`/`intValue` call versus an autobox-then-unbox bytecode pattern) before fixing, and cover whichever the pipeline actually emits.
2. **Scope to the shipped box types.** Only the types that already have symbolic `valueOf` peers (`Integer`, `Long`, `Boolean`, and any others in that set). Do not introduce new box modeling; this closes the return half of the existing forward peers.

## Acceptance

- Unbox peers added mirroring the shipped `valueOf` peers, reading the `value` field attr and setting it as the return attr when present.
- Listener/SPF test: a symbolic `long` boxed via `Long.valueOf` and unboxed via `longValue` returns a value carrying the original argument expression (not a fresh free variable, not concrete).
- New fixture: a tested method returning a primitive derived through a box round trip whose captured oracle is `SYMBOLIC` (previously `NULL_CONCRETE`), with the golden pinning the upgrade.
- Refusal-to-licensed conversion measured on the sentinel subset for the `Long.valueOf` NULL_CONCRETE-int class, reported in the findings audit.

## Non-goals

- Box types without an existing symbolic `valueOf` peer.
- The concretization-event counter semantics (the event still fires at the native boundary; this spec changes the captured oracle, which is what the license consults for non-EXCEPTION shapes).
- The exception-message license refinement (separate spec).
