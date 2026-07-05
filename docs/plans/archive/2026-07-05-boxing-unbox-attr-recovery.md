---
title: Boxing Round-Trip Attr Recovery — Keep Symbolic Attrs Across Unbox
type: spec
status: abandoned
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
archived: 2026-07-05
---

# Boxing Round-Trip Attr Recovery — Keep Symbolic Attrs Across Unbox

**One concern (refuted):** the premise was that the `valueOf` peers preserve a symbolic
argument on the box's `value` field but the unbox accessors drop it, leaving a boxed-then-
unboxed symbolic primitive concrete, and that this explained the 678 `Long.valueOf`
NULL_CONCRETE-int sentinel refusals.

The mechanism audit disproved both halves. Unboxing is plain bytecode (`longValue()` is a
GETFIELD on `value`; there is no unbox peer to mirror), core GETFIELD propagates the field
attr to the operand stack, and the narrowing conversions preserve operand attrs. An in-process
JPF run of the exact suspected chain — symbolic `long` through `Long.valueOf`, `longValue()`
or `intValue()`, a narrowing cast, and a primitive return — captures a SYMBOLIC oracle while
still counting the `Long.valueOf` concretization event. The attr survives the round trip
today; there is nothing to recover.

The 678 sentinel rows are two JadConfig methods (`Duration.compareTo`, `Size.compareTo`),
both `return Long.valueOf(count).compareTo(other.count)`. Their `int` is a branch-selected
constant chosen inside `Long.compareTo` by a symbolic comparison that lands in the path
condition, so `NULL_CONCRETE` is the correct classification and no SPF change converts them.
Licensing branch-selected constant oracles is a separate, unscheduled research question.
Evidence and triage: `2026-07-05-concretization-census-findings`, Finding 1.
