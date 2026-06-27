---
title: Receiver Constructor Inputs
type: plan
status: implemented
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
archived: 2026-06-27
---

Close the remaining JARVIS `Interval.getSize()` front-end gap discovered by the pinned scoreboard run.

## Goal

Promote constructor arguments from a tested receiver expression such as `new Interval(lower, upper).getSize()` into generated jqwik parameters, so no-argument instance methods whose receiver was constructed inline can enter specification collection.

## Acceptance criteria

- `IntervalTest::getSize` is no longer rejected by `ParameterTypeFilter` for having no method parameters when the receiver is an inline constructor with generalizable arguments.
- Generated tests use constructor-argument parameters to rebuild the receiver before invoking the no-argument instance method.
- The JARVIS scoreboard run records PVC for `Interval.getSize()` in `postgres_jarvis_scoreboard` using the scratch fixture.
- Existing object-construction argument behavior for constructor calls inside ordinary method arguments remains unchanged.

## Tasks

- [x] Add a failing receiver-constructor input test around `new Interval(1.0, 10.0).getSize()`.
- [x] Extend analysis so inline receiver constructor arguments become `GeneralizableInput` records.
- [x] Extend generation so receiver constructor parameters rebuild the receiver expression.
- [x] Re-run focused Java tests and the Math scoreboard config against scratch DB/data.
- [x] Update `2026-06-26-jarvis-case-coverage` with the new Interval result.
