package teralizer.domain;

public abstract class ModelVisitor {
    public void preVisit(Model model) {}
    public void preVisit(Expression expression) {}
    public void preVisit(Operation operation) {}
    public void preVisit(Operator operator) {}
    public void preVisit(Constant constant) {}
    public void preVisit(Variable variable) {}
    public void preVisit(ArrayExpression expression) {}
    public void preVisit(ArrayElementExpression expression) {}
    public void preVisit(Invocation invocation) {}
    public void preVisit(Not not) {}
    public void preVisit(Error error) {}
    public void preVisit(ExceptionModel exceptionModel) {}

    public void postVisit(Model model) {}
    public void postVisit(Expression expression) {}
    public void postVisit(Operation operation) {}
    public void postVisit(Operator operator) {}
    public void postVisit(Constant constant) {}
    public void postVisit(Variable variable) {}
    public void postVisit(ArrayExpression expression) {}
    public void postVisit(ArrayElementExpression expression) {}
    public void postVisit(Invocation invocation) {}
    public void postVisit(Not not) {}
    public void postVisit(Error error) {}
    public void postVisit(ExceptionModel exceptionModel) {}
}
