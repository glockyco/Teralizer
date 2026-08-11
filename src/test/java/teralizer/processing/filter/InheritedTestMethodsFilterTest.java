package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.TestRecord;
import org.junit.Assert;
import spoon.Launcher;
import spoon.support.compiler.VirtualFile;

public class InheritedTestMethodsFilterTest {

    @Example
    void rejectsATestThatInheritsATestMethod() {
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "class BaseTest { @Test public void testContract() { } } "
                + "class SubjectTest extends BaseTest { @Test public void testOwn() { } }",
            "sample.SubjectTest");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.INHERITED_TEST_METHODS, result.getReasonCode());
        Assert.assertTrue(result.getReason().contains("testContract"));
    }

    @Example
    void rejectsATestThatInheritsATestMethodFromAHigherSuperclass() {
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "class RootTest { @Test public void testContract() { } } "
                + "class BaseTest extends RootTest { } "
                + "class SubjectTest extends BaseTest { @Test public void testOwn() { } }",
            "sample.SubjectTest");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void acceptsATestWhoseSuperclassHasOnlyHelperMethods() {
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "class BaseTest { protected int helper() { return 1; } } "
                + "class SubjectTest extends BaseTest { @Test public void testOwn() { } }",
            "sample.SubjectTest");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsATestWhoseSuperclassDeclaresTheTestMethodAbstract() {
        // An abstract method has no body. The runner does not run it, thus it cannot fail.
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "abstract class BaseTest { @Test public abstract void testContract(); } "
                + "class SubjectTest extends BaseTest { "
                + "@Test public void testContract() { } }",
            "sample.SubjectTest");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsATestWithNoSuperclass() {
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "class SubjectTest { @Test public void testOwn() { } }",
            "sample.SubjectTest");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsATestClassThatTheModelDoesNotContain() {
        FilterResult result = check(
            "package sample; import org.junit.Test; "
                + "class SubjectTest { @Test public void testOwn() { } }",
            "sample.AbsentTest");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    private static FilterResult check(String source, String testClassQualifiedName) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source));
        launcher.buildModel();
        TestRecord record = new TestRecord();
        record.setTestClassQualifiedName(testClassQualifiedName);
        return new InheritedTestMethodsFilter(launcher, record).check();
    }
}
