package teralizer.tools.cutpvc;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

/**
 * Java agent recording the parameter values passed to a configured set of
 * methods. Usage:
 *
 * <pre>
 *   -javaagent:cut-pvc-capture.jar=methods=/abs/targets.txt,out=/abs/out,callers=org.example.FooTest
 * </pre>
 *
 * The targets file lists one method per line as
 * {@code fq.ClassName#methodName(type1,type2)}, with {@code <init>} selecting
 * a constructor. {@code callers} restricts recording to calls whose immediate
 * caller is the given test class (see {@link CaptureLog#callerClass}).
 */
public final class CaptureAgent {

    private CaptureAgent() {}

    public static void premain(String agentArgs, Instrumentation instrumentation) throws IOException {
        Map<String, String> options = parseOptions(agentArgs);
        String methodsFile = require(options, "methods");
        String outDir = require(options, "out");

        // The agent jar is appended to the system class loader by -javaagent,
        // and the surefire test class loader delegates to it, so the advice
        // code inlined into the target classes resolves CaptureLog without any
        // bootstrap injection.
        CaptureLog.outDir = outDir;
        CaptureLog.callerClass = options.getOrDefault("callers", "");

        List<Target> targets = parseTargets(methodsFile);
        Map<String, List<Target>> byClass = new HashMap<String, List<Target>>();
        for (Target target : targets) {
            byClass.computeIfAbsent(target.className, key -> new ArrayList<Target>()).add(target);
        }
        Set<String> classNames = new HashSet<String>(byClass.keySet());

        new AgentBuilder.Default()
            .type(ElementMatchers.namedOneOf(classNames.toArray(new String[0])))
            .transform(new AgentBuilder.Transformer() {
                @Override
                public DynamicType.Builder<?> transform(
                    DynamicType.Builder<?> builder,
                    TypeDescription typeDescription,
                    ClassLoader classLoader,
                    JavaModule module,
                    java.security.ProtectionDomain protectionDomain
                ) {
                    ElementMatcher.Junction<MethodDescription> matcher = ElementMatchers.none();
                    for (Target target : byClass.get(typeDescription.getName())) {
                        matcher = matcher.or(target.matcher());
                    }
                    return builder.visit(Advice.to(CaptureAdvice.class).on(matcher));
                }
            })
            .installOn(instrumentation);
    }

    private static Map<String, String> parseOptions(String agentArgs) {
        Map<String, String> options = new HashMap<String, String>();
        if (agentArgs == null || agentArgs.isEmpty()) {
            return options;
        }
        for (String part : agentArgs.split(",")) {
            int separator = part.indexOf('=');
            if (separator < 0) {
                throw new IllegalArgumentException("Malformed agent option: " + part);
            }
            options.put(part.substring(0, separator), part.substring(separator + 1));
        }
        return options;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("Missing required agent option: " + key);
        }
        return value;
    }

    private static List<Target> parseTargets(String methodsFile) throws IOException {
        List<Target> targets = new ArrayList<Target>();
        for (String line : Files.readAllLines(Paths.get(methodsFile), StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            targets.add(Target.parse(trimmed));
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("No capture targets in " + methodsFile);
        }
        return targets;
    }

    /** One {@code fq.ClassName#methodName(type1,type2)} capture target. */
    static final class Target {
        final String className;
        final String methodName;
        final List<String> parameterTypes;

        private Target(String className, String methodName, List<String> parameterTypes) {
            this.className = className;
            this.methodName = methodName;
            this.parameterTypes = parameterTypes;
        }

        static Target parse(String spec) {
            int hash = spec.indexOf('#');
            int open = spec.indexOf('(');
            int close = spec.lastIndexOf(')');
            if (hash < 0 || open < hash || close < open) {
                throw new IllegalArgumentException("Malformed capture target: " + spec);
            }
            String className = spec.substring(0, hash);
            String methodName = spec.substring(hash + 1, open);
            String parameters = spec.substring(open + 1, close).trim();
            List<String> parameterTypes = new ArrayList<String>();
            if (!parameters.isEmpty()) {
                for (String parameter : parameters.split(",")) {
                    parameterTypes.add(parameter.trim());
                }
            }
            return new Target(className, methodName, parameterTypes);
        }

        ElementMatcher.Junction<MethodDescription> matcher() {
            ElementMatcher.Junction<MethodDescription> named =
                "<init>".equals(methodName)
                    ? ElementMatchers.isConstructor()
                    : ElementMatchers.<MethodDescription>named(methodName);
            return named.and(new ElementMatcher<MethodDescription>() {
                @Override
                public boolean matches(MethodDescription method) {
                    List<TypeDescription> erasures =
                        method.getParameters().asTypeList().asErasures();
                    if (erasures.size() != parameterTypes.size()) {
                        return false;
                    }
                    for (int i = 0; i < erasures.size(); i++) {
                        TypeDescription erasure = erasures.get(i);
                        String expected = parameterTypes.get(i);
                        if (!erasure.getName().equals(expected)
                            && !erasure.getSimpleName().equals(expected)) {
                            return false;
                        }
                    }
                    return true;
                }
            });
        }
    }
}
