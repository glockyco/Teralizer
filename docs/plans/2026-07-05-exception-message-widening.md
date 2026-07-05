---
title: Exception-Message Widening — License Precision for Post-Decision Concretization
type: spec
status: draft
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
---

# Exception-Message Widening — License Precision for Post-Decision Concretization

**One concern:** the widening license refuses every `EXCEPTION`-oracle generalization whenever `concretization_events > 0`, but concretization that happens after the last branch the throw depends on cannot change throw reachability. The census found this single over-refusal is the largest load-bearing blocker (74 generalizations in antiaction alone), all from string concatenation in exception messages. Refine the license to widen when no symbolic branch follows the concretization.

## Why now

- The concretization census (`2026-07-05-concretization-census-findings`, Finding 2) ranked this the top load-bearing blocker by a wide margin, and it is not a native-peer gap: the concretizing methods are `StringBuilder.<init>` and `StringBuilder.append(String)` building `throw new JSONException("... " + converterName)`. The message is evaluated after the throw-determining branch (`id == null`, a map-lookup path condition) and never affects reachability, and the `EXCEPTION` oracle checks only the exception type.
- The current license comment already states the exact soundness argument it is being conservative about: "Concretized branches break this path-uniqueness argument for throws exactly as they do for booleans, because control flow can diverge inside the concretized region without path-condition evidence." The refinement is to detect when no such divergence is possible.

## Design

The soundness argument for widening a THROWN oracle is that the throw's reachability is pinned by the path condition. A concretization event threatens that only if control flow could diverge on symbolic data after the event without leaving a path-condition clause. If no symbolic branch (no `PCChoiceGenerator` advance) occurs after the last concretization event within the tested method's execution, then everything after the concretization is straight-line with respect to symbolic data, and the throw outcome is fully determined by the pre-concretization path condition. Exception-message construction is exactly this shape: the concatenation runs, then the throw fires, with no intervening symbolic decision.

1. **Telemetry (listener).** Extend `TestGeneralizationListener` to record, per assertion, whether any symbolic branch occurred after the last concretization event during the tested method's frame. Persist a nullable boolean beside the existing `concretization_events` count (for example `branch_after_concretization`). The listener already observes both `EXECUTENATIVE` (concretization) and the `PCChoiceGenerator` choices, so this is an ordering flag over signals it already sees.
2. **License.** In `WideningLicense.evaluate`, for `EXCEPTION` with `concretization_events > 0`, widen when `branch_after_concretization` is false and the existing path-name coverage condition holds (empty path condition, or every widened parameter named by the path condition). Keep refusing when a branch followed the concretization, since divergence is then possible. The `NULL_CONCRETE` boolean path is out of scope here and keeps its current blanket refusal on events.
3. **Old rows.** A null flag (pre-telemetry rows) is treated as "branch after concretization unknown," which conservatively refuses, matching today's behavior.

## Acceptance

- New listener telemetry: `branch_after_concretization` persisted per assertion; existing count column and its license role unchanged.
- Listener test: a target that concretizes only in straight-line code after the last branch records `false`; a target that branches on symbolic data after a concretization records `true`.
- License unit tests: `EXCEPTION` + events + `branch_after_concretization = false` + path coverage widens; `EXCEPTION` + events + `branch_after_concretization = true` refuses.
- New fixture reproducing the antiaction shape (`throw new X("literal " + symbolicArg)` after a symbolic guard) whose THROWN generalization widens, with the golden pinning the conversion.
- Refusal-to-licensed conversion measured on the antiaction hotspot leg (expected on the order of the 74 recorded), reported in the findings audit.

## Non-goals

- The `NULL_CONCRETE` boolean over-refusal (separate shape, separate soundness argument).
- Concretization that precedes a symbolic branch (genuinely reachability-relevant, correctly refused).
- Any change to native peers.
