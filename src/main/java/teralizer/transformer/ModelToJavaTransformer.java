package teralizer.transformer;

import teralizer.domain.Error;
import teralizer.domain.*;
import teralizer.jqwik.planning.MethodCapabilities;
import teralizer.jqwik.planning.MethodCapability;

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

    public String transform(Value value) {
        if (value instanceof NullValue) {
            return "null";
        }
        if (value instanceof StringValue) {
            return this.renderStringLiteral(((StringValue) value).getValue());
        }
        if (value instanceof PrimitiveValue) {
            Object boxed = ((PrimitiveValue) value).getValue();
            if (boxed instanceof Long) {
                return boxed + "L";
            }
            if (boxed instanceof Float) {
                return this.renderFloat((Float) boxed);
            }
            if (boxed instanceof Double) {
                return this.transform(((Double) boxed).doubleValue());
            }
            if (boxed instanceof Character) {
                return "(char) " + (int) ((Character) boxed).charValue();
            }
            // Byte, Short, Integer, and Boolean all render via their canonical toString().
            return boxed.toString();
        }
        if (value instanceof ReferenceValue) {
            throw new IllegalArgumentException(
                "An opaque reference value (" + value.getJavaType() + ") has no Java literal and must not be"
                    + " rendered; a captured receiver is offset-skipped and an unsupported reference is dropped"
                    + " before generation.");
        }
        throw new IllegalArgumentException("Unknown Value variant: " + value.getClass().getName());
    }

    private String renderFloat(float value) {
        if (Float.isNaN(value)) {
            return "Float.NaN";
        }
        if (value == Float.POSITIVE_INFINITY) {
            return "Float.POSITIVE_INFINITY";
        }
        if (value == Float.NEGATIVE_INFINITY) {
            return "Float.NEGATIVE_INFINITY";
        }
        return value + "F";
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
            // A clause whose referenced variables are all non-symbolized (outside the generated set)
            // stays at its concrete value on the path, so it is trivially satisfied and sound to
            // drop -- independent of whether it can be rendered. Checking this before rendering keeps
            // a renderable clause over a filtered parameter (e.g. a String equality on a concrete
            // argument) from emitting a reference to a `_p_` field that is never generated.
            Set<String> referenced = new LinkedHashSet<>();
            clause.accept(new VariableNameCollector(referenced));
            if (!referenced.isEmpty() && Collections.disjoint(referenced, generalizableParameterNames)) {
                continue;
            }
            // The clause constrains a generated parameter (or references none): it must render; a
            // non-generalizable operator here would weaken the predicate, so let it propagate.
            rendered.add(clause.fold(this));
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
    public String fold(Constant constant) {
        assert constant != null;
        assert constant.domain != null;
        switch (constant.domain) {
            case INTEGER:
                return this.transform(((Number) constant.value).longValue());
            case REAL:
                return this.transform(((Number) constant.value).doubleValue());
            case STRING:
                return this.renderStringLiteral((String) constant.value);
            default:
                throw new NonGeneralizableExpressionException(
                    "Cannot render constant with domain '" + constant.domain + "' as Java.");
        }
    }

    @Override
    public String fold(Variable variable) {
        assert variable != null;
        assert variable.name != null;
        assert variable.domain != null;
        if (variable.domain == TypeDomain.INTEGER
            && ("boolean".equals(this.variableTypes.get(variable.name))
                || "java.lang.Boolean".equals(this.variableTypes.get(variable.name)))) {
            return "(_p_." + variable.name + " ? 1 : 0)";
        }
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
    public String fold(Invocation invocation, String receiver, List<String> args) {
        MethodCapability capability = MethodCapabilities.get(invocation.method);
        if (capability == null || !capability.outputRenderable) {
            throw new NonGeneralizableExpressionException(
                "Cannot render call '" + invocation.method + "' as Java.");
        }
        String argList = String.join(", ", args);
        if (invocation.receiver != null) {
            if (capability.staticQualifier != null) {
                throw new NonGeneralizableExpressionException(
                    "Cannot render static call '" + invocation.method + "' as an instance invocation.");
            }
            if (!isStringExpression(invocation.receiver)) {
                throw new NonGeneralizableExpressionException(
                    "Cannot render string call '" + invocation.method + "' on a non-string receiver.");
            }
            for (Expression arg : invocation.args) {
                if (!isStringExpression(arg)) {
                    throw new NonGeneralizableExpressionException(
                        "Cannot render string call '" + invocation.method + "' with a non-string argument.");
                }
            }
            return "(" + receiver + "." + invocation.method + "(" + argList + "))";
        }
        if (capability.staticQualifier == null) {
            throw new NonGeneralizableExpressionException(
                "Cannot render instance call '" + invocation.method + "' without a receiver.");
        }
        if (!capability.staticQualifier.equals(invocation.qualifier)) {
            throw new NonGeneralizableExpressionException(
                "Cannot render static call '" + invocation.qualifier + "." + invocation.method
                    + "'; expected qualifier '" + capability.staticQualifier + "'.");
        }
        return renderQualifier(capability.staticQualifier) + "." + invocation.method + "(" + argList + ")";
    }

    @Override
    public String fold(Not not, String operand) {
        return "(!" + operand + ")";
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

    private static String renderQualifier(String qualifier) {
        if (qualifier != null && qualifier.startsWith("java.lang.")) {
            return qualifier.substring("java.lang.".length());
        }
        return qualifier;
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

    private static boolean isStringExpression(Expression expression) {
        return expression instanceof Variable
            && ((Variable) expression).domain == TypeDomain.STRING
            || expression instanceof Constant
            && ((Constant) expression).domain == TypeDomain.STRING
            || (expression instanceof Invocation && isStringReturningInvocation((Invocation) expression));
    }

    private static boolean isStringReturningInvocation(Invocation invocation) {
        if (invocation.receiver == null) {
            return "java.lang.String".equals(invocation.qualifier) && "valueOf".equals(invocation.method);
        }
        switch (invocation.method) {
            case "concat":
            case "trim":
            case "replace":
            case "toLowerCase":
            case "toUpperCase":
                return true;
            default:
                return false;
        }
    }

    private static boolean isFloatingPoint(Expression expression) {
        if (expression instanceof Variable
            && ((Variable) expression).domain == TypeDomain.REAL
            || expression instanceof Constant
            && ((Constant) expression).domain == TypeDomain.REAL) {
            return true;
        }
        if (expression instanceof Invocation) {
            Invocation invocation = (Invocation) expression;
            MethodCapability capability = MethodCapabilities.get(invocation.method);
            return capability != null
                && capability.outputRenderable
                && "java.lang.Math".equals(capability.staticQualifier);
        }
        if (expression instanceof Operation) {
            Operation inner = (Operation) expression;
            switch (inner.op) {
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
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
