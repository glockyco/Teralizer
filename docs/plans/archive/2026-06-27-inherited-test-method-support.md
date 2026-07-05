---
title: Inherited Test-Method Support
type: spec
status: implemented
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
archived: 2026-07-05
---

# Inherited Test-Method Support

When a JUnit test class inherits `@Test` methods from a parent class, the JUnit
report lists those methods under the child class, but Spoon's model only contains
them in the parent's AST. Teralizer crashes at `COLLECT_JUNIT_REPORTS_ORIGINAL`
and, if that crash is bypassed, every downstream Spoon-based stage breaks for the
same reason: the test method is not in the child class's declared-method set.

## Scope and priority

- **Affected:** 5,758 test cases across 52 projects (of 1,161). 51 of those 52
  projects also have successfully-collected tests; 1 has only inherited-method
  failures.
- **Nature:** adds more tests to projects that already partially work — it does
  not unlock new projects or new pipeline stages.
- **Priority:** ranked by the overview queue. This spec addresses 5,758 tests whose
  assertion count is unknown (they were dropped before assertion analysis). At the corpus
  median of 2 assertions/test, the projected reach is ~11,500 assertions — a rough
  projection, not a measured count.

## Evidence (DB-grounded, `postgres_test`, 2026-06-27; mechanism re-verified 2026-07-05)

All 52 projects have `EXECUTE_TESTS_ORIGINAL = SUCCEEDED` — tests ran fine; the
parser is the problem. The crash is in `JunitDataCollectionTask.updateTestRecord`
(the `getMethodsByName` guard — locate by symbol, not line):

```java
List<CtMethod<?>> matchingMethods =
    testClass.getMethodsByName(this.testRecord.getTestMethodName());
if (matchingMethods.isEmpty()) {
    throw new RuntimeException(
        "No method matches for test method (might be inherited): "
        + this.testRecord.getTestMethodQualifiedName());
}
```

`getMethodsByName` only searches the class's **declared** methods, not inherited
ones. The failure is test-level (not project-level): `AbstractTask.execute` catches
the throw, stores the test row with `is_included = false` and the free-text
exclusion message, and the pipeline also records a `task_diagnostic` row for the
failed task. The project continues with its other tests. So the drop is recorded
but untyped: the exclusion is a raw exception string, not a stable code, and the
test is unrecoverable even when the inherited method is perfectly resolvable.

### Downstream breakage chain (if step 9 is fixed naively)

Every subsequent Spoon-based stage uses the same declared-methods-only pattern:

1. **`TestAnalysisTask` (step 13):** resolves the test method via
   `CtPath.evaluateOn(testClass)` with path
   `#method[signature=testName()]`. `evaluateOn` searches the class's own AST;
   inherited methods are not present → empty result → `.get(0)` throws
   `IndexOutOfBoundsException`.

2. **`TestGeneralizationTask` (step 27):** `SpoonUtils.cloneClass` does
   `originalClass.clone()` — the clone preserves the `extends` clause but only
   copies declared methods. Then `testMethodPath.evaluateOn(generalizedClass)`
   fails for the same reason. `deleteOtherTestMethodsInClass` also operates on
   declared methods only.

3. **`JpfInstrumentationTask` (step 16):** uses both
   `instrumentedClass.getMethod(name)` (declared-only) and
   `testMethodPath.evaluateOn(instrumentedClass)` — both fail for inherited
   methods.

### Not a "modifying the parent affects all children" problem

The generalized class is a **clone with a new name**
(`_Foo_Generalized_testMethod_123_Test`). It does not modify the original class.
The problem is the opposite: the clone does not copy inherited methods into its
own AST, so all CtPath lookups and `getMethod`/`getMethodsByName` calls fail.

## Design

### Approach: flatten inheritance into the clone

The fix is to copy inherited test methods into the cloned class as declared
methods at clone time — "flattening" the inheritance. This keeps the downstream
CtPath and `getMethod` lookups working without changes, because the method is now
in the clone's own AST.

