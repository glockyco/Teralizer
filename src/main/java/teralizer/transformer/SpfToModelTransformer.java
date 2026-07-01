package teralizer.transformer;

import gov.nasa.jpf.symbc.numeric.ConstraintExpressionVisitor;
import teralizer.domain.Error;
import teralizer.domain.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

public class SpfToModelTransformer {

    public Expression transform(gov.nasa.jpf.symbc.numeric.PathCondition pathCondition) {
        if (pathCondition == null || pathCondition.header == null) {
            return null;
        }

        return this.transform(pathCondition.header);
    }

    public Expression transform(gov.nasa.jpf.symbc.string.StringPathCondition pathCondition) {
        if (pathCondition == null || pathCondition.header == null) {
            return null;
        }

        return this.transform(pathCondition.header);
    }

    public Expression transform(gov.nasa.jpf.symbc.numeric.Constraint constraint) {
        if (constraint == null) {
            return null;
        }

        ConstraintExpressionFactoryVisitor visitor = new ConstraintExpressionFactoryVisitor();
        constraint.accept(visitor);
        return visitor.getExpression();
    }

    public Expression transform(gov.nasa.jpf.symbc.string.StringConstraint constraint) {
        if (constraint == null) {
            return null;
        }

        ConstraintExpressionFactoryVisitor visitor = new ConstraintExpressionFactoryVisitor();
        constraint.accept(visitor);
        return visitor.getExpression();
    }

    public Expression transform(gov.nasa.jpf.symbc.numeric.Expression expression) {
        if (expression == null) {
            return null;
        }

        ConstraintExpressionFactoryVisitor visitor = new ConstraintExpressionFactoryVisitor();
        expression.accept(visitor);
        return visitor.getExpression();
    }

    public Error transform(gov.nasa.jpf.Error error) {
        if (error == null) {
            return null;
        }

        // @TODO: Include more information from the original JPF error.
        return new Error("Error", error.getDetails());
    }

    public ExceptionModel transform(CapturedException exception) {
        if (exception == null) {
            return null;
        }

        return new ExceptionModel(exception.getName(), exception.getMessage());
    }

    private static class ConstraintExpressionFactoryVisitor extends ConstraintExpressionVisitor {
        private final Stack<Expression> stack = new Stack<>();

        public Expression getExpression() {
            assert this.stack.size() == 1;
            return this.stack.pop();
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.Constraint constraint) {
            Expression and = null;
            if (constraint.and != null) {
                and = this.stack.pop();
            }

            Expression right = this.stack.pop();
            Expression left = this.stack.pop();
            Operator op = Operator.get(constraint.getComparator().toString());

            Operation operation = new Operation(left, op, right);

            if (and == null) {
                this.stack.push(operation);
            } else {
                this.stack.push(new Operation(and, Operator.AND, operation));
            }
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.string.StringConstraint constraint) {
            Expression and = null;
            if (constraint.and() != null) {
                and = this.stack.pop();
            }

            Expression right = this.stack.pop();
            Expression left = constraint.getLeft() == null ? null : this.stack.pop();
            Expression top = invocationForComparator(constraint.getComparator(), left, right);

            if (and == null) {
                this.stack.push(top);
            } else {
                this.stack.push(new Operation(top, Operator.AND, and));
            }
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.BinaryNonLinearIntegerExpression expression) {
            Expression right = this.stack.pop();
            Expression left = this.stack.pop();
            Operator op = Operator.get(expression.op.toString());

            this.stack.push(new Operation(left, op, right));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.string.DerivedStringExpression expression) {
            if (expression.oprlist != null) {
                List<Expression> operands = new ArrayList<>(expression.oprlist.length);
                for (gov.nasa.jpf.symbc.numeric.Expression operand : expression.oprlist) {
                    operands.add(transformOperand(operand));
                }
                this.stack.push(invocationForOperator(expression.op, operands));
                return;
            }

            Expression right = expression.right == null ? null : this.stack.pop();
            Expression left = expression.left == null ? null : this.stack.pop();
            this.stack.push(invocationForOperator(expression.op, left, right));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.BinaryLinearIntegerExpression expression) {
            Expression right = this.stack.pop();
            Expression left = this.stack.pop();
            Operator op = Operator.get(expression.getOp().toString());

            this.stack.push(new Operation(left, op, right));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.BinaryRealExpression expression) {
            Expression right = this.stack.pop();
            Expression left = this.stack.pop();
            Operator op = Operator.get(expression.getOp().toString());

            this.stack.push(new Operation(left, op, right));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.MathRealExpression expression) {
            Expression right = expression.getArg2() == null ? null : this.stack.pop();
            Expression left = expression.getArg1() == null ? null : this.stack.pop();
            List<Expression> args = new ArrayList<>(2);
            args.add(left);
            if (right != null) {
                args.add(right);
            }
            this.stack.push(new Invocation(
                null,
                "java.lang.Math",
                expression.getOp().name().toLowerCase(Locale.ROOT),
                args));
        }

