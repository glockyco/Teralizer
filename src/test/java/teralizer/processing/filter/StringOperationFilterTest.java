package teralizer.processing.filter;

import net.jqwik.api.Example;
import org.jooq.generated.tables.records.AssertionRecord;
import org.junit.Assert;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.support.compiler.VirtualFile;

public class StringOperationFilterTest {

    private static FilterResult check(String source, String methodName) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(source));
        launcher.buildModel();
        CtType<?> type = launcher.getModel().getAllTypes().iterator().next();
        CtMethod<?> method = type.getMethodsByName(methodName).get(0);

        AssertionRecord record = new AssertionRecord();
        record.setTestedMethodAbsolutePath(method.getPath().toString());
        return new StringOperationFilter(launcher, record).check();
    }

    @Example
    void rejectsCharAtOnAStringParameterMethod() {
        FilterResult result = check(
            "class Subject { public static char first(String s) { return s.charAt(0); } }", "first");
        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void rejectsSubstringOnAStringParameterMethod() {
        FilterResult result = check(
            "class Subject { public static String tail(String s) { return s.substring(1); } }", "tail");
        Assert.assertEquals(FilterDecision.REJECT, result.getDecision());
    }

    @Example
    void acceptsSupportedStringOperations() {
        FilterResult result = check(
            "class Subject { public static boolean flagged(String s) { return s.startsWith(\"a\") && s.contains(\"b\"); } }",
            "flagged");
        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }

    @Example
    void acceptsUnsupportedOperationWhenNoStringParameterIsSymbolic() {
        // substring/isEmpty are used only on a concrete literal, and there is no String parameter to
        // symbolize, so nothing is unsound; the filter must not over-reject.
        FilterResult result = check(
            "class Subject { public static boolean check(int n) { return \"abc\".substring(n).isEmpty(); } }",
            "check");
        Assert.assertEquals(FilterDecision.ACCEPT, result.getDecision());
    }
}
