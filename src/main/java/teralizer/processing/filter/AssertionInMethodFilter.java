package teralizer.processing.filter;

import java.util.List;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtExecutableReference;
import teralizer.processing.task.TestAnalysisTask;
import teralizer.spoon.analysis.TestAnalysis;

public class AssertionInMethodFilter extends AbstractFilter {

    private final Launcher launcher;
    private final TestRecord testRecord;

    public AssertionInMethodFilter(Launcher spoonLauncher, TestRecord testRecord) {
        this.launcher = spoonLauncher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() throws Exception {
        CtMethod<?> testMethod = TestAnalysisTask.resolveTestMethod(this.launcher.getFactory(), this.testRecord);

        List<CtInvocation<?>> methodCalls = testMethod.getElements(ctInvocation -> !TestAnalysis.isAssertion(ctInvocation));
        for (CtInvocation<?> methodCall : methodCalls) {
            CtExecutableReference<?> reference = methodCall.getExecutable();
            CtExecutable<?> executable = reference.getDeclaration();

            if (executable != null) {
                if (TestAnalysis.containsAssertion(executable)) {
                    return new FilterResult(this.getName(), FilterDecision.DEFER, "Test contains assertion fixture", FilterReasonCodes.ASSERTION_IN_METHOD);
                }
            }
        }

        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }
}
