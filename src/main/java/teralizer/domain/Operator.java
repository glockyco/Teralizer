package teralizer.domain;

import java.util.HashMap;
import java.util.Map;

public enum Operator implements Model {

    EQ("==", " == ", " = "),
    NE("!=", " != "),
    LT("<", " < "),
    LE("<=", " <= "),
    GT(">", " > "),
    GE(">=", " >= "),

    PLUS("+", " + "),
    MINUS("-", " - "),
    MUL("*", " * "),
    DIV("/", " / "),
    MOD("%", " % "),

    AND("&", " & "),
    OR("|", " | "),
    XOR("^", " ^ "),

    POW("pow", " pow "),
    SQRT("sqrt", " sqrt "),
    EXP("exp", " exp "),
    LOG("log", " log "),

    SIN("sin", " sin "),
    COS("cos", " cos "),
    TAN("tan", " tan "),
    ASIN("asin", " asin "),
    ACOS("acos", " acos "),
    ATAN("atan", " atan "),
    ATAN2("atan2", " atan2 "),

    SHIFTL("<<"),
    SHIFTR(">>"),
    SHIFTUR(">>>");

    private final String[] symbols;

    private static final Map<String, Operator> lookup = new HashMap<>();

    static {
        for (Operator op : Operator.values()) {
            for (String symbol : op.symbols) {
                lookup.put(symbol, op);
            }
        }
    }

    Operator(final String ... symbols) {
        assert symbols.length > 0;
        this.symbols = symbols;
    }

    public static Operator get(String symbol) {
        if (!lookup.containsKey(symbol)) {
            throw new IllegalArgumentException("Unknown operator symbol: " + symbol);
        }
        return lookup.get(symbol);
    }

    @Override
    public void accept(ModelVisitor visitor) {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    @Override
    public <T> T fold(ModelFolder<T> folder) {
        return folder.fold(this);
    }

    @Override
    public String toString() {
        return this.symbols[0];
    }
}
