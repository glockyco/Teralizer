package teralizer.transformer;

import teralizer.domain.Error;
import teralizer.domain.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link Model} tree to a Java expression. Backed by {@link ModelFolder},
 * so every concrete node kind has its own hook — a new node added to the domain is a
 * compile error here until a hook is implemented, never a silent no-op.
 *
 * <p>Unsupported {@link Operator}s (the string operators) are not node kinds; they are
 * rendered through the operation hook's default branch and raise a typed
 * {@link NonGeneralizableExpressionException}. {@link #transformPredicate} uses that
 * signal to drop a clause whose only referenced parameters are non-symbolized (sound:
 * they stay concrete), while refusing to drop a clause that still constrains a
 * generated parameter (which would weaken the path predicate).
 */
public class ModelToJavaTransformer extends ModelFolder<String> {
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
                return argument.getValue();
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
        }
        return model.fold(this);
    }

    /**
     * Renders the input path predicate to a Java boolean, dropping clauses that are
     * non-generalizable because they reference only parameters that will not be
     * symbolized (they stay at their concrete value, so the clause is trivially
     * satisfied). A clause that uses an unsupported operator but still constrains a
     * generated parameter is not dropped — that would weaken the predicate — and the
     * typed {@link NonGeneralizableExpressionException} surfaces instead. When every
     * clause is dropped the predicate is {@code true}.
     *
     * @param generalizableParameterNames names of parameters that jqwik will generate;
     *        a clause is sound to drop only if every variable it references is outside
     *        this set
     */
    public String transformPredicate(teralizer.domain.Model inputModel, Set<String> generalizableParameterNames) {
        if (inputModel == null) {
            return "true";
        }
        List<teralizer.domain.Model> clauses = new ArrayList<>();
        flattenConjuncts(inputModel, clauses);

        List<String> rendered = new ArrayList<>();
        for (teralizer.domain.Model clause : clauses) {
            String javaClause;
            try {
                javaClause = clause.fold(this);
            } catch (NonGeneralizableExpressionException nonGeneralizable) {
                // Drop only if the clause references at least one variable and every one of
                // those variables is non-symbolized (stays concrete, so the clause is
                // trivially satisfied). A clause with no variables, or one that constrains
                // a generated parameter, must not be dropped — the former is not a
                // filter-referencing clause and the latter would weaken the predicate.
                Set<String> referenced = new LinkedHashSet<>();
                clause.accept(new VariableNameCollector(referenced));
                if (!referenced.isEmpty() && Collections.disjoint(referenced, generalizableParameterNames)) {
                    continue;
                }
                throw nonGeneralizable;
            }
            rendered.add(javaClause);
        }
        if (rendered.isEmpty()) {
            return "true";
        }
        return String.join(" && ", rendered);
    }

    private static void flattenConjuncts(teralizer.domain.Model model, List<teralizer.domain.Model> clauses) {
        if (model instanceof Operation) {
            Operation operation = (Operation) model;
            if (operation.op == Operator.AND && operation.left != null && operation.right != null) {
                flattenConjuncts(operation.left, clauses);
                flattenConjuncts(operation.right, clauses);
                return;
            }
        }
        clauses.add(model);
    }

    @Override
    public String fold(ConstantInteger constant) {
        return this.transform(constant.value);
    }

    @Override
    public String fold(ConstantReal constant) {
        return this.transform(constant.value);
    }

    @Override
    public String fold(ConstantString constant) {
        return this.renderStringLiteral(constant.value);
    }

    @Override
    public String fold(VariableInteger variable) {
        assert variable != null;
        assert variable.name != null;
        if ("boolean".equals(this.variableTypes.get(variable.name)) || "java.lang.Boolean".equals(this.variableTypes.get(variable.name))) {
            return "(_p_." + variable.name + " ? 1 : 0)";
        }
        return "_p_." + variable.name;
    }

    @Override
    public String fold(VariableReal variable) {
        assert variable != null;
        assert variable.name != null;
        return "_p_." + variable.name;
    }

    @Override
    public String fold(VariableString variable) {
        assert variable != null;
        assert variable.name != null;
        return "_p_." + variable.name;
    }

    @Override
    public String fold(ArrayExpression expression) {
        return "_p_." + expression.name;
    }

    @Override
    public String fold(ArrayElementExpression expression, String elementSelector) {
        return "_p_." + expression.arrayName + "[" + elementSelector + "]";
    }

    @Override
    public String fold(SymbolicIntegerFunction function, List<String> args) {
        return function.name + "(" + String.join(", ", args) + ")";
    }

    @Override
    public String fold(SymbolicRealFunction function, List<String> args) {
        return function.name + "(" + String.join(", ", args) + ")";
    }

    @Override
    public String fold(SymbolicStringFunction function, List<String> args) {
        return function.name + "(" + String.join(", ", args) + ")";
    }

    @Override
    public String fold(Operation operation, String left, String right) {
        if (isBitwiseOrShift(operation.op)
            && (isFloatingPoint(operation.left) || isFloatingPoint(operation.right))) {
            throw new NonGeneralizableExpressionException(
                "Cannot render operator '" + operation.op.name()
                    + "' on floating-point operands as Java; the raw-bits relation is not modeled.");
        }
        switch (operation.op) {
            case EQ:
                return "(" + left + " == " + right + ")";
            case NE:
                return "(" + left + " != " + right + ")";
            case LT:
                return "(" + left + " < " + right + ")";
            case LE:
                return "(" + left + " <= " + right + ")";
            case GT:
                return "(" + left + " > " + right + ")";
            case GE:
                return "(" + left + " >= " + right + ")";
            case PLUS:
                return "(" + left + " + " + right + ")";
            case MINUS:
                return "(" + left + " - " + right + ")";
            case MUL:
                return "(" + left + " * " + right + ")";
            case DIV:
                return "(" + left + " / " + right + ")";
            case MOD:
                return "(" + left + " % " + right + ")";
            case AND:
                return "(" + left + " & " + right + ")";
            case OR:
                return "(" + left + " | " + right + ")";
            case XOR:
                return "(" + left + " ^ " + right + ")";
            case POW:
                return "Math.pow(" + left + ", " + right + ")";
            case SQRT:
                return "Math.sqrt(" + left + ")";
            case EXP:
                return "Math.exp(" + left + ")";
            case LOG:
                return "Math.log(" + left + ")";
            case SIN:
                return "Math.sin(" + left + ")";
            case COS:
                return "Math.cos(" + left + ")";
            case TAN:
                return "Math.tan(" + left + ")";
            case ASIN:
                return "Math.asin(" + left + ")";
            case ACOS:
                return "Math.acos(" + left + ")";
            case ATAN:
                return "Math.atan(" + left + ")";
            case ATAN2:
                return "Math.atan2(" + left + ", " + right + ")";
            case SHIFTL:
                return "(" + left + " << " + right + ")";
            case SHIFTR:
                return "(" + left + " >> " + right + ")";
            case SHIFTUR:
                return "(" + left + " >>> " + right + ")";
            default:
                throw new NonGeneralizableExpressionException(
                    "Unable to transform operation '" + operation + "' (operator " + operation.op.name() + ") to Java.");
        }
    }

    private static boolean isBitwiseOrShift(Operator op) {
        switch (op) {
            case AND:
            case OR:
            case XOR:
            case SHIFTL:
            case SHIFTR:
            case SHIFTUR:
                return true;
            default:
                return false;
        }
    }

    private static boolean isFloatingPoint(Expression expression) {
        if (expression instanceof VariableReal
            || expression instanceof ConstantReal
            || expression instanceof SymbolicRealFunction) {
            return true;
        }
        if (expression instanceof Operation) {
            Operation inner = (Operation) expression;
            switch (inner.op) {
                case POW:
                case SQRT:
                case EXP:
                case LOG:
                case SIN:
                case COS:
                case TAN:
                case ASIN:
                case ACOS:
                case ATAN:
                case ATAN2:
                    return true;
                case PLUS:
                case MINUS:
                case MUL:
                case DIV:
                case MOD:
                    return (inner.left != null && isFloatingPoint(inner.left))
                        || (inner.right != null && isFloatingPoint(inner.right));
                default:
                    return false;
            }
        }
        return false;
    }

    @Override
    public String fold(Operator operator) {
        // Operators are visited as part of an Operation, never folded standalone for Java.
        throw new NonGeneralizableExpressionException(
            "Operator '" + operator + "' is not a standalone Java expression.");
    }

    @Override
    public String fold(Error error) {
        // Only models without errors should be transformed to Java.
        throw new NonGeneralizableExpressionException(
            "Unable to transform error '" + error + "' to Java.");
    }

    @Override
    public String fold(ExceptionModel exceptionModel) {
        return null;
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

    private String renderStringLiteral(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b");  break;
                case '\t': sb.append("\\t");  break;
                case '\n': sb.append("\\n");  break;
                case '\f': sb.append("\\f");  break;
                case '\r': sb.append("\\r");  break;
                default:   sb.append(c);      break;
            }
        }
        sb.append("\"");
        return sb.toString();
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
}
