package teralizer.processing.filter;

import org.jooq.generated.tables.records.AssertionRecord;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.path.CtPath;
import spoon.reflect.path.CtPathStringBuilder;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.domain.MethodCapabilities;

/**
 * Rejects an assertion whose method under test takes a {@link String} parameter and performs a
 * String operation SPF cannot symbolize soundly:
 *
 * <ul>
 *   <li>{@code charAt}/{@code substring} omit the out-of-bounds fork, so the collected spec is
 *       under-constrained (unsound) for an index the method could throw on;</li>
 *   <li>{@code compareTo} is not implemented in {@code SymbolicStringHandler} and aborts the run.</li>
 * </ul>
 *
 * <p>Direct-body-only and conservative, mirroring the raw-bits detection in
 * {@code SpfSymbolicConfigSelector}: a transitively-reached unsupported op is the run-time
 * exclusion's job. The gate on a String parameter avoids rejecting a MUT that uses these operations
 * only on concrete (non-symbolized) strings.
 */
public class StringOperationFilter extends AbstractFilter {

    private final Launcher launcher;
    private final AssertionRecord assertionRecord;

    public StringOperationFilter(Launcher spoonLauncher, AssertionRecord assertionRecord) {
        this.launcher = spoonLauncher;
        this.assertionRecord = assertionRecord;
    }

    @Override
    public FilterResult check() {
        String testedMethodPath = this.assertionRecord.getTestedMethodAbsolutePath();
        if (testedMethodPath == null || testedMethodPath.isEmpty()) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        CtElement testedMethod;
        try {
            CtPath path = new CtPathStringBuilder().fromString(testedMethodPath);
            CtModel model = this.launcher.getModel();
            testedMethod = path.evaluateOn(model.getRootPackage()).get(0);
        } catch (IndexOutOfBoundsException e) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        if (!(testedMethod instanceof CtExecutable) || !hasStringParameter((CtExecutable<?>) testedMethod)) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }

        for (CtInvocation<?> invocation : testedMethod.getElements(new TypeFilter<>(CtInvocation.class))) {
            if (isUnsupportedStringOperation(invocation)) {
                return new FilterResult(this.getName(), FilterDecision.REJECT,
                    "The method under test performs an unsound/unsupported symbolic String operation ("
                        + invocation.getExecutable().getSimpleName() + ").",
                    FilterReasonCodes.UNSUPPORTED_STRING_OPERATION);
            }
        }
        return new FilterResult(this.getName(), FilterDecision.ACCEPT);
    }

    private static boolean hasStringParameter(CtExecutable<?> method) {
        for (CtParameter<?> parameter : method.getParameters()) {
            if ("java.lang.String".equals(parameter.getType().getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnsupportedStringOperation(CtInvocation<?> invocation) {
        CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null) {
            return false;
        }
        CtTypeReference<?> declaringType = executable.getDeclaringType();
        return declaringType != null
            && "java.lang.String".equals(declaringType.getQualifiedName())
            && !MethodCapabilities.isSupported(executable.getSimpleName());
    }
}
