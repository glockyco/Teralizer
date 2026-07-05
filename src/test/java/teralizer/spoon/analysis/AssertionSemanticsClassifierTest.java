package teralizer.spoon.analysis;

import java.nio.file.Paths;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class AssertionSemanticsClassifierTest {

    @Example
    void classifiesCoreJunitAssertionKinds() {
        CtMethod<?> method = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.*;\n"
                + "public class SubjectTest {\n"
                + "  public void t(Object value, Object other, int[] a, int[] b) {\n"
                + "    assertEquals(1, 1);\n"
                + "    assertTrue(value != null);\n"
                + "    assertFalse(false);\n"
                + "    assertNotNull(value);\n"
                + "    assertNull(other);\n"
                + "    assertNotEquals(value, other);\n"
                + "    assertSame(value, other);\n"
                + "    assertArrayEquals(a, b);\n"
                + "  }\n"
                + "}\n"
        );

        Assert.assertEquals(AssertionSemanticCodes.EQUALITY, semantics(method, 0).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.BOOLEAN_TRUE, semantics(method, 1).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.BOOLEAN_FALSE, semantics(method, 2).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.NULLNESS_NOT_NULL, semantics(method, 3).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.NULLNESS_NULL, semantics(method, 4).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.INEQUALITY, semantics(method, 5).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.SAMENESS, semantics(method, 6).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.ARRAY_EQUALITY, semantics(method, 7).semanticKind());
    }

    @Example
    void classifiesFailByControlFlowContext() {
        CtMethod<?> method = testMethodFromSource(
            "package smoke;\n"
                + "import static org.junit.Assert.fail;\n"
                + "public class SubjectTest {\n"
                + "  public void t(boolean guard) {\n"
                + "    try { fail(\"expected\"); } catch (RuntimeException expected) { }\n"
                + "    try { throw new RuntimeException(); } catch (RuntimeException unexpected) { fail(\"bad\"); }\n"
                + "    if (guard) { fail(\"guard\"); }\n"
                + "  }\n"
                + "}\n"
        );

        Assert.assertEquals(AssertionSemanticCodes.FAIL_SENTINEL, semantics(method, 0).semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.FAIL_CONTEXT_TRY_BLOCK_EXPECTING_EXCEPTION, semantics(method, 0).failContext());
        Assert.assertEquals(AssertionSemanticCodes.FAIL_CONTEXT_CATCH_BLOCK_SHOULD_NOT_REACH, semantics(method, 1).failContext());
        Assert.assertEquals(AssertionSemanticCodes.FAIL_CONTEXT_GUARD_BRANCH, semantics(method, 2).failContext());
    }

    @Example
    void recordsMatcherFamilyAndName() {
        CtMethod<?> method = testMethodFromSource(
            "package smoke;\n"
                + "public class SubjectTest {\n"
                + "  public void t(Object value) {\n"
                + "    org.junit.Assert.assertThat(value, org.hamcrest.CoreMatchers.notNullValue());\n"
                + "  }\n"
                + "}\n"
        );

        AssertionSemanticsClassifier.Result result = semantics(method, 0);

        Assert.assertEquals(AssertionSemanticCodes.HAMCREST_MATCHER, result.semanticKind());
        Assert.assertEquals(AssertionSemanticCodes.MATCHER_FAMILY_HAMCREST, result.matcherFamily());
        Assert.assertEquals("notNullValue", result.matcherName());
    }


    private static AssertionSemanticsClassifier.Result semantics(CtMethod<?> method, int index) {
        return AssertionSemanticsClassifier.classify(TestAnalysis.findAllAsserts(method).get(index));
    }

    private static CtMethod<?> testMethodFromSource(String source) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source, Paths.get(System.getProperty("user.dir"), "SubjectTest.java").toString()));
        launcher.buildModel();
        CtModel model = launcher.getModel();
        CtClass<?> testClass = model.getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest")).get(0);
        return testClass.getMethodsByName("t").get(0);
    }
}
