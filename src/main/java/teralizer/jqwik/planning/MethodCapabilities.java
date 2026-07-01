package teralizer.jqwik.planning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import teralizer.domain.TypeDomain;

public final class MethodCapabilities {
    private static final Map<String, MethodCapability> CAPABILITIES = capabilities();

    private MethodCapabilities() {}

    public static MethodCapability get(String method) {
        return CAPABILITIES.get(method);
    }

    public static boolean isSupported(String method) {
        return CAPABILITIES.containsKey(method);
    }

    public static boolean isInputGeneratable(String method) {
        MethodCapability capability = get(method);
        return capability != null && capability.inputGeneratable;
    }

    public static boolean isOutputRenderable(String method) {
        MethodCapability capability = get(method);
        return capability != null && capability.outputRenderable;
    }

    private static Map<String, MethodCapability> capabilities() {
        Map<String, MethodCapability> capabilities = new LinkedHashMap<>();
        stringPredicate(capabilities, "equals", true, MethodCapability.InputConstraintKind.EQUALITY);
        stringPredicate(capabilities, "equalsIgnoreCase", false, MethodCapability.InputConstraintKind.NONE);
        stringPredicate(capabilities, "startsWith", true, MethodCapability.InputConstraintKind.PREFIX);
        stringPredicate(capabilities, "endsWith", true, MethodCapability.InputConstraintKind.SUFFIX);
        stringPredicate(capabilities, "contains", true, MethodCapability.InputConstraintKind.CONTAINS);
        stringPredicate(capabilities, "isEmpty", true, MethodCapability.InputConstraintKind.EMPTY);
        stringTransform(capabilities, "concat");
        stringTransform(capabilities, "trim");
        stringTransform(capabilities, "replace");
        stringTransform(capabilities, "toLowerCase");
        stringTransform(capabilities, "toUpperCase");
        stringNumeric(capabilities, "length");
        stringNumeric(capabilities, "indexOf");
        stringNumeric(capabilities, "lastIndexOf");

        math(capabilities, "sqrt");
        math(capabilities, "pow");
        math(capabilities, "exp");
        math(capabilities, "log");
        math(capabilities, "sin");
        math(capabilities, "cos");
        math(capabilities, "tan");
        math(capabilities, "asin");
        math(capabilities, "acos");
        math(capabilities, "atan");
        math(capabilities, "atan2");
        staticMethod(capabilities, "valueOf", "java.lang.String", TypeDomain.STRING, false, true);
        return Collections.unmodifiableMap(capabilities);
    }

    private static void stringPredicate(
        Map<String, MethodCapability> capabilities,
        String method,
        boolean inputGeneratable,
        MethodCapability.InputConstraintKind inputConstraintKind) {
        instance(capabilities, method, TypeDomain.BOOLEAN, inputGeneratable, true, inputConstraintKind);
    }

    private static void stringTransform(Map<String, MethodCapability> capabilities, String method) {
        instance(capabilities, method, TypeDomain.STRING, false, true, MethodCapability.InputConstraintKind.NONE);
    }

    private static void stringNumeric(Map<String, MethodCapability> capabilities, String method) {
        instance(capabilities, method, TypeDomain.INTEGER, false, false, MethodCapability.InputConstraintKind.NONE);
    }

    private static void instance(
        Map<String, MethodCapability> capabilities,
        String method,
        TypeDomain returnDomain,
        boolean inputGeneratable,
        boolean outputRenderable,
        MethodCapability.InputConstraintKind inputConstraintKind) {
        capabilities.put(method, new MethodCapability(
            method,
            null,
            TypeDomain.STRING,
            returnDomain,
            inputGeneratable,
            outputRenderable,
            inputConstraintKind));
    }

    private static void math(Map<String, MethodCapability> capabilities, String method) {
        staticMethod(capabilities, method, "java.lang.Math", TypeDomain.REAL, false, true);
    }

    private static void staticMethod(
        Map<String, MethodCapability> capabilities,
        String method,
        String staticQualifier,
        TypeDomain returnDomain,
        boolean inputGeneratable,
        boolean outputRenderable) {
        capabilities.put(method, new MethodCapability(
            method,
            staticQualifier,
            null,
            returnDomain,
            inputGeneratable,
            outputRenderable,
            MethodCapability.InputConstraintKind.NONE));
    }
}
