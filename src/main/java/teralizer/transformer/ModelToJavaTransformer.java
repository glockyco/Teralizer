package teralizer.transformer;

import teralizer.domain.Error;
import teralizer.domain.*;

import java.util.Collections;
import java.util.Map;
import java.util.Stack;

public class ModelToJavaTransformer extends ModelVisitor {
    private final Stack<String> stack = new Stack<>();
    private final Map<String, String> variableTypes;

    public ModelToJavaTransformer() {
        this(Collections.emptyMap());
    }

    public ModelToJavaTransformer(Map<String, String> variableTypes) {
        this.variableTypes = variableTypes;
    }

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
        return "'" + this.escapeChar(value) + "'";
    }

    public String transform(String value) {
        return String.valueOf(value);
    }

    public String transform(MethodArgument argument) {
        switch (argument.getType()) {
            case "byte":
            case "java.lang.Byte":
            case "short":
            case "java.lang.Short":
            case "int":
            case "java.lang.Integer":
            case "char":
            case "java.lang.Character":
                return this.renderCharArgument(argument.getValue());
            case "boolean":
            case "java.lang.Boolean":
                return this.renderBooleanArgument(argument.getValue());
            case "String":
            case "java.lang.String":
                return argument.getValue();
            case "long":
            case "java.lang.Long":
                return argument.getValue() + "L";
            case "float":
            case "java.lang.Float":
                switch (argument.getValue()) {
                    case "NaN":
                        return "Float.NaN";
                    case "Infinity":
                        return "Float.POSITIVE_INFINITY";
                    case "-Infinity":
                        return "Float.NEGATIVE_INFINITY";
                    default:
                        return argument.getValue() + "F";
                }
            case "double":
            case "java.lang.Double":
                switch (argument.getValue()) {
                    case "NaN":
                        return "Double.NaN";
                    case "Infinity":
                        return "Double.POSITIVE_INFINITY";
                    case "-Infinity":
                        return "Double.NEGATIVE_INFINITY";
                    default:
                        return argument.getValue();
                }
            default:
                return "null";
        }
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
        if ("boolean".equals(this.variableTypes.get(variable.name)) || "java.lang.Boolean".equals(this.variableTypes.get(variable.name))) {
            this.stack.push("(_p_." + variable.name + " ? 1 : 0)");
        } else {
            this.stack.push("_p_." + variable.name);
        }
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
        this.stack.push(null);
    }

    private String renderBooleanArgument(String value) {
        switch (value) {
            case "1":
            case "true":
                return "true";
            case "0":
            case "false":
                return "false";
            default:
                throw new RuntimeException("Unable to transform boolean value '" + value + "' to Java.");
        }
    }

    private String renderCharArgument(String value) {
        if (value.startsWith("'") && value.endsWith("'")) {
            return value;
        }
        if (value.matches("[0-9]+")) {
            return "(char) " + value;
        }
        if (value.length() == 1) {
            return this.transform(value.charAt(0));
        }
        throw new RuntimeException("Unable to transform char value '" + value + "' to Java.");
    }

    private String escapeChar(char value) {
        switch (value) {
            case '\b':
                return "\\b";
            case '\t':
                return "\\t";
            case '\n':
                return "\\n";
            case '\f':
                return "\\f";
            case '\r':
                return "\\r";
            case '"':
                return "\"";
            case '\'':
                return "\\'";
            case '\\':
                return "\\\\";
            default:
                return String.valueOf(value);
        }
    }

    private String[] popArgs(int n) {
        String[] args = new String[n];
        for (int i = n - 1; i >= 0; i--) {
            args[i] = this.stack.pop();
        }
        return args;
    }
}
