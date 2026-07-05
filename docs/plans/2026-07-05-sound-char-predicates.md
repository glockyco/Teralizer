---
title: Sound Character Predicates — Model isWhitespace and Adjacent char Tests
type: spec
status: draft
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
---

# Sound Character Predicates — Model isWhitespace and Adjacent char Tests

**One concern:** `Character.isWhitespace(char)` and adjacent character predicates concretize their symbolic argument at the native boundary, so a tested method that branches or returns on such a predicate loses its symbolic evidence and refuses widening. The census ranked `Character.isWhitespace(char)` the top genuine bounded native-peer gap. Model it soundly, following the shipped `String.isEmpty` precedent.

## Why now

- The concretization census (`2026-07-05-concretization-census-findings`, Finding 3) named `Character.isWhitespace(char)` the top of the bounded bucket (4 sole-blocker conversions on the five-project sentinel; more expected at corpus scale). It is a pure character predicate returning boolean, the same shape as the already-shipped sound `String.isEmpty` model (`2026-06-30-partial-sound-string-support` Task 6).
- The sound-modeling machinery and the typed unsupported-op signaling already exist from the string support work, so this is an addition to an established pattern, not new infrastructure.

## Design

1. **Sound predicate model.** Model `Character.isWhitespace(char)` as a sound symbolic boolean operation: the result is a symbolic boolean constrained to equal the whitespace membership test of the symbolic character argument, so the path condition records the branch and downstream widening keeps the oracle coherent. Mirror the structure of the shipped `isEmpty`/`equalsIgnoreCase` sound ops, including typed signaling for any argument shape that cannot be modeled.
2. **Scope.** `Character.isWhitespace(char)` first, since the census ranks it. If the same modeling trivially covers immediately adjacent single-char predicates already observed in the census tail (`Character.isDigit`, `Character.isLetter`), include only those that fit the identical sound pattern with no extra machinery; otherwise leave them for a follow-up and record them. Do not model predicates that need multi-character or locale state.
3. **Interaction with the census levers.** Independent of the license and boxing specs; a char predicate feeding a boolean-returning tested method is the `NULL_CONCRETE` boolean path the license already licenses once the concretization is gone.

## Acceptance

- `Character.isWhitespace(char)` modeled as a sound symbolic boolean op with typed signaling for unmodelable argument shapes; no concretization event for the modeled path.
- SPF/listener test: a symbolic char through `isWhitespace` records a path-condition clause and leaves the boolean result symbolic.
- New fixture: a boolean-returning tested method that branches on `Character.isWhitespace` whose generalization widens (previously refused on the concretization event), golden pinning the conversion.
- Refusal-to-licensed conversion measured on the sentinel subset (expected on the order of the 4 recorded), reported in the findings audit.

## Non-goals

- Multi-character or locale-dependent character operations.
- `String.matches` (regex) and `String.hashCode` (symbolic string content), which the census records as research-grade and explicitly does not schedule.
- `String.lastIndexOf`/`charAt`/`substring` bounded-index string ops (deferred to the string plan; recorded medium in the census).