**Step 1 — Fix `JunitDataCollectionTask.updateTestRecord`:** replace
`getMethodsByName` with a search that walks the superclass chain:

```java
List<CtMethod<?>> matchingMethods = new ArrayList<>();
CtType<?> current = testClass;
while (current != null && matchingMethods.isEmpty()) {
    if (current instanceof CtClass<?>) {
        matchingMethods = ((CtClass<?>) current)
            .getMethodsByName(this.testRecord.getTestMethodName());
    }
    current = current.getSuperclass() != null
        ? current.getSuperclass().getDeclaration()
        : null;
}
```

**Step 2 — Store the declaring class:** the `test_method_qualified_name` and
`test_method_relative_path` columns must reference the class that actually
declares the method (the parent), not the child reported by JUnit. This is needed
so downstream `CtPath.evaluateOn` resolves against the right class. The
`test_class_qualified_name` stays as the child (it's the JUnit-reported class).
Consequence: JUnit report matching must keep using the CHILD class plus method
name (reports list inherited methods under the child), and every consumer that
derives a class from `test_method_qualified_name` (`AssertionInMethodFilter`,
`JpfInstrumentationTask`) must resolve against the declaring class — one shared
resolution helper, not per-site reimplementations.

**Step 3 — Flatten in `SpoonUtils.cloneClass`:** after cloning, walk the
superclass chain and copy any `@Test`-annotated methods that are not already
declared in the clone into the clone as declared methods. This makes
`evaluateOn(generalizedClass)` and `getMethod(name)` find the inherited method
in the clone's own AST. Field and helper-method references to the parent class
are preserved via the `extends` clause (the clone still extends the parent).

**Step 3b — Merge the declaring compilation unit's imports:** a flattened method
body carries references that its OWN compilation unit imported (`org.junit.Test`,
`org.junit.Before`, static `assertEquals`); the generated instrumented and
generalized class writers emit imports from the CHILD's compilation unit only,
so the flattened code does not compile without merging the declaring class's
imports (deduplicated) into the generated class. Both writers need it.

**Step 4 — Verify `JpfInstrumentationTask` works:** this task also clones the
class (`createInstrumentedClass` calls `SpoonUtils.cloneClass`). The flattening
in step 3 covers it, but the tested-method resolution
(`testedMethodPath.evaluateOn(factory.getModel().getRootPackage())` — locate by
symbol) operates on
the full model, not a clone — inherited tested methods (the focal method under
test, not the test method) already resolve correctly because they live in their
declaring class in the model.

### Flattening screens (sound subset only)

Copying a parent method body into the clone is only sound when the copied AST compiles and
behaves identically in the child context. Two screens gate the flatten, decided at
collection time (the screens need only the Spoon model, which
`JunitDataCollectionTask` already holds). A method failing either screen is excluded
cleanly instead of crashing: the test row stores `is_included = false` with
`exclusion_info` carrying the stable label `INHERITED_METHOD_NOT_FLATTENABLE` and the
failing screen named in the detail — a normal exclusion, not a caught RuntimeException,
so no `task_diagnostic` failure row and no free-text stack trace. Never silently
dropped, never flattened broken.

- **Type-variable screen.** The dominant real-world shape is a generic base
  (`class FooTest extends AbstractBaseTest<Foo>`). A parent method whose body or signature
  references a type parameter of the declaring class cannot be copied verbatim into a clone
  that declares no such parameter. v1 flattens only methods with no unresolved
  `CtTypeParameterReference` after considering the child's `extends` substitution;
  performing the substitution during the copy is a possible v2, not v1 scope.
- **Accessibility screen.** A parent method body referencing `private` members of the
  declaring class will not compile from the child. v1 flattens only methods whose
  referenced parent members are accessible from the child (`protected`, `public`, or
  package-visible within the same package).

The consequence is honest and measurable: the 5,758 affected tests will NOT all convert.
The flattenable share is unknown until measured — generic bases are expected to be a
substantial fraction — and the typed exclusion makes the split a direct query.

### Edge cases

- **Overridden methods:** if the child overrides the parent's `@Test` method,
  `getMethodsByName` already finds the override (it's declared in the child).
  The superclass walk must stop at the first match.
- **Diamond inheritance:** if multiple ancestors declare the same method name,
  the first match (closest ancestor) wins, matching Java's method resolution.
- **Private/protected methods:** handled by the accessibility screen above. `@Test`
  methods are public by convention; it is their *helper references* that carry the
  accessibility risk.
- **`@Before`/`@After` setup methods:** these can also be inherited. The
  flattening must include setup methods annotated with `@Before`/`@BeforeEach`/
  `@After`/`@AfterEach`/`@BeforeClass`/`@BeforeAll`/`@AfterClass`/`@AfterAll`,
  not just `@Test` methods — otherwise the generalized test compiles but lacks
  its setup. `JpfInstrumentationTask.getBeforeMethods` (locate by symbol) uses
  `testClass.getMethodsAnnotatedWith` which also only searches declared
  methods.

### What does NOT need changing

- `TestAnalysis.findTestedMethodCall` (step 13 MUT identification): works on
  the test method body's AST — pure source-level, class-agnostic. Once the
  method is resolvable via CtPath, this works unchanged.
- `MissingValueFilter`, `ParameterTypeFilter`, etc.: operate on assertion rows,
  not on the test class structure. Unaffected.
- PIT execution: runs on the compiled test suite, not on Spoon models.
  Unaffected.

## Acceptance criteria

- [x] `JunitDataCollectionTask` resolves inherited `@Test` methods by walking
  the superclass chain; the fixture's inherited tests are collected (flattenable arm)
  or excluded cleanly (`is_included = false`, `exclusion_info` =
  `INHERITED_METHOD_NOT_FLATTENABLE:TYPE_VARIABLE` observed for the generic arm). The
  throw remains only for methods genuinely absent from the model.
- [x] `test_method_qualified_name` and `test_method_relative_path` reference the
  declaring (parent) class for inherited methods; report matching keeps the child
  class, and the shared `resolveTestMethod` helper serves every downstream consumer.
- [x] `SpoonUtils.cloneClass` flattens screened-in inherited `@Test` and lifecycle
  methods; both screens have unit tests (generic base excluded typed, private-helper
  reference excluded typed, protected-helper base flattened), and the generated-class
  writers merge the declaring unit's imports so flattened code compiles.
- [x] `TestAnalysisTask`, `TestGeneralizationTask`, and `JpfInstrumentationTask`
  resolve flattened test methods, including inherited `@Before` setup invoked by the
  instrumented driver.
- [x] Fixture `inherited-tests` (16th): the flattenable arm generalizes and widens —
  observed golden `SYMBOLIC FULL 100/100 included`; the generic arm produces no
  generalization rows (gen_count pins the absence) and its typed exclusion is asserted
  against the DB. The wave gate passed with all 15 pre-existing goldens byte-identical.
- [ ] Corpus-scale measurement (flattenable share of the 5,758, assertion reach) batches
  into the next scheduled corpus evaluation event per the measurement policy in AGENTS.md.

## Out of scope

- **Tested methods (focal methods under test) that are inherited:** this is a
  different problem — the tested method lives in a parent class, and its
  source/parameters need resolving from the parent. The current
  `TestAnalysisTask` already resolves tested methods via
  `testedMethodCall.getExecutable().getDeclaration()`, which follows
  references across classes. This spec does not change that.
- **Pre-analysis infrastructure failures** (SETUP_PROJECT, BUILD_PROJECT_ORIGINAL,
  EXECUTE_TESTS_ORIGINAL timeouts): these are separate concerns documented in
  the capability audit's pipeline failure landscape.
