package teralizer.spoon.analysis;

import java.util.List;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnaryOperator;

public final class ExpressionSliceScreen {
    private ExpressionSliceScreen() {
    }

    public static boolean isSelfContained(CtExpression<?> expression) {
        if (expression == null) {
            return false;
        }
        if (expression instanceof CtLiteral<?>) {
            return true;
        }
        if (expression instanceof CtNewClass<?>) {
            return false;
        }
        if (expression instanceof CtConstructorCall<?>) {
            return allSelfContained(((CtConstructorCall<?>) expression).getArguments());
        }
        if (expression instanceof CtInvocation<?>) {
            CtInvocation<?> invocation = (CtInvocation<?>) expression;
            CtExpression<?> target = invocation.getTarget();
            boolean targetOk = target == null || target instanceof CtTypeAccess<?> || isSelfContained(target);
            return targetOk && allSelfContained(invocation.getArguments());
        }
        if (expression instanceof CtBinaryOperator<?>) {
            CtBinaryOperator<?> operator = (CtBinaryOperator<?>) expression;
            return isSelfContained(operator.getLeftHandOperand())
                && isSelfContained(operator.getRightHandOperand());
        }
        if (expression instanceof CtUnaryOperator<?>) {
            return isSelfContained(((CtUnaryOperator<?>) expression).getOperand());
        }
        return false;
    }

    private static boolean allSelfContained(List<CtExpression<?>> expressions) {
        for (CtExpression<?> expression : expressions) {
            if (!isSelfContained(expression)) {
                return false;
            }
        }
        return true;
    }
}
