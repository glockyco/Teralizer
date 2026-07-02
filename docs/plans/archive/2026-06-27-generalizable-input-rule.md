---
title: Generalizable Input Rule
type: spec
status: implemented
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
archived: 2026-07-02
---

Formal rule for when a concrete JUnit value becomes a symbolic Teralizer input.

## Goal

Keep Teralizer's input surface explicit, reproducible, and sound: promote concrete values to generated jqwik parameters only when the pipeline can name them, symbolize them in JPF, recover their concrete values, and reconstruct the tested call without changing the tested method's declared API.

## Current rule

`GeneralizableInput.derive(testedMethod, testedMethodCall)` is the canonical decision procedure.

For each tested method call:

1. If the tested method is an instance method and the receiver expression is an inline `CtConstructorCall<?>`, derive inputs from the receiver constructor arguments using the synthetic method-argument index reserved for receiver constructors.
2. For each declared tested-method parameter and concrete call argument:
   - if the inferred argument type is supported by `TypeCapability.supportsGeneratedInput` (derived from the registered `DomainPlanner`s), keep it as a direct scalar input;
   - else if the concrete argument is an inline `CtConstructorCall<?>`, derive one input per supported constructor argument;
   - else leave the argument concrete and do not treat it as a generated input.
3. A constructor expression is generalizable only when every constructor argument has a supported scalar type. One unsupported constructor argument makes the whole constructor expression non-generalizable for this path.

The supported scalar set is derived from the registered `DomainPlanner`s via `TypeCapability`; constructor-derived inputs do not bypass that type gate.

## Naming contract

Constructor-derived parameters use stable synthetic names:

```text
_ctor_<owner>_<wordIndex>_<constructorParameterNameOrArgN>
```

- `<owner>` is the tested-method parameter name for argument constructors, or `receiver` for inline receiver constructors.
- `<wordIndex>` is `zero`, `one`, ..., `nine`, then `arg<N>` for larger indexes.
- `<constructorParameterNameOrArgN>` is the constructor parameter name when Spoon resolves it, otherwise `arg<N>`.

Examples:

- `Subject.contains(new Interval(1, 10), 5)` derives `_ctor_interval_zero_lower`, `_ctor_interval_one_upper`, and direct `value`.
- `new Interval(1.0, 10.0).getSize()` derives `_ctor_receiver_zero_lower` and `_ctor_receiver_one_upper`.

The names are part of the JPF variable contract: constraint extraction, concrete value logging, and generated jqwik code must agree on the same identifiers.

## Pipeline consumers

All consumers that decide, symbolize, record, or regenerate inputs must use the same derived input surface.

- `ParameterTypeFilter` accepts an assertion when at least one derived input has a generalizable type, checking via `TypeCapability.supportsGeneratedInput()`. `TestAnalysisTask` stores the unwrapped constructor inputs from `GeneralizableInput.derive(...)` into `tested_method_parameters` / `tested_method_call_arguments` when the tested-method declaration resolves, so the filter sees the flattened constructor parameters and accepts inline-constructor cases. The residual reject path is `testedMethod == null` (unresolvable declaration), where no `CtMethod` exists for `derive` to consult.
- `JpfInstrumentationTask` creates the instrumented method signature from derived inputs. Direct scalar inputs become method parameters; constructor-derived inputs replace constructor arguments when the instrumented call is made and rebuild the receiver or argument constructor inside the instrumented method body.
- `TestGeneralizationListener` must record the instrumented method's input values, because the original tested method frame sees reconstructed objects rather than flattened constructor values.
- `TestGeneralizationTask` must build `TestParameters` from the same derived inputs and rewrite generated tests so `_p_.<name>` is substituted into direct arguments or constructor arguments.
- Downstream generators consume the derived names without schema changes.

## Soundness boundary

Supported now:

- direct scalar arguments;
- fixed-arity inline constructors used as tested-method arguments;
- fixed-arity inline constructors used as instance-method receivers;
- mixed calls that combine direct scalar arguments with constructor-derived arguments;
- constructors whose supported scalar arguments are sufficient to deterministically reconstruct the receiver or argument object at the tested call boundary.

Out of scope for this rule:

- field, local-variable, fixture, factory, builder, setter, collection, or alias provenance for receiver/object state;
- nested constructor graphs;
- variable-length arrays, collections, varargs, and symbolic array lengths;
- mutable post-construction state;
- constructors with unsupported, unresolved, or non-scalar arguments;
- deriving a behavioral oracle independent of the implementation under test.

This boundary is intentional. Inline constructor flattening covers the JARVIS object rows and a small real-world bonus. The larger real-world mass is object state assembled outside the tested call (`@Before`, factories, builders, fields, aliases) and belongs to the stateful setup / interprocedural-state track, not to this rule.

## Extension surface

Future input expansion should extend the same decision procedure instead of adding parallel eligibility checks.

Promising extensions, in order of containment:

1. ~~Make `ParameterTypeFilter` consult the Spoon-backed derived input surface so the filter gate matches the instrumentation/generation capability.~~ → done: `TestAnalysisTask` stores the unwrapped constructor inputs, so the filter already sees generalizable primitive parameters and accepts inline-constructor cases.
2. Add array-literal construction when the array length is concrete and every element has a supported scalar type.
3. Add one-hop nested constructors only when every nested constructor is pure, fixed-arity, and fully scalar-derived.
4. Add receiver symbolization for local/field receivers only after a separate stateful-setup spec defines provenance, aliasing, mutation, and reconstruction rules.

Rejected shortcut: treating every object parameter as symbolic. SPF may propagate fields once symbolic values exist, but Teralizer still needs a precise source-to-generated-test reconstruction contract. Without that contract, generated tests can silently change the fixture state they claim to generalize.

## Acceptance criteria

- A fresh reader can decide whether a tested call has generalizable inputs without reading implementation history.
- `GeneralizableInput.derive()` remains the single source of truth for input eligibility.
- Filter, instrumentation, listener, and generator behavior are described against one shared input surface.
- The spec distinguishes inline constructor support from broader object-state support.
- Planning docs that mention object-construction inputs link here rather than restating a divergent rule.
