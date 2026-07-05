---
title: Collect-Mode Conformance Survey of the Vendored SPF
type: audit
status: active
created: 2026-07-05
parent: 2026-06-26-teralizer-overview
---

# Collect-Mode Conformance — where the fork still forks

**One concern:** a full survey of every `PCChoiceGenerator` creation site in the vendored
jpf-symbc (125 sites), classifying whether constraint-collection mode degenerates it to the
seed's single concrete path. Collect mode is Teralizer's core contract: one path, constraints
recorded along it, never explored. Two site families violate it today, two more are latent
landmines behind config flags, and the rest are conformant.

## Method

Programmatic inventory of `new PCChoiceGenerator(` across `jpf-symbc/src/main` (125 sites),
then a manual read of one representative per family covering both the creation guard
(`collect_constraints ? 1 : n`) and the select-time guard (concrete-outcome `select(...)`
versus `getNextChoice()`). Line-level grep misclassifies here: several files guard at the
branch level or in the select logic, and one flagged line is commented-out code.

## Findings

### S1 — string parse family forks and can crash the host (live, high)

`parseInt`, `parseLong`, `parseFloat`, `parseDouble`, `parseBoolean`
(`SymbolicStringHandler:241-281`) and the string-to-box `valueOf` conversions
(`:1367-1419`) create hardcoded `PCChoiceGenerator(2)` with no collect guard and no
symcrete select. Consequences in collect mode, verified by reading `handleParseInt`
(`:1625-1677`):

- JPF explores choice 0 first, and choice 0 is the FAILING partition
  (`conditionValue=false` → `NOTINTEGER`).
- The failing branch, when satisfiable, executes
  `throw new RuntimeException("ERROR: Integer Format Type Exception")` — a host-level
  exception that kills the search, not a modeled `NumberFormatException` the target
  could catch.

Every collect-mode run whose symbolic string reaches a parse call either crashes the search
or explores a sibling partition. This is the sharpened root cause behind the xenqtt
`AppContext` failures (`getArgAsInt`/`getArgAsBoolean` parse the flag string) that
`2026-07-03-symbolic-sibling-throws` attributed to predicate-level partition exploration.
The predicate level has been symcrete-pinned since the string-support wave; the parse level
never was.

### S2 — FCMPL/FCMPG corrupt the concrete comparison result (live when floats are symbolic)

`FCMPL.java:68` and `FCMPG.java:68` overwrite the concretely computed `conditionValue` with
`getNextChoice() - 1` BEFORE the collect-mode `select(...)`. On a collect-mode 1-choice CG,
`getNextChoice()` is always 0, so `conditionValue` becomes -1 regardless of the seed's
actual comparison. Two corruptions follow: the recorded constraint is the LT partition
whether or not the seed took it, and line 131 pushes the corrupted value back onto the
stack, so the SUBSEQUENT branch executes the wrong path. `DCMPL`, `DCMPG`, and `LCMP` are
the same copy-paste family with the overwrite correctly removed — the float pair diverged.
Reachable whenever a float-typed MUT runs under `symbolic.fp` selection.

### S3 — optimization package has zero collect awareness (latent)

`optimization/util/IFInstrSymbHelper` (14 sites) and `optimization/LCMP` fork
unconditionally. Dead under Teralizer because `jpf-config.vm` pins
`symbolic.optimizechoices=false`, but the factory default is TRUE
(`SymbolicInstructionFactory:730`), so any config drift silently re-enables exploring
branch code. No fail-fast exists.

### S4 — symbolic-arrays package has zero collect awareness (latent)

All 19 `symarrays/*` sites fork unconditionally. Dead under Teralizer
(`symbolic.arrays` unset). Same landmine shape as S3.

### Conformant families (verified, no action)

