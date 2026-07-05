---
title: Sound Character Predicates — ASCII Interval Model for isWhitespace
type: spec
status: draft
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
---

# Sound Character Predicates — ASCII Interval Model for isWhitespace

**One concern:** `Character.isWhitespace(char)` concretizes its symbolic argument at the native
boundary, so a tested method that branches or returns on it loses its symbolic evidence and
refuses widening. The census ranked it the top genuine bounded native-peer gap. Model the ASCII
subset soundly as interval constraints on the symbolic char; leave full Unicode to the parked
research list.

## Why now

- The concretization census (`2026-07-05-concretization-census-findings`, Finding 3) named
  `Character.isWhitespace(char)` the top of the bounded bucket (4 sole-blocker conversions on
  the five-project sentinel; more expected at corpus scale).
- The mechanism differs from the shipped `String.isEmpty` sound op. `isEmpty` is an
  INVOKEVIRTUAL on a symbolic String, handled inside `SymbolicStringHandler` as a string
  equality. `isWhitespace` is an INVOKESTATIC over a symbolic char (an integer expression) with
  a jpf-core native peer (`JPF_java_lang_Character.isWhitespace__C__Z`), so no existing handler
  sees it, and its full definition is Unicode general-category membership — a table over
  thousands of code points, research-grade to encode exactly. The sound, tractable scope is the
  ASCII subset, where the truth set is ten code points: 9–13 and 28–32.

## Design

Constraint collection follows the concrete path, so the model only has to record a partition
that is truth-constant for the branch the concrete seed took. That makes intervals sufficient:
no disjunction support is needed anywhere downstream.

1. **Interception seam.** Intercept `Character.isWhitespace(C)Z` in the symbolic INVOKESTATIC
   path (`BytecodeUtils.execute`, beside the existing `SymbolicStringHandler` hook) when the
   argument carries a symbolic expression attr. Interception happens before native-peer
   dispatch, so `EXECUTENATIVE` never executes and no concretization event fires — the same
   reason the sound string ops are event-free.
2. **Interval pinning.** Evaluate the predicate on the concrete argument, then add to the path
   condition the contiguous constant-truth interval containing that argument:
   TRUE → `[9,13]` or `[28,32]`; FALSE → `[0,8]`, `[14,27]`, or `[33,127]`. Two linear
   constraints per capture (`c >= lo` and `c <= hi`), which the existing SPF-to-Model
   transformer, Model, and generators already handle. The pinned interval is narrower than the
   full truth set (the other disjunct's width is sacrificed), but every char inside it takes
   the same branch, so widening stays path-exact. The full truth set would need
   `LogicalORLinearIntegerConstraints`, which the SPF-to-Model transformer has no mapping for;
   interval pinning avoids that machinery entirely.
3. **Result value.** Push the concrete boolean result without a return attr. A branch consumer
   (`if (isWhitespace(c))`) then branches concretely with the partition already recorded; a
   return consumer yields a `NULL_CONCRETE` boolean oracle with zero events and a path
   condition naming the char parameter, which the widening license already licenses.
4. **Non-ASCII fallback.** A concrete argument above 127 is not intercepted: execution falls
   through to the native peer, the concretization event fires, and the license refuses as it
   does today. This keeps the unmodeled shape on its current sound, observable path with no new
   exclusion machinery.
5. **Adjacent predicates.** `Character.isDigit` (TRUE run `[48,57]`) and `Character.isLetter`
   (TRUE runs `[65,90]`, `[97,122]`) fit the identical ASCII interval pattern. Include them
   only if the census tail observed them; otherwise record them as trivially reachable
   follow-ups.

## Acceptance

- A symbolic char through `Character.isWhitespace` in constraint collection records the
  containing constant-truth interval in the path condition, fires no concretization event, and
  produces the concrete boolean along the concrete branch.
- SPF/listener tests cover both consumer shapes (branch and boolean return) and both truth
  values, plus a non-ASCII concrete argument that falls through to the peer and still counts a
  concretization event.
- New fixture: a boolean-returning tested method that branches on `Character.isWhitespace`
  whose generalization widens (previously refused on the concretization event), golden pinning
  the conversion.
- Refusal-to-licensed conversion measured on the sentinel subset (expected on the order of the
  4 recorded), reported in the findings audit.

## Non-goals

- Full-Unicode `isWhitespace` (general-category membership) — parked research-grade.
- Multi-character or locale-dependent character operations.
- `String.matches` (regex) and `String.hashCode` (symbolic string content), recorded
  research-grade by the census.
- `String.lastIndexOf`/`charAt`/`substring` bounded-index string ops (deferred to the string
  plan; recorded medium in the census).
- Disjunction (`LogicalORLinearIntegerConstraints`) support in the SPF-to-Model transformer.
