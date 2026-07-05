---
title: Exception-Message Widening — License Precision for Post-Decision Concretization
type: spec
status: implemented
created: 2026-07-05
parent: 2026-07-05-concretization-census-findings
archived: 2026-07-05
---

# Exception-Message Widening — License Precision for Post-Decision Concretization

**One concern:** the widening license refuses every `EXCEPTION`-oracle generalization whenever
`concretization_events > 0`, but concretization that cannot steer control flow between the
throw-determining branch and the throw cannot change throw reachability. The census found this
single over-refusal is the largest load-bearing blocker (74 generalizations in antiaction
alone), all from string concatenation in exception messages. Refine the license to widen when
the post-concretization region is provably divergence-free.

## Why now

- The concretization census (`2026-07-05-concretization-census-findings`, Finding 2) ranked
  this the top load-bearing blocker by a wide margin, and it is not a native-peer gap: the
  concretizing methods are `StringBuilder.<init>` and `StringBuilder.append(String)` building
  `throw new JSONException("... " + converterName)`. The message is evaluated after the
  throw-determining branch (`id == null`, a map-lookup path condition) and never affects
  reachability, and the `EXCEPTION` oracle checks only the exception type.
- The current license comment already states the exact soundness argument it is being
  conservative about: "Concretized branches break this path-uniqueness argument for throws
  exactly as they do for booleans, because control flow can diverge inside the concretized
  region without path-condition evidence." The refinement is to detect when no such
  divergence is possible.

## Design

The soundness argument for widening a THROWN oracle is that the throw's reachability is
pinned by the path condition. A concretization event threatens that only through two
divergence vectors, neither of which is a symbolic branch:

- **A concrete branch on concretized-lineage data.** After a native boundary drops symbolic
  attrs, later branches on that data execute concretely and leave no path-condition clause,
  so widened inputs can silently steer them. A *symbolic* branch after the event is not a
  threat: it registers a `PCChoiceGenerator` and leaves a clause that generated inputs must
  satisfy.
- **A native-origin throw.** When the captured exception is raised inside a native peer that
  received symbolic arguments (`Integer.parseInt` throwing `NumberFormatException`), throw
  reachability is decided by the concrete value at the boundary, with no path-condition
  evidence.

Both vectors are observable without taint tracking, at the cost of accepted conservatism:

1. **Telemetry (listener).** Extend `TestGeneralizationListener` with an ordering flag over
   the extraction window it already scopes (wrapper entry to capture). After the first
   concretization event, any conditional-branch instruction executing in application bytecode
   (non-JDK, non-modeled-library classes; exact predicate settled by the spike) whose operands
   carry no symbolic expression sets the flag. The listener already intercepts
   `executeInstruction` for `EXECUTENATIVE`; this adds branch-instruction inspection on the
   same hook. Separately record whether the captured exception's throw site is an `ATHROW` in
   application bytecode or a native boundary (the `exceptionThrown` hook sees the current
   instruction). Persist beside `concretization_events` as a nullable
   `post_concretization_divergence_risk` (true when either vector was observed). The
   conservatism: a post-event concrete application branch on data unrelated to the
   concretization also sets the flag. The spike measures whether the antiaction hotspot still
   converts under that conservatism (its post-event application code is straight-line to the
   throw, so it should).
2. **License.** In `WideningLicense.evaluate`, for `EXCEPTION` with
   `concretization_events > 0`, widen when `post_concretization_divergence_risk` is false and
   the existing path-name coverage condition holds (empty path condition, or every widened
   parameter named by the path condition). Keep refusing when the flag is true. The
   `NULL_CONCRETE` boolean path is out of scope and keeps its blanket refusal on events.
3. **Old rows.** A null flag (pre-telemetry rows) is treated as divergence-risk unknown, which
   refuses, matching today's behavior.
4. **Soundness spike first.** Before implementation, adversarial fixtures validate the
   argument: a target with a concrete branch on concretized data after the event (must
   refuse), a native-origin throw fed a symbolic argument (must refuse), and the antiaction
   shape (`throw new X("literal " + symbolicArg)` after a symbolic guard — must widen). If a
   sound counterexample survives the fixture set, the item converts to research-grade.

## Acceptance

- New listener telemetry: `post_concretization_divergence_risk` persisted per assertion;
  existing count column and its license role unchanged.
- Listener tests: straight-line post-event message construction records false; a concrete
  post-event branch in application code records true; a native-origin throw records true; a
  symbolic post-event branch alone records false.
- License unit tests: `EXCEPTION` + events + risk false + path coverage widens; `EXCEPTION` +
  events + risk true refuses; null risk refuses.
- New fixture reproducing the antiaction shape whose THROWN generalization widens, with the
  golden pinning the conversion.
- Refusal-to-licensed conversion on the antiaction hotspot leg (expected on the order of the
  74 recorded) batches into the next scheduled corpus evaluation event per the measurement
  policy in `AGENTS.md`, reported in the findings audit when that event runs.

## Non-goals

- The `NULL_CONCRETE` boolean over-refusal (separate shape, separate soundness argument).
- Concretization followed by a genuine divergence vector (correctly refused).
- Taint tracking of concretized-lineage data (the conservative branch flag stands in for it).
- Any change to native peers.
