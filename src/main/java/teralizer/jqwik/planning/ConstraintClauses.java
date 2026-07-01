package teralizer.jqwik.planning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.transformer.ModelToJavaTransformer;
import teralizer.transformer.NonGeneralizableExpressionException;
import teralizer.transformer.VariableNameCollector;

public final class ConstraintClauses {
    private ConstraintClauses() {
    }

    /**
     * Flattens the input predicate into top-level conjuncts and renders each to Java. A conjunct
     * whose referenced variables are all outside {@code generalizableParameterNames} stays at its
     * concrete value on the path, so it is trivially satisfied and dropped -- independent of whether
     * it renders. A conjunct that constrains a generated parameter is always rendered; if its
     * operator is unsupported the typed {@link NonGeneralizableExpressionException} propagates rather
     * than silently weakening the SPF path predicate. This keeps the planner's clause set consistent
     * with the residual predicate rendered by {@link ModelToJavaTransformer#transformPredicate}.
     */
    public static List<ConstraintClause> from(Model inputModel, Map<String, String> parameterTypes, Set<String> generalizableParameterNames) {
        if (inputModel == null) {
            return Collections.emptyList();
        }

        List<Model> expressions = new ArrayList<>();
        flatten(inputModel, expressions);

        ModelToJavaTransformer transformer = new ModelToJavaTransformer(parameterTypes);
        List<ConstraintClause> clauses = new ArrayList<>();
        int id = 0;
        for (Model expression : expressions) {
            Set<String> referenced = new LinkedHashSet<>();
            expression.accept(new VariableNameCollector(referenced));
            // A conjunct constraining only filtered-out (concrete) parameters is trivially satisfied,
            // so drop it before rendering -- a renderable String clause over a filtered parameter must
            // not survive and reference a `_p_` field that is never generated.
            if (!referenced.isEmpty() && Collections.disjoint(referenced, generalizableParameterNames)) {
                continue;
            }
            // Constrains a generated parameter (or references none): render it; an unsupported
            // operator here propagates as NonGeneralizableExpressionException rather than weakening
            // the predicate.
            clauses.add(new ConstraintClause(id++, expression, expression.fold(transformer)));
        }
        return clauses;
    }

    /** Convenience overload assuming every parameter is generalizable (no drops). */
    public static List<ConstraintClause> from(Model inputModel, Map<String, String> parameterTypes) {
        return from(inputModel, parameterTypes, parameterTypes.keySet());
    }

    private static void flatten(Model model, List<Model> expressions) {
        if (model instanceof Operation) {
            Operation operation = (Operation) model;
            if (operation.op == Operator.AND && operation.left != null && operation.right != null) {
                flatten(operation.left, expressions);
                flatten(operation.right, expressions);
                return;
            }
        }
        expressions.add(model);
    }
}
