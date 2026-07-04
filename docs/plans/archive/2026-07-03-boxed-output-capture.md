---
title: Boxed-Primitive Output Capture
type: spec
status: implemented
created: 2026-07-03
parent: 2026-06-26-teralizer-overview
archived: 2026-07-04
---

# Boxed-Primitive Output Capture

**One concern:** a tested method returning a boxed primitive (`Long`, `Integer`, `Boolean`, …) loses its symbolic return attribute at the boxing conversion, so the extraction records no output model (`NULL_CONCRETE`) even when the underlying primitive is fully symbolic; capture the attribute from the box's `value` field so these extractions become SYMBOLIC.

## Evidence

716 of 850 validated generalizations on the definitive spike are `NULL_CONCRETE`; the octotron/JadConfig widened-failure clusters (`GetLong()`, `GetBoolean()`, converter `convertTo` returning boxed types) are all boxed-return MUTs. Under `2026-07-03-widening-license` these lose their widening license; this spec is the completeness recovery — a boxed return whose primitive is symbolic should be a rule-1 (SYMBOLIC) license, not an exclusion.

## Mechanism

`TestGeneralizationListener.methodExited` captures the SPF return attribute at the tested-method frame exit. For a primitive return the attribute rides the operand-stack slot. For a boxed return the method returns an object reference: the symbolic expression lives (if anywhere) on the box object's `value` field as a field attr — `Long.valueOf(long)` allocates the box and stores the primitive (with its attr) into the field; the reference itself carries no attribute. The listener currently reads only the stack-slot attribute → `spfOutput = null` → `modelOutput` serialized as JSON `null` → `NULL_CONCRETE`.

## Design

In the listener's return-capture path: when the returned value is an object reference whose class is one of the eight boxed-primitive types, dereference the heap object (`ElementInfo`) and read the field attribute of its `value` field; if present, that expression is the symbolic output, exactly as if the primitive had been returned unboxed. The concrete captured value (already recorded via `CapturedOutput.ofReturnValue`) is unchanged.

Scope boundaries:
- Boxed primitives only. General heap-object output capture (field graphs, collections) is a different, larger problem — explicitly out of scope; it remains the "heap-PC capture" backlog item.
- No jpf-symbc (vendored fork) changes expected — the read is JPF listener-API surface (`ElementInfo.getFieldAttr`). If implementation reveals the attr genuinely does not survive `valueOf` in the vendored fork, that becomes a measured finding and a bounded upstream task, not an inline fork patch.
- Autoboxing caches (`Integer.valueOf` returns interned boxes for −128..127): the interned box is allocated once with a concrete field value — whether SPF's model propagates attrs through the cache path must be answered by a characterization fixture, not assumed. If attrs are lost on the cache path, the capture simply finds no attr and the extraction stays NULL_CONCRETE — degraded completeness, never unsoundness.

## Acceptance

- JPF listener tests (existing `JpfListenerHarness` + new target classes in `src/test/java/teralizer/jpf/targets/`): a boxed-`Long`/`Integer`/`Boolean`-returning MUT whose primitive is symbolic yields a SYMBOLIC output model equal to the unboxed equivalent's; a boxed return of a genuinely concrete value stays non-symbolic; existing primitive/String capture tests unchanged.
- Spike re-run (shared with the widening-license verification): `output_spec_class` distribution shifts NULL_CONCRETE → SYMBOLIC for boxed-return MUTs; octotron/JadConfig boxed-return generalizations regain licenses and validate; no regression in previously-SYMBOLIC extractions.
