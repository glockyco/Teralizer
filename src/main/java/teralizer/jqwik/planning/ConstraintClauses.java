package teralizer.jqwik.planning;

import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.transformer.ModelToJavaTransformer;
import teralizer.transformer.NonGeneralizableExpressionException;
import teralizer.transformer.VariableNameCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConstraintClauses {
    private ConstraintClauses() {
    }

    /**
     * Flattens the input predicate into top-level conjuncts and renders each to Java,
     * dropping a clause only when it is non-generalizable and references no generated
     * parameter (its variables all stay at their concrete value, so the clause is
     * trivially satisfied). A clause that uses an unsupported operator but still
     * constrains a generated parameter is never dropped — that would weaken the SPF
     * path predicate — and the typed {@link NonGeneralizableExpressionException}
     * surfaces instead. This keeps the planner's clause set consistent with the
     * residual predicate rendered by {@link ModelToJavaTransformer#transformPredicate}.
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
            String javaExpression;
            try {
                javaExpression = expression.fold(transformer);
            } catch (NonGeneralizableExpressionException nonGeneralizable) {
                Set<String> referenced = new LinkedHashSet<>();
                expression.accept(new VariableNameCollector(referenced));
                if (!referenced.isEmpty() && Collections.disjoint(referenced, generalizableParameterNames)) {
                    continue;
                }
                throw nonGeneralizable;
            }
            clauses.add(new ConstraintClause(id++, expression, javaExpression));
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
