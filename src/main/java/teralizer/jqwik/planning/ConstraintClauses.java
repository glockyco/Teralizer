package teralizer.jqwik.planning;

import teralizer.domain.Model;
import teralizer.domain.Operation;
import teralizer.domain.Operator;
import teralizer.transformer.ModelToJavaTransformer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ConstraintClauses {
    private ConstraintClauses() {
    }

    public static List<ConstraintClause> from(Model inputModel, Map<String, String> parameterTypes) {
        if (inputModel == null) {
            return Collections.emptyList();
        }

        List<Model> expressions = new ArrayList<>();
        flatten(inputModel, expressions);

        ModelToJavaTransformer transformer = new ModelToJavaTransformer(parameterTypes);
        List<ConstraintClause> clauses = new ArrayList<>();
        for (int i = 0; i < expressions.size(); i++) {
            Model expression = expressions.get(i);
            clauses.add(new ConstraintClause(i, expression, transformer.transform(expression)));
        }
        return clauses;
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
