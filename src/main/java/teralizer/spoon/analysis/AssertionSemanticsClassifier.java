package teralizer.spoon.analysis;

import java.util.List;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public final class AssertionSemanticsClassifier {

    private AssertionSemanticsClassifier() {
    }

    public static Result classify(CtInvocation<?> assertion) {
        String name = assertion.getExecutable().getSimpleName();
        String semanticKind = semanticKind(assertion, name);
        CtExpression<?> focus = focusArgument(assertion, semanticKind);
        String argumentShape = argumentShape(focus);
        String failContext = AssertionSemanticCodes.FAIL_SENTINEL.equals(semanticKind) ? failContext(assertion) : null;
        Matcher matcher = matcher(assertion, semanticKind);
        return new Result(semanticKind, argumentShape, failContext, matcher.family, matcher.name);
    }

    private static String semanticKind(CtInvocation<?> assertion, String name) {
        switch (name) {
            case "assertEquals":
                return AssertionSemanticCodes.EQUALITY;
            case "assertTrue":
                return AssertionSemanticCodes.BOOLEAN_TRUE;
            case "assertFalse":
                return AssertionSemanticCodes.BOOLEAN_FALSE;
            case "assertNotNull":
                return AssertionSemanticCodes.NULLNESS_NOT_NULL;
            case "assertNull":
                return AssertionSemanticCodes.NULLNESS_NULL;
            case "assertNotEquals":
                return AssertionSemanticCodes.INEQUALITY;
            case "assertSame":
            case "assertNotSame":
                return AssertionSemanticCodes.SAMENESS;
            case "assertArrayEquals":
                return AssertionSemanticCodes.ARRAY_EQUALITY;
            case "assertThat":
                return matcherAssertionKind(assertion);
            case "fail":
                return AssertionSemanticCodes.FAIL_SENTINEL;
            default:
                return AssertionSemanticCodes.UNKNOWN;
        }
    }

    private static String matcherAssertionKind(CtInvocation<?> assertion) {
        CtTypeReference<?> declaringType = assertion.getExecutable().getDeclaringType();
        String declaringName = declaringType == null ? "" : declaringType.getQualifiedName();
        if (declaringName.startsWith("org.assertj.")) {
            return AssertionSemanticCodes.ASSERTJ_MATCHER;
        }
        return AssertionSemanticCodes.HAMCREST_MATCHER;
    }

    private static CtExpression<?> focusArgument(CtInvocation<?> assertion, String semanticKind) {
        List<CtExpression<?>> args = assertion.getArguments();
        if (args.isEmpty()) {
            return null;
        }
        if (AssertionSemanticCodes.FAIL_SENTINEL.equals(semanticKind)) {
            return null;
        }
        if (AssertionSemanticCodes.HAMCREST_MATCHER.equals(semanticKind) || AssertionSemanticCodes.ASSERTJ_MATCHER.equals(semanticKind)) {
            return args.get(0);
        }
        return TestAnalysis.getActualParameterIndex(assertion)
            .map(args::get)
            .orElse(args.get(args.size() - 1));
    }

    private static String argumentShape(CtExpression<?> expression) {
        if (expression == null) {
            return AssertionSemanticCodes.ARGUMENT_SHAPE_NONE;
        }
        if (expression instanceof CtInvocation<?>) {
            return AssertionSemanticCodes.ARGUMENT_SHAPE_METHOD_CALL;
        }
        if (expression instanceof CtLiteral<?>) {
            return AssertionSemanticCodes.ARGUMENT_SHAPE_LITERAL;
        }
        if (expression instanceof CtVariableRead<?> || expression instanceof CtFieldRead<?>) {
            return AssertionSemanticCodes.ARGUMENT_SHAPE_VARIABLE_OR_FIELD;
        }
        return AssertionSemanticCodes.ARGUMENT_SHAPE_OTHER;
    }

    private static String failContext(CtInvocation<?> assertion) {
        if (hasParent(assertion, CtCatch.class)) {
            return AssertionSemanticCodes.FAIL_CONTEXT_CATCH_BLOCK_SHOULD_NOT_REACH;
        }
        if (hasParent(assertion, CtIf.class)) {
            return AssertionSemanticCodes.FAIL_CONTEXT_GUARD_BRANCH;
        }
        if (hasParent(assertion, CtTry.class)) {
            return AssertionSemanticCodes.FAIL_CONTEXT_TRY_BLOCK_EXPECTING_EXCEPTION;
        }
        return AssertionSemanticCodes.FAIL_CONTEXT_UNKNOWN;
    }

    private static <T extends CtElement> boolean hasParent(CtElement element, Class<T> parentType) {
        try {
            return element.getParent(parentType) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Matcher matcher(CtInvocation<?> assertion, String semanticKind) {
        if (!(AssertionSemanticCodes.HAMCREST_MATCHER.equals(semanticKind) || AssertionSemanticCodes.ASSERTJ_MATCHER.equals(semanticKind))) {
            return new Matcher(null, null);
        }
        String family = AssertionSemanticCodes.HAMCREST_MATCHER.equals(semanticKind)
            ? AssertionSemanticCodes.MATCHER_FAMILY_HAMCREST
            : AssertionSemanticCodes.MATCHER_FAMILY_ASSERTJ;
        String name = null;
        List<CtExpression<?>> args = assertion.getArguments();
        if (args.size() > 1 && args.get(1) instanceof CtInvocation<?>) {
            CtExecutableReference<?> matcherExecutable = ((CtInvocation<?>) args.get(1)).getExecutable();
            name = matcherExecutable.getSimpleName();
        }
        return new Matcher(family, name);
    }

    private static final class Matcher {
        private final String family;
        private final String name;

        private Matcher(String family, String name) {
            this.family = family;
            this.name = name;
        }
    }

    public static final class Result {
        private final String semanticKind;
        private final String argumentShape;
        private final String failContext;
        private final String matcherFamily;
        private final String matcherName;

        private Result(String semanticKind, String argumentShape, String failContext, String matcherFamily, String matcherName) {
            this.semanticKind = semanticKind;
            this.argumentShape = argumentShape;
            this.failContext = failContext;
            this.matcherFamily = matcherFamily;
            this.matcherName = matcherName;
        }

        public String semanticKind() {
            return this.semanticKind;
        }

        public String argumentShape() {
            return this.argumentShape;
        }

        public String failContext() {
            return this.failContext;
        }

        public String matcherFamily() {
            return this.matcherFamily;
        }

        public String matcherName() {
            return this.matcherName;
        }
    }
}
