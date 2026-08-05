package teralizer.processing.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jooq.generated.tables.records.TestRecord;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtImportKind;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import teralizer.spoon.analysis.TestMethodResolver;

/**
 * Rejects test methods with a direct dependency on a mocking framework that JPF cannot execute.
 *
 * <p>The check is intentionally method-scoped. A mocking-framework import or an unrelated test in
 * the same class is not enough to reject a test. The method is rejected only when its body names a
 * framework type or accesses a field whose declaration, annotation, or initializer names one.
 * Mocking hidden behind an arbitrary helper remains a runtime diagnostic rather than a speculative
 * filter rejection.
 */
public class MockingFrameworkFilter extends AbstractFilter {

    private static final List<String> MOCKING_PACKAGE_PREFIXES = Arrays.asList(
        "org.mockito.",
        "org.powermock.",
        "org.easymock.",
        "org.jmock.",
        "mockit."
    );
    private static final Set<String> MOCKING_STATIC_METHODS = new HashSet<>(Arrays.asList(
        "mock",
        "mockStatic",
        "mockConstruction",
        "spy",
        "when",
        "whenNew",
        "verify",
        "verifyNoMoreInteractions",
        "verifyNoInteractions",
        "expect",
        "expectLastCall",
        "replay",
        "reset",
        "clearInvocations",
        "inOrder",
        "times",
        "never",
        "atLeast",
        "atLeastOnce",
        "atMost",
        "timeout",
        "after",
        "withSettings",
        "lenient",
        "doReturn",
        "doThrow",
        "doAnswer",
        "doNothing",
        "doCallRealMethod",
        "createMock",
        "createNiceMock",
        "createStrictMock",
        "suppress",
        "replace"
    ));

    private final Launcher launcher;
    private final TestRecord testRecord;

    public MockingFrameworkFilter(Launcher launcher, TestRecord testRecord) {
        this.launcher = launcher;
        this.testRecord = testRecord;
    }

    @Override
    public FilterResult check() {
        CtMethod<?> testMethod = TestMethodResolver.resolve(this.launcher.getFactory(), this.testRecord);
        if (!usesMockingFramework(testMethod)) {
            return new FilterResult(this.getName(), FilterDecision.ACCEPT);
        }
        return new FilterResult(
            this.getName(),
            FilterDecision.REJECT,
            "Test method depends directly on a mocking framework that JPF cannot execute faithfully.",
            FilterReasonCodes.UNSUPPORTED_MOCKING
        );
    }

    private static boolean usesMockingFramework(CtMethod<?> testMethod) {
        if (containsMockingType(testMethod)) {
            return true;
        }
        for (CtFieldAccess<?> fieldAccess : testMethod.getElements(new TypeFilter<>(CtFieldAccess.class))) {
            CtVariable<?> declaration = fieldAccess.getVariable().getDeclaration();
            if (declaration != null && containsMockingType(declaration)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMockingType(CtElement element) {
        for (CtTypeReference<?> type : element.getElements(new TypeFilter<>(CtTypeReference.class))) {
            if (isMockingType(type)) {
                return true;
            }
        }
        for (CtInvocation<?> invocation : element.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (declaringType != null && isMockingType(declaringType)) {
                return true;
            }
            if (hasMatchingMockingStaticImport(element, invocation.getExecutable().getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMatchingMockingStaticImport(CtElement element, String invokedName) {
        if (!element.getPosition().isValidPosition()) {
            return false;
        }
        for (CtImport imported : element.getPosition().getCompilationUnit().getImports()) {
            CtImportKind kind = imported.getImportKind();
            if (kind != CtImportKind.METHOD && kind != CtImportKind.ALL_STATIC_MEMBERS && kind != CtImportKind.UNRESOLVED) {
                continue;
            }
            String reference = imported.getReference() == null
                ? imported.toString()
                : imported.getReference().toString();
            reference = reference.replace("import static ", "").replace(";", "").trim();
            if (!isMockingReference(reference)) {
                continue;
            }
            String importedName = imported.getReference() == null
                ? reference.substring(reference.lastIndexOf('.') + 1).replace("()", "")
                : imported.getReference().getSimpleName();
            if (kind == CtImportKind.ALL_STATIC_MEMBERS || "*".equals(importedName)) {
                if (isMockingStaticMethod(invokedName)) {
                    return true;
                }
                continue;
            }
            if (invokedName.equals(importedName)
                || reference.endsWith("." + invokedName)
                || reference.endsWith("." + invokedName + "()")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMockingStaticMethod(String methodName) {
        return MOCKING_STATIC_METHODS.contains(methodName)
            || methodName.startsWith("any")
            || methodName.startsWith("isNull")
            || methodName.startsWith("notNull")
            || methodName.startsWith("argThat");
    }

    private static boolean isMockingReference(String reference) {
        return MOCKING_PACKAGE_PREFIXES.stream().anyMatch(reference::startsWith);
    }

    private static boolean isMockingType(CtTypeReference<?> type) {
        String qualifiedName = type.getQualifiedName();
        return MOCKING_PACKAGE_PREFIXES.stream().anyMatch(qualifiedName::startsWith);
    }
}
