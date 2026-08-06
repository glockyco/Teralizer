package teralizer.spoon.analysis;

import java.util.List;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class MethodUnderTestResolverTest {

    // --- Task 3: characterization of current behavior, now graded ---

    @Example
    void directInvocationInActualPosition_isT1Proven() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, new Subject().gcd(6, 9)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.RESOLVED, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.DIRECT_ACTUAL_CALL, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void oneHopLocalVariable_isT1Proven() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.LOCAL_VARIABLE_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void assertThrowsSingleInvocation_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, () -> new Subject().gcd(0, 0)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void junit4AssertThrowsDegradesToNoPick() {
        // JUnit 4.13 declares assertThrows but the resolver only extracts a JUnit 5 lambda body,
        // so this shape must reach the no-pick outcome rather than an unchecked argument read.
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(
            "public class SubjectTest {\n"
                + "  public void t() { org.junit.Assert.assertThrows(RuntimeException.class, new Object()); }\n"
                + "}\n",
            "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);

        MutResolution resolution = MethodUnderTestResolver.resolve(testMethod, assertion, new FocalTypeResolver());

        Assert.assertEquals(MutResolution.Status.NONE, resolution.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, resolution.getNoPickReason());
    }

    @Example
    void malformedAssertThrowsDegradesToNoPick() {
        MutResolution resolution = resolve(
            "public class SubjectTest {\n"
                + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class); }\n"
                + "}");

        Assert.assertEquals(MutResolution.Status.NONE, resolution.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, resolution.getNoPickReason());
    }

    @Example
    void assertThrowsMultipleInvocations_picksLast_gradedGuess() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
        // last-call position decided; no identity indicator computed yet in Task 3 => T4 base
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertTrue(r.getTier() == MutResolution.Tier.T4_GUESS
            || r.getTier() == MutResolution.Tier.T3_SINGLE_WEAK
            || r.getTier() == MutResolution.Tier.T2_CORROBORATED);
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        Assert.assertEquals("helper", r.getAlternatives().get(0).methodName);
    }

    @Example
    void tryFailCatchSingleInvocationBeforeFail_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() {\n"
            + "    try { new Subject().gcd(0, 0); org.junit.Assert.fail(); }\n"
            + "    catch (IllegalArgumentException e) { }\n"
            + "  }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void tryFailCatchMultipleInvocationsBeforeFail_picksLast_gradedGuess() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() {\n"
            + "    try { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); org.junit.Assert.fail(); }\n"
            + "    catch (IllegalArgumentException e) { }\n"
            + "  }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
        Assert.assertEquals(MutResolution.Signal.ASSERT_THROWS_LAMBDA, r.getDecidingSignal());
        Assert.assertTrue(r.getTier() == MutResolution.Tier.T4_GUESS
            || r.getTier() == MutResolution.Tier.T3_SINGLE_WEAK
            || r.getTier() == MutResolution.Tier.T2_CORROBORATED);
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        Assert.assertEquals("helper", r.getAlternatives().get(0).methodName);
    }

    @Example
    void unsupportedShape_isNoneT5() {
        // assertNotNull has no actual-parameter index => unsupported shape
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertNotNull(new Subject().gcd(6, 9)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T5_NONE, r.getTier());
        Assert.assertNull(r.getPick());
        Assert.assertEquals(MutResolution.NoPickReason.UNSUPPORTED_ASSERTION_SHAPE, r.getNoPickReason());
    }

    @Example
    void hamcrestAssertThatResolvesProducerFromActualArgument() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertThat(new Subject().gcd(6, 9), org.hamcrest.CoreMatchers.is(3)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.RESOLVED, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.DIRECT_ACTUAL_CALL, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void libraryPick_isCharacterizationOnly() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(3, Integer.parseInt(\"3\")); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.CHARACTERIZATION_ONLY, r.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.LIBRARY_DECLARATION, r.getNoPickReason());
        Assert.assertEquals("parseInt", r.getPick().getExecutable().getSimpleName());
    }


    @Example
    void transitiveVariableCopy_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int a = new Subject().gcd(6, 9); int b = a; org.junit.Assert.assertEquals(3, b); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void reassignedVariable_nearestWriteWins_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().helper(1); x = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void killedDefinition_yieldsNoProducerFromVariable() {
        // int x = gcd(...); x = 5; assert(x) -- the literal write kills the call definition.
        // (Pre-fusion code wrongly returned gcd here; contract divergence (a).)
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); x = 5; org.junit.Assert.assertEquals(5, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotEquals("gcd",
            r.getPick() == null ? null : r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void fieldWriteProducer_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  int r;\n"
            + "  public void t() { this.r = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, this.r); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.FIELD_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void qualifiedThisFieldReadProducer_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  int r;\n"
            + "  public void t() { this.r = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, SubjectTest.this.r); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.FIELD_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }


    @Example
    void enclosingThisFieldReadProducer_isT1() {
        CtModel model = modelOf(
            "public class SubjectTest {\n"
            + "  int r;\n"
            + "  class InnerTest {\n"
            + "    public void t() { SubjectTest.this.r = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(3, SubjectTest.this.r); }\n"
            + "  }\n"
            + "}",
            SUBJECT_SOURCE);
        MutResolution r = resolveFrom(model, "SubjectTest$InnerTest", "t", 0);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.FIELD_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void shadowedFieldWriteDoesNotProduceEnclosingFieldRead() {
        CtModel model = modelOf(
            "public class SubjectTest {\n"
            + "  int r;\n"
            + "  class InnerTest {\n"
            + "    int r;\n"
            + "    public void t() { this.r = new Subject().gcd(6, 9); org.junit.Assert.assertEquals(0, SubjectTest.this.r); }\n"
            + "  }\n"
            + "}",
            SUBJECT_SOURCE);
        MutResolution r = resolveFrom(model, "SubjectTest$InnerTest", "t", 0);
        Assert.assertNotEquals(MutResolution.Signal.FIELD_PRODUCER, r.getDecidingSignal());
    }
    @Example
    void writeInsideNestedBlock_isUnproven_T3orBetter() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t(boolean c) { int x = 0; if (c) { x = new Subject().gcd(6, 9); } org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
        Assert.assertNotEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }

    @Example
    void comparisonOperand_isT1() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().gcd(6, 9) > 0); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertEquals(MutResolution.Signal.SUBEXPRESSION_PRODUCER, r.getDecidingSignal());
        Assert.assertEquals("gcd", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void twoProducerComposite_isRankedNotAbstained() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertEquals(5, new Subject().gcd(6, 9) + new Subject().helper(2)); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotNull(r.getPick());
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        Assert.assertNotEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }

    @Example
    void inspectorOnComputedReceiver_unwrapsToProducer() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.Assert.assertTrue(new Subject().compute(5).isEmpty()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("compute", r.getPick().getExecutable().getSimpleName());
        Assert.assertEquals(MutResolution.Signal.INSPECTOR_UNWRAP, r.getDecidingSignal());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
        Assert.assertTrue(r.isInspectorUnwrapped());
        Assert.assertFalse(r.isShallowInspectorPick());
    }

    @Example
    void inspectorOnVariableWithProducer_unwrapsThroughVariable() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { java.util.List<Integer> l = new Subject().compute(5); org.junit.Assert.assertTrue(l.isEmpty()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("compute", r.getPick().getExecutable().getSimpleName());
        Assert.assertTrue(r.isInspectorUnwrapped());
    }

    @Example
    void inspectorWithUnreachableReceiver_keptButFlaggedShallow() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  Subject sut = new Subject();\n"
            + "  public void t() { org.junit.Assert.assertEquals(0, sut.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("getTotal", r.getPick().getExecutable().getSimpleName());
        Assert.assertTrue(r.isShallowInspectorPick());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier()); // dataflow-true, shallow-flagged
    }

    @Example
    void focalClass_nameDerived_corroboratesMembership() {
        // Weak position pick inside assertThrows multi-call: Subject membership + no name match => T3.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals("Subject", r.getFocalType());
        Assert.assertEquals(MutResolution.FocalSource.NAME_ONLY, r.getFocalSource());
        Assert.assertEquals(Boolean.TRUE, r.getFocalAgreement());
        Assert.assertTrue(r.getCorroborators().contains(MutResolution.Corroborator.FOCAL_CLASS_MEMBER));
        Assert.assertEquals(MutResolution.Tier.T3_SINGLE_WEAK, r.getTier());
    }

    @Example
    void nameAndFocalAgreement_promoteToT2() {
        // Test method named tGcd -> name-match on gcd; + focal membership => 2 indicators => T2.
        String source =
            "public class SubjectTest {\n"
            + "  public void testGcd() { org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,\n"
            + "    () -> { Subject s = new Subject(); s.helper(1); s.gcd(0, 0); }); }\n"
            + "}";
        MutResolution r = resolveNamed(source, "testGcd", SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Tier.T2_CORROBORATED, r.getTier());
    }


    @Example
    void uniqueProductionCallInSlice_isT1Elimination() {
        // Mutator-then-inspect: process() is the only production call; getTotal() is the asserted
        // inspector with unreachable receiver-producer -- elimination picks process.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        // getTotal is a shallow inspector; the slice holds exactly one other production call.
        Assert.assertEquals("process", r.getPick().getExecutable().getSimpleName());
        Assert.assertEquals(MutResolution.Signal.UNIQUE_PRODUCER_ELIMINATION, r.getDecidingSignal());
        Assert.assertEquals(MutResolution.Tier.T1_PROVEN, r.getTier());
    }

    @Example
    void multipleFeasibleCandidates_rankedGuessWithAlternatives() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { Subject s = new Subject(); s.process(5); s.helper(2); org.junit.Assert.assertEquals(5, s.getTotal()); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertNotNull(r.getPick());
        Assert.assertEquals(MutResolution.Signal.RANKED_GUESS, r.getDecidingSignal());
        Assert.assertEquals(2, r.getCandidateCount());
        Assert.assertEquals(1, r.getAlternatives().size());
        // helper(int->int) is type-eligible, process(int->void) is not => ranking prefers helper.
        Assert.assertEquals("helper", r.getPick().getExecutable().getSimpleName());
    }

    @Example
    void killedDefinition_notResurrectedBySliceElimination() {
        // Dataflow refuted gcd (the write of 5 kills it); elimination must NOT bring it back.
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = new Subject().gcd(6, 9); x = 5; org.junit.Assert.assertEquals(5, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.NoPickReason.NO_VISIBLE_CALL, r.getNoPickReason());
    }

    @Example
    void noCallsAtAll_isT5None() {
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() { int x = 1 + 2; org.junit.Assert.assertEquals(3, x); }\n"
            + "}",
            SUBJECT_SOURCE);
        Assert.assertEquals(MutResolution.Status.NONE, r.getStatus());
        Assert.assertEquals(MutResolution.Tier.T5_NONE, r.getTier());
        Assert.assertEquals(MutResolution.NoPickReason.NO_VISIBLE_CALL, r.getNoPickReason());
    }


    private static CtModel modelOf(String... sources) {
        Launcher launcher = new Launcher();
        for (int i = 0; i < sources.length; i++) {
            launcher.addInputResource(new VirtualFile(sources[i], "Source" + i + ".java"));
        }
        launcher.buildModel();
        return launcher.getModel();
    }

    private static MutResolution resolveFrom(
        CtModel model,
        String testClassName,
        String testMethodName,
        int assertionIndex
    ) {
        CtClass<?> testClass = model.getElements(CtClass.class::isInstance).stream()
            .map(CtClass.class::cast)
            .filter(type -> testClassName.equals(type.getQualifiedName()))
            .findFirst()
            .get();
        CtMethod<?> testMethod = testClass.getMethodsByName(testMethodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(assertionIndex);
        return MethodUnderTestResolver.resolve(testMethod, assertion, new FocalTypeResolver());
    }
    // --- shared helpers (used by all tasks) ---

    public static final String SUBJECT_SOURCE =
        "public class Subject {\n"
        + "  public int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }\n"
        + "  public int helper(int a) { return a; }\n"
        + "  public boolean isPrime(int n) { return n > 1; }\n"
        + "  public java.util.List<Integer> compute(int x) { return new java.util.ArrayList<>(); }\n"
        + "  public int getTotal() { return 0; }\n"
        + "  public void process(int x) { }\n"
        + "}";

    @Example
    void explicitConstructorInvocation_neverPicked() {
        // A local class whose constructor calls super() puts a CtInvocation with a
        // CONSTRUCTOR executable into the test method body. Picking it would blow up
        // TestAnalysisTask's CtMethod cast (spike regression: JadConfig, kouchat, ...).
        MutResolution r = resolve(
            "public class SubjectTest {\n"
            + "  public void t() {\n"
            + "    class Local extends Subject { Local() { super(); } }\n"
            + "    Local l = new Local();\n"
            + "    org.junit.Assert.assertEquals(0, l.getTotal());\n"
            + "  }\n"
            + "}",
            SUBJECT_SOURCE);
        if (r.getPick() != null) {
            Assert.assertTrue("pick must never be a constructor executable",
                r.getPick().getExecutable().getDeclaration() == null
                    || r.getPick().getExecutable().getDeclaration() instanceof CtMethod<?>);
            Assert.assertFalse("pick must not be super()/this()",
                r.getPick().getExecutable().isConstructor());
        }
        for (MutResolution.Candidate candidate : r.getAlternatives()) {
            Assert.assertFalse("<init> must not appear among candidates",
                "<init>".equals(candidate.methodName) || "Local".equals(candidate.methodName));
        }
    }

    public static MutResolution resolve(String testSource, String... otherSources) {
        return resolveNth(testSource, 0, otherSources);
    }

    static MutResolution resolveNamed(String testSource, String testMethodName, String... otherSources) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        for (int i = 0; i < otherSources.length; i++) {
            launcher.addInputResource(new VirtualFile(otherSources[i], "Other" + i + ".java"));
        }
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName(testMethodName).get(0);
        CtInvocation<?> assertion = TestAnalysis.findAllAsserts(testMethod).get(0);
        return MethodUnderTestResolver.resolve(testMethod, assertion, new FocalTypeResolver());
    }

    public static MutResolution resolveNth(String testSource, int assertionIndex, String... otherSources) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(testSource, "SubjectTest.java"));
        for (int i = 0; i < otherSources.length; i++) {
            launcher.addInputResource(new VirtualFile(otherSources[i], "Other" + i + ".java"));
        }
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        List<CtInvocation<?>> asserts = TestAnalysis.findAllAsserts(testMethod);
        CtInvocation<?> assertion = asserts.isEmpty() ? null : asserts.get(assertionIndex);
        return MethodUnderTestResolver.resolve(testMethod, assertion, new FocalTypeResolver());
    }
}
