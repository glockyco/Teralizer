package teralizer.spoon.analysis;

import java.util.List;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;

public final class ExpectedTypeInference {
    private ExpectedTypeInference() {
    }

    public static CtTypeReference<?> inferExpectedType(CtInvocation<?> call) {
        CtElement parent = call.getParent();
        Factory factory = call.getFactory();

        if (parent instanceof CtAssignment) {
            CtAssignment<?, ?> assignment = (CtAssignment<?, ?>) parent;
            return eraseGenerics(assignment.getAssigned().getType(), factory);
        }

        if (parent instanceof CtVariable) {
            CtVariable<?> variable = (CtVariable<?>) parent;
            return eraseGenerics(variable.getType(), factory);
        }

        if (parent instanceof CtInvocation) {
            CtInvocation<?> invocation = (CtInvocation<?>) parent;
            int argIndex = invocation.getArguments().indexOf(call);
            if (argIndex >= 0) {
                CtExecutableReference<?> execRef = invocation.getExecutable();
                List<CtTypeReference<?>> paramTypes = execRef.getParameters();
                if (argIndex < paramTypes.size()) {
                    return eraseGenerics(paramTypes.get(argIndex), factory);
                }
                if (!paramTypes.isEmpty()) {
                    return eraseGenerics(paramTypes.get(paramTypes.size() - 1), factory);
                }
            }
        }

        if (parent instanceof CtReturn) {
            CtMethod<?> enclosingMethod = call.getParent(CtMethod.class);
            if (enclosingMethod != null) {
                return eraseGenerics(enclosingMethod.getType(), factory);
            }
        }

        if (parent instanceof CtConditional) {
            return factory.Type().BOOLEAN_PRIMITIVE;
        }

        return eraseGenerics(call.getType(), factory);
    }

    static CtTypeReference<?> eraseGenerics(CtTypeReference<?> type, Factory factory) {
        if (type == null || type.isGenerics() || type instanceof CtTypeParameterReference) {
            return factory.Type().OBJECT;
        }
        return type;
    }
}