        private Expression transformOperand(gov.nasa.jpf.symbc.numeric.Expression expression) {
            ConstraintExpressionFactoryVisitor visitor = new ConstraintExpressionFactoryVisitor();
            expression.accept(visitor);
            return visitor.getExpression();
        }

        private static Expression invocationForComparator(
            gov.nasa.jpf.symbc.string.StringComparator comparator,
            Expression left,
            Expression right) {
            if (isEqualityComparator(comparator) && isStringConstant(left) && !isStringConstant(right)) {
                Expression originalLeft = left;
                left = right;
                right = originalLeft;
            }
            switch (comparator) {
                case EQUALS:
                    return new Invocation(left, null, "equals", Collections.singletonList(right));
                case NOTEQUALS:
                    return new Not(new Invocation(left, null, "equals", Collections.singletonList(right)));
                case EQUALSIGNORECASE:
                    return new Invocation(left, null, "equalsIgnoreCase", Collections.singletonList(right));
                case NOTEQUALSIGNORECASE:
                    return new Not(new Invocation(left, null, "equalsIgnoreCase", Collections.singletonList(right)));
                case STARTSWITH:
                    return new Invocation(left, null, "startsWith", Collections.singletonList(right));
                case NOTSTARTSWITH:
                    return new Not(new Invocation(left, null, "startsWith", Collections.singletonList(right)));
                case ENDSWITH:
                    return new Invocation(left, null, "endsWith", Collections.singletonList(right));
                case NOTENDSWITH:
                    return new Not(new Invocation(left, null, "endsWith", Collections.singletonList(right)));
                case CONTAINS:
                    return new Invocation(left, null, "contains", Collections.singletonList(right));
                case NOTCONTAINS:
                    return new Not(new Invocation(left, null, "contains", Collections.singletonList(right)));
                case EMPTY:
                    return new Invocation(left == null ? right : left, null, "isEmpty", Collections.emptyList());
                case NOTEMPTY:
                    return new Not(new Invocation(left == null ? right : left, null, "isEmpty", Collections.emptyList()));
                default:
                    throw new UnsupportedSpfTermException(
                        "String comparator '" + comparator + "' is not mapped to a Model invocation.");
            }
        }

        private static boolean isStringConstant(Expression expression) {
            return expression instanceof Constant && ((Constant) expression).domain == TypeDomain.STRING;
        }

        private static boolean isEqualityComparator(gov.nasa.jpf.symbc.string.StringComparator comparator) {
            switch (comparator) {
                case EQUALS:
                case NOTEQUALS:
                case EQUALSIGNORECASE:
                case NOTEQUALSIGNORECASE:
                    return true;
                default:
                    return false;
            }
        }

