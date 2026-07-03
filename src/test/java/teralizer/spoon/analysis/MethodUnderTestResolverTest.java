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
    // --- shared helpers (used by all tasks) ---

    static final String SUBJECT_SOURCE =
        "public class Subject {\n"
        + "  public int gcd(int a, int b) { return b == 0 ? a : gcd(b, a % b); }\n"
        + "  public int helper(int a) { return a; }\n"
        + "  public boolean isPrime(int n) { return n > 1; }\n"
        + "  public java.util.List<Integer> compute(int x) { return new java.util.ArrayList<>(); }\n"
        + "  public int getTotal() { return 0; }\n"
        + "  public void process(int x) { }\n"
        + "}";

    static MutResolution resolve(String testSource, String... otherSources) {
        return resolveNth(testSource, 0, otherSources);
    }

    static MutResolution resolveNth(String testSource, int assertionIndex, String... otherSources) {
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
        return MethodUnderTestResolver.resolve(testMethod, assertion);
    }
}
