package teralizer.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Conservative check that a concrete seed satisfies its extracted input predicate. */
public final class SeedSpecConsistency {
    public static final String INPUT_SPEC_NOT_SATISFIED_BY_SEED = "INPUT_SPEC_NOT_SATISFIED_BY_SEED";

    public enum Verdict {
        SATISFIED,
        VIOLATED,
        UNKNOWN
    }

    private SeedSpecConsistency() {
    }

    public static Verdict evaluate(
        Model inputPredicate,
        Map<String, Value> seed,
        List<MethodParameter> testedMethodParameters
    ) {
        if (inputPredicate == null) {
            return Verdict.UNKNOWN;
        }
        FoldedValue folded = inputPredicate.fold(new Evaluator(seed, testedMethodParameters));
        if (!folded.isKnown() || !(folded.value instanceof Boolean)) {
            return Verdict.UNKNOWN;
        }
        return ((Boolean) folded.value) ? Verdict.SATISFIED : Verdict.VIOLATED;
    }

    private static final class Evaluator extends ModelFolder<FoldedValue> {
        private final Map<String, Value> seed;
        private final Map<String, String> parameterTypes;

        private Evaluator(Map<String, Value> seed, List<MethodParameter> testedMethodParameters) {
            this.seed = seed == null ? new HashMap<>() : seed;
            this.parameterTypes = new HashMap<>();
            if (testedMethodParameters != null) {
                for (MethodParameter parameter : testedMethodParameters) {
                    this.parameterTypes.put(parameter.getName(), parameter.getType());
                }
            }
        }

        @Override
        public FoldedValue fold(Constant constant) {
            if (constant == null) {
                return FoldedValue.unknown();
            }
            if (constant.domain == TypeDomain.INTEGER && constant.value instanceof Number) {
                return FoldedValue.known(((Number) constant.value).longValue());
            }
            return FoldedValue.known(constant.value);
        }

        @Override
        public FoldedValue fold(Variable variable) {
            if (variable == null || variable.name == null || !this.seed.containsKey(variable.name)) {
                return FoldedValue.unknown();
            }
            return this.foldVariableValue(variable, this.seed.get(variable.name));
        }

        @Override
        public FoldedValue fold(ArrayExpression expression) {
            return FoldedValue.unknown();
        }

        @Override
        public FoldedValue fold(ArrayElementExpression expression, FoldedValue elementSelector) {
            return FoldedValue.unknown();
        }

        @Override
        public FoldedValue fold(Invocation invocation, FoldedValue receiver, List<FoldedValue> args) {
            if (invocation == null || invocation.receiver == null || invocation.qualifier != null) {
                return FoldedValue.unknown();
            }
            if (!"length".equals(invocation.method) || args == null || !args.isEmpty()) {
                return FoldedValue.unknown();
            }
            if (!receiver.isKnown() || !(receiver.value instanceof String)) {
                return FoldedValue.unknown();
            }
            return FoldedValue.known((long) ((String) receiver.value).length());
        }

        @Override
        public FoldedValue fold(Not not, FoldedValue operand) {
            if (!operand.isKnown() || !(operand.value instanceof Boolean)) {
                return FoldedValue.unknown();
            }
            return FoldedValue.known(!((Boolean) operand.value));
        }

        @Override
        public FoldedValue fold(Operation operation, FoldedValue left, FoldedValue right) {
            if (operation == null || operation.op == null || !left.isKnown() || !right.isKnown()) {
                return FoldedValue.unknown();
            }
            switch (operation.op) {
                case EQ:
                    return this.evaluateEquality(left.value, right.value, true);
                case NE:
                    return this.evaluateEquality(left.value, right.value, false);
                case LT:
                    return this.compareLongs(left.value, right.value, Comparison.LT);
                case LE:
                    return this.compareLongs(left.value, right.value, Comparison.LE);
                case GT:
                    return this.compareLongs(left.value, right.value, Comparison.GT);
                case GE:
                    return this.compareLongs(left.value, right.value, Comparison.GE);
                case PLUS:
                    return this.longArithmetic(left.value, right.value, Arithmetic.PLUS);
                case MINUS:
                    return this.longArithmetic(left.value, right.value, Arithmetic.MINUS);
                case MUL:
                    return this.longArithmetic(left.value, right.value, Arithmetic.MUL);
                case DIV:
                    return this.longArithmetic(left.value, right.value, Arithmetic.DIV);
                case MOD:
                    return this.longArithmetic(left.value, right.value, Arithmetic.MOD);
                case AND:
                    return this.booleanConnective(left.value, right.value, true);
                case OR:
                    return this.booleanConnective(left.value, right.value, false);
                default:
                    return FoldedValue.unknown();
            }
        }

        @Override
        public FoldedValue fold(Operator operator) {
            return FoldedValue.unknown();
        }

        @Override
        public FoldedValue fold(Error error) {
            return FoldedValue.unknown();
        }

        @Override
        public FoldedValue fold(ExceptionModel exceptionModel) {
            return FoldedValue.unknown();
        }