        private static Expression invocationForOperator(
            gov.nasa.jpf.symbc.string.StringOperator operator,
            Expression left,
            Expression right) {
            switch (operator) {
                case CONCAT:
                    return new Invocation(left, null, "concat", Collections.singletonList(right));
                case TRIM:
                    return new Invocation(right, null, "trim", Collections.emptyList());
                case TOLOWERCASE:
                    return new Invocation(right, null, "toLowerCase", Collections.emptyList());
                case TOUPPERCASE:
                    return new Invocation(right, null, "toUpperCase", Collections.emptyList());
                default:
                    throw new UnsupportedSpfTermException(
                        "String operator '" + operator + "' is not mapped from left/right operands.");
            }
        }

        private static Expression invocationForOperator(
            gov.nasa.jpf.symbc.string.StringOperator operator,
            List<Expression> operands) {
            if (operator == gov.nasa.jpf.symbc.string.StringOperator.VALUEOF) {
                return new Invocation(null, "java.lang.String", "valueOf", operands);
            }
            if (operands.isEmpty()) {
                throw new UnsupportedSpfTermException(
                    "String operator '" + operator + "' has no receiver operand.");
            }
            Expression receiver = operands.get(0);
            List<Expression> args = operands.subList(1, operands.size());
            switch (operator) {
                case REPLACE:
                    return new Invocation(receiver, null, "replace", args);
                case REPLACEFIRST:
                    return new Invocation(receiver, null, "replaceFirst", args);
                case REPLACEALL:
                    return new Invocation(receiver, null, "replaceAll", args);
                case SUBSTRING:
                    return new Invocation(receiver, null, "substring", args);
                case TOLOWERCASE:
                    return new Invocation(receiver, null, "toLowerCase", args);
                case TOUPPERCASE:
                    return new Invocation(receiver, null, "toUpperCase", args);
                default:
                    throw new UnsupportedSpfTermException(
                        "String operator '" + operator + "' is not mapped from oprlist operands.");
            }
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.IntegerConstant constant) {
            this.stack.push(new Constant((long) constant.value, TypeDomain.INTEGER));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.RealConstant constant) {
            this.stack.push(new Constant(constant.value, TypeDomain.REAL));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.string.StringConstant constant) {
            this.stack.push(new Constant(constant.value, TypeDomain.STRING));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.SymbolicInteger variable) {
            String name = variable.getName().replaceAll("_\\d+_[A-Z]+$", "");
            this.stack.push(new Variable(name, TypeDomain.INTEGER));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.numeric.SymbolicReal variable) {
            String name = variable.getName().replaceAll("_\\d+_[A-Z]+$", "");
            this.stack.push(new Variable(name, TypeDomain.REAL));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.string.StringSymbolic variable) {
            String name = variable.getName().replaceAll("_\\d+_[A-Z]+$", "");
            this.stack.push(new Variable(name, TypeDomain.STRING));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.string.SymbolicStringBuilder constant) {
            this.stack.push(new Constant(constant.toString(), TypeDomain.STRING));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.arrays.ArrayExpression expression) {
            this.stack.push(new ArrayExpression(expression.getName(), expression.getElemType()));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.arrays.SelectExpression expr) {
            Expression indexExpression = this.stack.pop();
            ArrayExpression arrayExpression = (ArrayExpression) this.stack.pop();

            this.stack.push(new ArrayElementExpression(arrayExpression.name, arrayExpression.elementType, indexExpression));
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.mixednumstrg.SpecialIntegerExpression expression) {
            throw new UnsupportedSpfTermException(
                "SpecialIntegerExpression is not mapped to a Model node.");
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.mixednumstrg.SpecialRealExpression expression) {
            throw new UnsupportedSpfTermException(
                "SpecialRealExpression is not mapped to a Model node.");
        }

        @Override
        public void postVisit(gov.nasa.jpf.symbc.concolic.FunctionExpression expression) {
            throw new UnsupportedSpfTermException(
                "FunctionExpression is not mapped to a Model node.");
        }
    }
}

