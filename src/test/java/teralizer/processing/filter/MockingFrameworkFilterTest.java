package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.TestRecord;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

public class MockingFrameworkFilterTest {

    @Example
    void rejectsDirectMockitoCalls() {
        FilterResult result = check(
            "package sample; import static org.mockito.Mockito.mock; "
                + "class SubjectTest { void testUsesMock() { Object value = mock(Object.class); } }",
            "testUsesMock");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
        Assert.assertEquals(FilterReasonCodes.UNSUPPORTED_MOCKING, result.getReasonCode());
    }

    @Example
    void rejectsAccessToMockitoAnnotatedFields() {
        FilterResult result = check(
            "package sample; import org.mockito.Mock; "
                + "class SubjectTest { @Mock Object dependency; "
                + "void testUsesMock() { System.out.println(dependency); } }",
            "testUsesMock");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void rejectsAccessToFieldsInitializedWithMocks() {
        FilterResult result = check(
            "package sample; import static org.easymock.EasyMock.createMock; "
                + "class SubjectTest { Object dependency = createMock(Object.class); "
                + "void testUsesMock() { System.out.println(dependency); } }",
            "testUsesMock");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void acceptsAnUnusedMockingImport() {
        FilterResult result = check(
            "package sample; import static org.mockito.Mockito.mock; "
                + "class SubjectTest { void testWithoutMock() { int value = 1; } }",
            "testWithoutMock");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsAMethodWhenOnlyAnotherMethodUsesMocking() {
        FilterResult result = check(
            "package sample; import static org.mockito.Mockito.mock; "
                + "class SubjectTest { void helper() { mock(Object.class); } "
                + "void testWithoutMock() { int value = 1; } }",
            "testWithoutMock");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void rejectsMockCallsFromWildcardImports() {
        FilterResult result = check(
            "package sample; import static org.mockito.Mockito.*; "
                + "class SubjectTest { void testUsesMock() { Object value = mock(Object.class); } }",
            "testUsesMock");

        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void acceptsUnrelatedCallsAlongsideWildcardImports() {
        FilterResult result = check(
            "package sample; import static org.mockito.Mockito.*; "
                + "class SubjectTest { void testWithoutMock() { String.valueOf(1); } }",
            "testWithoutMock");

        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    private static FilterResult check(String source, String methodName) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource(new VirtualFile(source));
        launcher.buildModel();
        CtType<?> type = launcher.getFactory().Type().get("sample.SubjectTest");
        CtMethod<?> method = type.getMethodsByName(methodName).get(0);
        TestRecord record = new TestRecord();
        record.setTestMethodAbsolutePath(method.getPath().toString());
        record.setTestMethodQualifiedName(type.getQualifiedName() + "." + methodName);
        return new MockingFrameworkFilter(launcher, record).check();
    }
}
