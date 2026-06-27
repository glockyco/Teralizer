package teralizer.jqwik.planning;

import teralizer.domain.Model;

public class ConstraintClause {
    private final int id;
    private final Model expression;
    private final String javaExpression;

    public ConstraintClause(int id, Model expression, String javaExpression) {
        this.id = id;
        this.expression = expression;
        this.javaExpression = javaExpression;
    }

    public int getId() {
        return this.id;
    }

    public Model getExpression() {
        return this.expression;
    }

    public String getJavaExpression() {
        return this.javaExpression;
    }
}