- Numeric branches `IF*`, `IF_ICMP*`: creation-guarded, symcrete select.
- `IFEQ`/`IFNE`: branch-level creation guard (1 vs omega-3 vs 2), symcrete select.
- `DCMPL`/`DCMPG`/`LCMP`: guarded, concrete conditionValue preserved.
- Switches (`TABLESWITCH`, `SwitchInstruction`): guarded both ends, concrete index select.
- Div/rem (`IDIV`/`IREM`/`LDIV`/`LREM`/`FDIV`/`DDIV`): guarded, concrete zero-check select.
- Core array loads/stores (`IALOAD` family): branch-level creation guard, concrete index
  select.
- Casts (`D2I` family): unconditional 1-choice CG — no fork by construction.
- String predicates (`equals`, `startsWith`, `endsWith`, `contains`, `isEmpty`,
  `equalsIgnoreCase`): creation-guarded, symcrete select since the string-support wave.
- `BytecodeUtils:236`: 1-choice CG behind preconditions/arrays flags, both off.

## Fix plan

- **F1 (S1):** extend the symcrete pattern to the parse family. Creation becomes
  `collect_constraints ? 1 : 2`. Select-time evaluates the seed's concrete parse outcome
  (read the concrete string, attempt the parse) and selects that branch. The success path
  adds `ISINTEGER`-style constraints plus the symbolic result as today. The failure path
  adds the negative constraint and throws a MODELED `NumberFormatException` via
  `createAndThrowException`, never a host `RuntimeException` — the seed itself threw, so
  the concrete path IS the exception path and the target's own catch/throw semantics apply.
  Same treatment for the five parse handlers and the four string-to-box valueOf forks.
- **F2 (S2):** delete the `getNextChoice()` overwrite in `FCMPL`/`FCMPG`, mirroring
  `DCMPL`/`DCMPG` exactly.
- **F3 (S3/S4):** fail-fast in `SymbolicInstructionFactory` init: collect mode combined
  with `pcChoiceOptimization` or `symbolic.arrays` refuses to start instead of silently
  forking.
- **F4:** two new verification arms pin the fixes: a float-compare MUT (S2 — currently no
  fixture exercises FCMP* symbolically) and a parse-reaching string MUT covering both the
  parse-success seed and the parse-failure seed (S1).

## Outcome (all four fixes landed)

F1 and F4b landed as jpf-symbc `cf4bfadc` + parent `a18d00cd`. F2, F3, and F4a landed as
jpf-symbc `d1768d50` + parent `d596f837`. Observed results, RED-first in every case:

- **S2 confirmed as a live soundness bug.** Before the fix the true-seed float compare
  captured the INVERTED comparator. After it, both float-compare fixture arms carry the
  constraint matching their seed's branch (`a > b` / `a < b`), licensed FULL 100/100,
  pinned in `verification/golden/float-compare.tsv`.
- **S1's crash class is gone.** Before the fix both parse-reaching harness targets killed
  the search with the host `RuntimeException` at `handleParseInt`. After it, the parse
  outcome follows the seed and both fixture arms land as typed `UNSUPPORTED_TERM`
  exclusions at ingestion, because `isinteger`/`notinteger` comparators are not in the
  admitted sound set. No gen rows, no crash, sibling assertions unaffected
  (`verification/golden/string-parse.tsv` pins the header-only shape).
- **Full recovery to included specs is a separate, census-gated decision:** admitting
  ISINTEGER/NOTINTEGER to the ingestion sound set (rendering as a parse-based predicate)
  belongs to the string-op-growth bucket the concretization census ranks. The crash fix
  stands on its own.
- The config guards refuse constraint collection combined with
  `symbolic.optimizechoices` or `symbolic.arrays` at factory init.

## Relationship to `2026-07-03-symbolic-sibling-throws` — retired

That draft's design (typed per-assertion outcome for sibling throws) targeted the symptom.
With S1 fixed there is no parse-level sibling partition in constraint collection, the
predicate level has been symcrete-pinned since the string-support wave, and every other
string site is creation-guarded per this survey. The residual sibling-throw class is empty
under the surveyed fork. The spec is superseded by this audit. The xenqtt `AppContext`
family now dies typed at ingestion instead of crashing the search, and its full recovery
rides the ISINTEGER admission decision above.