        private FoldedValue foldVariableValue(Variable variable, Value value) {
            if (value instanceof NullValue) {
                return FoldedValue.known(null);
            }
            if (value instanceof ReferenceValue || value == null) {
                return FoldedValue.unknown();
            }
            if (value instanceof StringValue) {
                return variable.domain == TypeDomain.STRING
                    ? FoldedValue.known(((StringValue) value).getValue())
                    : FoldedValue.unknown();
            }
            if (!(value instanceof PrimitiveValue)) {
                return FoldedValue.unknown();
            }

            PrimitiveValue primitive = (PrimitiveValue) value;
            Object payload = primitive.getValue();
            String declaredType = this.parameterTypes.getOrDefault(variable.name, primitive.getJavaType());
            switch (variable.domain) {
                case INTEGER:
                    return this.foldIntegerVariable(payload, declaredType);
                case BOOLEAN:
                    return payload instanceof Boolean ? FoldedValue.known(payload) : FoldedValue.unknown();
                case CHAR:
                    return payload instanceof Character ? FoldedValue.known(payload) : FoldedValue.unknown();
                case REAL:
                    return payload instanceof Float || payload instanceof Double
                        ? FoldedValue.known(payload)
                        : FoldedValue.unknown();
                default:
                    return FoldedValue.unknown();
            }
        }

        private FoldedValue foldIntegerVariable(Object payload, String declaredType) {
            if (payload instanceof Byte || payload instanceof Short || payload instanceof Integer || payload instanceof Long) {
                return FoldedValue.known(((Number) payload).longValue());
            }
            if (payload instanceof Character && isCharType(declaredType)) {
                return FoldedValue.known((long) ((Character) payload).charValue());
            }
            if (payload instanceof Boolean && isBooleanType(declaredType)) {
                return FoldedValue.known(((Boolean) payload) ? 1L : 0L);
            }
            return FoldedValue.unknown();
        }

        private FoldedValue evaluateEquality(Object left, Object right, boolean equalResult) {
            if (!sameComparableKind(left, right)) {
                return FoldedValue.unknown();
            }
            boolean equal = Objects.equals(left, right);
            return FoldedValue.known(equalResult == equal);
        }

        private FoldedValue compareLongs(Object left, Object right, Comparison comparison) {
            if (!(left instanceof Long) || !(right instanceof Long)) {
                return FoldedValue.unknown();
            }
            long leftLong = (Long) left;
            long rightLong = (Long) right;
            switch (comparison) {
                case LT:
                    return FoldedValue.known(leftLong < rightLong);
                case LE:
                    return FoldedValue.known(leftLong <= rightLong);
                case GT:
                    return FoldedValue.known(leftLong > rightLong);
                case GE:
                    return FoldedValue.known(leftLong >= rightLong);
                default:
                    return FoldedValue.unknown();
            }
        }

        private FoldedValue longArithmetic(Object left, Object right, Arithmetic arithmetic) {
            if (!(left instanceof Long) || !(right instanceof Long)) {
                return FoldedValue.unknown();
            }
            long leftLong = (Long) left;
            long rightLong = (Long) right;
            try {
                switch (arithmetic) {
                    case PLUS:
                        return FoldedValue.known(Math.addExact(leftLong, rightLong));
                    case MINUS:
                        return FoldedValue.known(Math.subtractExact(leftLong, rightLong));
                    case MUL:
                        return FoldedValue.known(Math.multiplyExact(leftLong, rightLong));
                    case DIV:
                        if (rightLong == 0 || leftLong == Long.MIN_VALUE && rightLong == -1) {
                            return FoldedValue.unknown();
                        }
                        return FoldedValue.known(leftLong / rightLong);
                    case MOD:
                        if (rightLong == 0) {
                            return FoldedValue.unknown();
                        }
                        return FoldedValue.known(leftLong % rightLong);
                    default:
                        return FoldedValue.unknown();
                }
            } catch (ArithmeticException exception) {
                return FoldedValue.unknown();
            }
        }

        private FoldedValue booleanConnective(Object left, Object right, boolean and) {
            if (!(left instanceof Boolean) || !(right instanceof Boolean)) {
                return FoldedValue.unknown();
            }
            boolean leftBoolean = (Boolean) left;
            boolean rightBoolean = (Boolean) right;
            return FoldedValue.known(and ? leftBoolean && rightBoolean : leftBoolean || rightBoolean);
        }
    }

    private static boolean sameComparableKind(Object left, Object right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        if (left instanceof Long && right instanceof Long) {
            return true;
        }
        if (left instanceof Boolean && right instanceof Boolean) {
            return true;
        }
        if (left instanceof String && right instanceof String) {
            return true;
        }
        return left instanceof Character && right instanceof Character;
    }

    private static boolean isBooleanType(String javaType) {
        return "boolean".equals(javaType) || "java.lang.Boolean".equals(javaType);
    }

    private static boolean isCharType(String javaType) {
        return "char".equals(javaType) || "java.lang.Character".equals(javaType);
    }

    private enum Comparison {
        LT,
        LE,
        GT,
        GE
    }

    private enum Arithmetic {
        PLUS,
        MINUS,
        MUL,
        DIV,
        MOD
    }

    private static final class FoldedValue {
        private static final FoldedValue UNKNOWN = new FoldedValue(false, null);

        private final boolean known;
        private final Object value;

        private FoldedValue(boolean known, Object value) {
            this.known = known;
            this.value = value;
        }

        private static FoldedValue known(Object value) {
            return new FoldedValue(true, value);
        }

        private static FoldedValue unknown() {
            return UNKNOWN;
        }

        private boolean isKnown() {
            return this.known;
        }
    }
}
