package teralizer.jqwik.planning;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
        instance(capabilities, "equals", true, true);
        instance(capabilities, "equalsIgnoreCase", false, true);
        instance(capabilities, "startsWith", true, true);
        instance(capabilities, "endsWith", true, true);
        instance(capabilities, "contains", true, true);
        instance(capabilities, "isEmpty", true, true);
        instance(capabilities, "concat", false, true);

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
        staticMethod(capabilities, "valueOf", "java.lang.String", false, true);
        return Collections.unmodifiableMap(capabilities);
    }

    private static void instance(
        Map<String, MethodCapability> capabilities,
        String method,
        boolean inputGeneratable,
        boolean outputRenderable) {
        capabilities.put(method, new MethodCapability(method, null, inputGeneratable, outputRenderable));
    }

    private static void math(Map<String, MethodCapability> capabilities, String method) {
        staticMethod(capabilities, method, "java.lang.Math", false, true);
    }

    private static void staticMethod(
        Map<String, MethodCapability> capabilities,
        String method,
        String staticQualifier,
        boolean inputGeneratable,
        boolean outputRenderable) {
        capabilities.put(method, new MethodCapability(method, staticQualifier, inputGeneratable, outputRenderable));
    }
}
