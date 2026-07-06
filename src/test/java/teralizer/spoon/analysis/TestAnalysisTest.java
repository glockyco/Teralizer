package teralizer.spoon.analysis;

import java.util.Optional;
import net.jqwik.api.Example;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.NamedElementFilter;
import spoon.support.compiler.VirtualFile;

public class TestAnalysisTest {

    @Example
    void hamcrestIsViewExposesActualAndExpected() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.is(1));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(1)", view.getActualExpression().toString());
        Assert.assertEquals("1", view.getExpectedExpression().toString());
    }

    @Example
    void hamcrestEqualToViewExposesActualAndExpected() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(2), org.hamcrest.CoreMatchers.equalTo(2));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(2)", view.getActualExpression().toString());
        Assert.assertEquals("2", view.getExpectedExpression().toString());
    }

    @Example
    void hamcrestThreeArgumentOverloadUsesSecondArgumentAsActual() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(\"reason\", new Subject().id(3), org.hamcrest.CoreMatchers.is(3));");

        Assert.assertEquals("new Subject().id(3)", view.getActualExpression().toString());
        Assert.assertEquals("3", view.getExpectedExpression().toString());
    }

    @Example
    void nestedHamcrestIsEqualToUsesInnerExpectedExpression() {
        TestAnalysis.NormalizedAssertion view = normalizedAssertion(
            "org.junit.Assert.assertThat(new Subject().id(4), org.hamcrest.CoreMatchers.is(org.hamcrest.CoreMatchers.equalTo(4)));");

        Assert.assertEquals(TestAnalysis.AssertionKind.EQUALITY, view.getKind());
        Assert.assertEquals("new Subject().id(4)", view.getActualExpression().toString());
        Assert.assertEquals("4", view.getExpectedExpression().toString());
    }

    @Example
    void nonEqualityHamcrestMatcherIsNotGeneralizable() {
        CtInvocation<?> assertion = assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.notNullValue());");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(assertion).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(assertion));
    }

    @Example
    void nonHamcrestIsMatcherIsNotGeneralizable() {
        CtInvocation<?> assertion = assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), SubjectTest.is(1));");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(assertion).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(assertion));
    }

    @Example
    void invocationAwareGateAdmitsOnlyEqualityIsomorphicAssertThat() {
        Assert.assertTrue(TestAnalysis.isGeneralizable(assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.is(1));")));
        Assert.assertTrue(TestAnalysis.isGeneralizable(assertion(
            "org.junit.Assert.assertThat(new Subject().id(1), org.hamcrest.CoreMatchers.equalTo(1));")));
        Assert.assertFalse(TestAnalysis.isGeneralizable("assertThat"));
    }

    @Example
    void tryFailCatchViewExposesCaughtExceptionType() {
        TestAnalysis.NormalizedAssertion view = normalizedFailAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "      org.junit.Assert.fail();\n"
                + "    } catch (IllegalArgumentException e) {\n"
                + "    }");

        Assert.assertEquals(TestAnalysis.AssertionKind.THROWS, view.getKind());
        Assert.assertNull(view.getActualExpression());
        Assert.assertEquals("java.lang.IllegalArgumentException", view.getExpectedExceptionTypeName());
    }

    @Example
    void tryFailCatchViewAllowsOnlyCaughtMessageEqualityAssertions() {
        TestAnalysis.NormalizedAssertion view = normalizedFailAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "      org.junit.Assert.fail();\n"
                + "    } catch (IllegalArgumentException e) {\n"
                + "      org.junit.Assert.assertEquals(\"bad input\", e.getMessage());\n"
                + "    }");

        Assert.assertEquals(TestAnalysis.AssertionKind.THROWS, view.getKind());
        Assert.assertEquals("java.lang.IllegalArgumentException", view.getExpectedExceptionTypeName());
    }

    @Example
    void multiCatchTryFailCatchIsNotGeneralizable() {
        CtInvocation<?> fail = failAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "      org.junit.Assert.fail();\n"
                + "    } catch (IllegalArgumentException | IllegalStateException e) {\n"
                + "    }");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(fail).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(fail));
    }

    @Example
    void failOutsideTryIsNotGeneralizable() {
        CtInvocation<?> fail = failAssertion("org.junit.Assert.fail();");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(fail).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(fail));
    }

    @Example
    void failInsideCatchIsNotGeneralizable() {
        CtInvocation<?> fail = failAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "    } catch (IllegalArgumentException e) {\n"
                + "      org.junit.Assert.fail();\n"
                + "    }");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(fail).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(fail));
    }

    @Example
    void tryFailCatchRejectsOtherCatchBodyLogic() {
        CtInvocation<?> fail = failAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "      org.junit.Assert.fail();\n"
                + "    } catch (IllegalArgumentException e) {\n"
                + "      new Subject().id(1);\n"
                + "    }");

        Assert.assertFalse(TestAnalysis.normalizedAssertion(fail).isPresent());
        Assert.assertFalse(TestAnalysis.isGeneralizable(fail));
    }

    @Example
    void invocationAwareGateAdmitsOnlyRecognizedTryFailCatch() {
        Assert.assertTrue(TestAnalysis.isGeneralizable(failAssertion(
            "try {\n"
                + "      new Subject().reject(1);\n"
                + "      org.junit.Assert.fail();\n"
                + "    } catch (IllegalArgumentException e) {\n"
                + "    }")));
        Assert.assertFalse(TestAnalysis.isGeneralizable("fail"));
    }

    private static TestAnalysis.NormalizedAssertion normalizedAssertion(String assertionSource) {
        Optional<TestAnalysis.NormalizedAssertion> view = TestAnalysis.normalizedAssertion(assertion(assertionSource));
        Assert.assertTrue("expected normalized assertion for " + assertionSource, view.isPresent());
        return view.get();
    }

    private static TestAnalysis.NormalizedAssertion normalizedFailAssertion(String methodBody) {
        Optional<TestAnalysis.NormalizedAssertion> view = TestAnalysis.normalizedAssertion(failAssertion(methodBody));
        Assert.assertTrue("expected normalized fail assertion for " + methodBody, view.isPresent());
        return view.get();
    }

    private static CtInvocation<?> failAssertion(String methodBody) {
        return assertionInMethod(methodBody, "fail");
    }

    private static CtInvocation<?> assertionInMethod(String methodBody, String assertionName) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(sourceForMethodBody(methodBody), "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        return TestAnalysis.findAllAsserts(testMethod).stream()
            .filter(assertion -> assertionName.equals(assertion.getExecutable().getSimpleName()))
            .findFirst()
            .get();
    }

    private static String sourceForMethodBody(String methodBody) {
        return "public class SubjectTest {\n"
            + "  static Object is(int value) { return value; }\n"
            + "  public void t() {\n"
            + methodBody
            + "\n  }\n"
            + "}\n"
            + "class Subject { int id(int x) { return x; } void reject(int x) { throw new IllegalArgumentException(\"bad input\"); } }\n";
    }

    private static CtInvocation<?> assertion(String assertionSource) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source(assertionSource), "SubjectTest.java"));
        launcher.buildModel();
        CtClass<?> testClass = launcher.getModel()
            .getElements(new NamedElementFilter<>(CtClass.class, "SubjectTest"))
            .get(0);
        CtMethod<?> testMethod = testClass.getMethodsByName("t").get(0);
        return TestAnalysis.findAllAsserts(testMethod).get(0);
    }

    private static String source(String assertionSource) {
        return "public class SubjectTest {\n"
            + "  static Object is(int value) { return value; }\n"
            + "  public void t() { " + assertionSource + " }\n"
            + "}\n"
            + "class Subject { int id(int x) { return x; } }\n";
    }
}
