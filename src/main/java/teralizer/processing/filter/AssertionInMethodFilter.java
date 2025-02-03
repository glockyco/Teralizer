package teralizer.processing.filter;

import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import teralizer.spoon.analysis.TestAnalysis;

import java.util.List;

public class AssertionInMethodFilter extends AbstractFilter {

    private final Launcher launcher;
    private final TestRecord testRecord;

    public AssertionInMethodFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.launcher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        CtClass<?> testClass = this.launcher.getFactory().Class().get(this.testRecord.getTestClassQualifiedName());
        CtMethod<?> testMethod = testClass.getMethod(this.testRecord.getTestMethodName());

        List<CtInvocation<?>> methodCalls = testMethod.getElements(ctInvocation -> !TestAnalysis.isAssertion(ctInvocation));
        for (CtInvocation<?> methodCall : methodCalls) {
            CtExecutableReference<?> executable = methodCall.getExecutable();
            CtMethod<?> method = (CtMethod<?>) executable.getDeclaration();

            if (method != null) {
                if (TestAnalysis.containsAssertion(method)) {
                    return new FilterResult(this.getName(), FilterDecision.DEFER, "Test contains assertion fixture");
                }
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
