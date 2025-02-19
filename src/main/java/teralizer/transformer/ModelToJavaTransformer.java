package teralizer.transformer;

import teralizer.domain.*;
import teralizer.domain.Error;

import java.util.Stack;

public class ModelToJavaTransformer extends ModelVisitor {
    private final Stack<String> stack = new Stack<>();

    public String transform(boolean value) {
        return String.valueOf(value);
    }

    public String transform(long value) {
        return value + ((value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) ? "L" : "");
    }

    public String transform(double value) {
        if (Double.isNaN(value)) {
            return "Double.NaN";
        } else if (value == Double.POSITIVE_INFINITY) {
            return "Double.POSITIVE_INFINITY";
        } else if (value == Double.NEGATIVE_INFINITY) {
            return "Double.NEGATIVE_INFINITY";
        }
        return String.valueOf(value);
    }

    public String transform(char value) {
        return String.valueOf(value);
    }

    public String transform(String value) {
        return String.valueOf(value);
    }

    public String transform(teralizer.domain.Model model) {
        if (model == null) {
            return null;
        } else {
            model.accept(this);
            assert this.stack.size() == 1;
            return this.stack.pop();
        }
    }

    @Override
    public void postVisit(Operation operation) {
        String right = operation.right == null ? null : this.stack.pop();
        String left = operation.left == null ? null : this.stack.pop();

        String expr;
        switch (operation.op) {
            case EQ:
                expr = "(" + left + " == " + right + ")";
                break;
            case NE:
                expr = "(" + left + " != " + right + ")";
                break;
            case LT:
                expr = "(" + left + " < " + right + ")";
                break;
            case LE:
                expr = "(" + left + " <= " + right + ")";
                break;
            case GT:
                expr = "(" + left + " > " + right + ")";
                break;
            case GE:
                expr = "(" + left + " >= " + right + ")";
                break;
            case PLUS:
                expr = "(" + left + " + " + right + ")";
                break;
            case MINUS:
                expr = "(" + left + " - " + right + ")";
                break;
            case MUL:
                expr = "(" + left + " * " + right + ")";
                break;
            case DIV:
                expr = "(" + left + " / " + right + ")";
                break;
            case MOD:
                expr = "(" + left + " % " + right + ")";
                break;
            case AND:
                expr = "(" + left + " & " + right + ")";
                break;
            case OR:
                expr = "(" + left + " | " + right + ")";
                break;
            case XOR:
                expr = "(" + left + " ^ " + right + ")";
                break;
            case POW:
                expr = "Math.pow(" + left + ", " + right + ")";
                break;
            case SQRT:
                expr = "Math.sqrt(" + left + ")";
                break;
            case EXP:
                expr = "Math.exp(" + left + ")";
                break;
            case LOG:
                expr = "Math.log(" + left + ")";
                break;
            case SIN:
                expr = "Math.sin(" + left + ")";
                break;
            case COS:
                expr = "Math.cos(" + left + ")";
                break;
            case TAN:
                expr = "Math.tan(" + left + ")";
                break;
            case ASIN:
                expr = "Math.asin(" + left + ")";
                break;
            case ACOS:
                expr = "Math.acos(" + left + ")";
                break;
            case ATAN:
                expr = "Math.atan(" + left + ")";
                break;
            case ATAN2:
                expr = "Math.atan2(" + left + ", " + right + ")";
                break;
            case SHIFTL:
                expr = "(" + left + " << " + right + ")";
                break;
            case SHIFTR:
                expr = "(" + left + " >> " + right + ")";
                break;
            case SHIFTUR:
                expr = "(" + left + " >>> " + right + ")";
                break;
            default:
                throw new RuntimeException("Unable to transform operation '" + operation + "' to Java.");
        }
        this.stack.push(expr);
    }

    @Override
    public void postVisit(ConstantInteger constant) {
        this.stack.push(this.transform(constant.value));
    }

    @Override
    public void postVisit(ConstantReal constant) {
        this.stack.push(this.transform(constant.value));
    }

    @Override
    public void postVisit(ConstantString constant) {
        this.stack.push(this.transform(constant.value));
    }

    @Override
    public void postVisit(VariableInteger variable) {
        assert variable != null;
        assert variable.name != null;
        this.stack.push("_p_." + variable.name);
    }

    @Override
    public void postVisit(VariableReal variable) {
        assert variable != null;
        assert variable.name != null;
        this.stack.push("_p_." + variable.name);
    }

    @Override
    public void postVisit(VariableString variable) {
        assert variable != null;
        assert variable.name != null;
        this.stack.push("_p_." + variable.name);
    }

    @Override
    public void postVisit(ArrayExpression expression) {
        this.stack.push("_p_." + expression.name);
    }

    @Override
    public void postVisit(ArrayElementExpression expression) {
        String elementSelector = this.stack.pop();
        this.stack.push("_p_." + expression.arrayName + "[" + elementSelector + "]");
    }

    @Override
    public void postVisit(SymbolicIntegerFunction function) {
        String[] args = this.popArgs(function.args.length);
        String expr = function.name + "(" + String.join(", ", args) + ")";
        this.stack.push(expr);
    }

    @Override
    public void postVisit(SymbolicRealFunction function) {
        String[] args = this.popArgs(function.args.length);
        String expr = function.name + "(" + String.join(", ", args) + ")";
        this.stack.push(expr);
    }

    @Override
    public void postVisit(SymbolicStringFunction function) {
        String[] args = this.popArgs(function.args.length);
        String expr = function.name + "(" + String.join(", ", args) + ")";
        this.stack.push(expr);
    }

    @Override
    public void postVisit(Error error) {
        // Only models without errors should be transformed to Java.
        throw new RuntimeException("Unable to transform error '" + error + "' to z3.");
    }

    @Override
    public void postVisit(ExceptionModel exceptionModel) {
        this.stack.push(exceptionModel.name + ".class");
    }

    private String[] popArgs(int n) {
        String[] args = new String[n];
        for (int i = n - 1; i >= 0; i--) {
            args[i] = this.stack.pop();
        }
        return args;
    }
}
