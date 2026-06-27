---
title: Inherited Test-Method Support
type: spec
status: draft
created: 2026-06-27
parent: 2026-06-26-teralizer-overview
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
- **Priority:** below the mutation-based MUT-identification spec
  (`2026-06-27-mutation-based-mut-identification`). That spec addresses 58,122
  first-reject assertions across 21,081 tests; this spec addresses 5,758 tests
  whose assertion count is unknown (they were dropped before assertion
  analysis). At the corpus median of 2 assertions/test, the projected reach is
  ~11,500 assertions — a rough projection, not a measured count.

## Evidence (DB-grounded, `postgres_test`, 2026-06-27)

All 52 projects have `EXECUTE_TESTS_ORIGINAL = SUCCEEDED` — tests ran fine; the
parser is the problem. The crash is in `JunitDataCollectionTask.updateTestRecord`
(line 345):

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
ones. The failure is test-level (not project-level): the test record is never
created, so the test is silently dropped. The project continues with its other
tests.

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

**Step 3 — Flatten in `SpoonUtils.cloneClass`:** after cloning, walk the
superclass chain and copy any `@Test`-annotated methods that are not already
declared in the clone into the clone as declared methods. This makes
`evaluateOn(generalizedClass)` and `getMethod(name)` find the inherited method
in the clone's own AST. Field and helper-method references to the parent class
are preserved via the `extends` clause (the clone still extends the parent).

**Step 4 — Verify `JpfInstrumentationTask` works:** this task also clones the
class (`createInstrumentedClass` calls `SpoonUtils.cloneClass`). The flattening
in step 3 covers it, but the tested-method resolution at line 91–92
(`testedMethodPath.evaluateOn(factory.getModel().getRootPackage())`) operates on
the full model, not a clone — inherited tested methods (the focal method under
test, not the test method) already resolve correctly because they live in their
declaring class in the model.

### Edge cases

- **Overridden methods:** if the child overrides the parent's `@Test` method,
  `getMethodsByName` already finds the override (it's declared in the child).
  The superclass walk must stop at the first match.
- **Diamond inheritance:** if multiple ancestors declare the same method name,
  the first match (closest ancestor) wins, matching Java's method resolution.
- **Private/protected methods:** `@Test` methods are public by convention;
  private inherited test methods are a JUnit edge case that doesn't occur in
  practice.
- **`@Before`/`@After` setup methods:** these can also be inherited. The
  flattening must include setup methods annotated with `@Before`/`@BeforeEach`/
  `@After`/`@AfterEach`/`@BeforeClass`/`@BeforeAll`/`@AfterClass`/`@AfterAll`,
  not just `@Test` methods — otherwise the generalized test compiles but lacks
  its setup. `JpfInstrumentationTask.getBeforeMethods` (line 414) uses
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

- [ ] `JunitDataCollectionTask` resolves inherited `@Test` methods by walking
  the superclass chain; the 5,758 "No method matches" failures drop to zero.
- [ ] `test_method_qualified_name` and `test_method_relative_path` reference the
  declaring (parent) class for inherited methods.
- [ ] `SpoonUtils.cloneClass` flattens inherited `@Test` and lifecycle
  (`@Before`/`@After`/`@BeforeClass`/`@AfterClass`) methods into the clone.
- [ ] `TestAnalysisTask`, `TestGeneralizationTask`, and
  `JpfInstrumentationTask` all resolve inherited test methods without
  `IndexOutOfBoundsException` or "No method matches" errors.
- [ ] No regressions on `postgres_dev` (13 controlled projects).
- [ ] Re-run `applicability_priorities.py` after implementation to confirm the
  5,758 dropped tests are collected and their assertions reach analysis.
- [ ] At least one inherited-method test case generalizes and passes PIT on the
  generalized variant.

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
