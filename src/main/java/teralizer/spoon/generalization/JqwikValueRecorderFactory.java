package teralizer.spoon.generalization;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class JqwikValueRecorderFactory {

    public static CtClass<?> createRecorderClass(Factory factory, Path valueLogPath) {
        CtClass<?> recorderClass = factory.Class().create("JqwikValueRecorder");
        recorderClass.setModifiers(new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC)));

        CtMethod<?> recordMethod = factory.Method().create(
            recorderClass,
            new HashSet<>(Arrays.asList(ModifierKind.PUBLIC, ModifierKind.STATIC, ModifierKind.SYNCHRONIZED)),
            factory.Type().VOID_PRIMITIVE,
            "record",
            Collections.singletonList(factory.createParameter(null, factory.Type().createReference("TestParameters"), "parameters")),
            Collections.emptySet(),
            factory.Core().createBlock()
        );
        recordMethod.getBody().addStatement(factory.Code().createCodeSnippetStatement(createRecordBody(valueLogPath)));

        CtMethod<?> escapeMethod = factory.Method().create(
            recorderClass,
            new HashSet<>(Arrays.asList(ModifierKind.PRIVATE, ModifierKind.STATIC)),
            factory.Type().createReference("java.lang.String"),
            "escapeValue",
            Collections.singletonList(factory.createParameter(null, factory.Type().createReference("java.lang.Object"), "value")),
            Collections.emptySet(),
            factory.Core().createBlock()
        );
        escapeMethod.getBody().addStatement(factory.Code().createCodeSnippetStatement(createEscapeValueBody()));
        return recorderClass;
    }

    static String createRecorderSource(Path valueLogPath) {
        return "public static class JqwikValueRecorder {\n"
            + "    private static final java.nio.file.Path VALUE_LOG_PATH = java.nio.file.Paths.get(\"" + escapePath(valueLogPath) + "\");\n"
            + "    public static synchronized void record(final TestParameters parameters) {\n"
            + createRecordBody(valueLogPath).replace("\n", "\n        ") + "\n"
            + "    }\n"
            + "    // Value rows are tab-separated; escape control characters so each jqwik trial stays one row.\n"
            + "    private static String escapeValue(Object value) {\n"
            + "        String raw = String.valueOf(value);\n"
            + "        StringBuilder escaped = new StringBuilder(raw.length());\n"
            + "        for (int i = 0; i < raw.length(); i++) {\n"
            + "            char ch = raw.charAt(i);\n"
            + "            switch (ch) {\n"
            + "                case '\\\\':\n"
            + "                    escaped.append(\"\\\\\\\\\");\n"
            + "                    break;\n"
            + "                case '\\n':\n"
            + "                    escaped.append(\"\\\\n\");\n"
            + "                    break;\n"
            + "                case '\\r':\n"
            + "                    escaped.append(\"\\\\r\");\n"
            + "                    break;\n"
            + "                case '\\t':\n"
            + "                    escaped.append(\"\\\\t\");\n"
            + "                    break;\n"
            + "                default:\n"
            + "                    if (Character.isISOControl(ch)) {\n"
            + "                        escaped.append(String.format(\"\\\\u%04x\", (int) ch));\n"
            + "                    } else {\n"
            + "                        escaped.append(ch);\n"
            + "                    }\n"
            + "            }\n"
            + "        }\n"
            + "        return escaped.toString();\n"
            + "    }\n"
            + "}";
    }

    private static String createRecordBody(Path valueLogPath) {
        return "try {\n"
            + "    final java.nio.file.Path VALUE_LOG_PATH = java.nio.file.Paths.get(\"" + escapePath(valueLogPath) + "\");\n"
            + "    java.nio.file.Files.createDirectories(VALUE_LOG_PATH.getParent());\n"
            + "    StringBuilder row = new StringBuilder();\n"
            + "    for (java.lang.reflect.Field field : TestParameters.class.getDeclaredFields()) {\n"
            + "        if (field.isSynthetic() || field.getName().startsWith(\"$\")) {\n"
            + "            continue;\n"
            + "        }\n"
            + "        if (row.length() > 0) {\n"
            + "            row.append('\\t');\n"
            + "        }\n"
            + "        field.setAccessible(true);\n"
            + "        row.append(field.getName()).append('=').append(escapeValue(field.get(parameters)));\n"
            + "    }\n"
            + "    java.nio.file.Files.write(VALUE_LOG_PATH, java.util.Collections.singletonList(row.toString()), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);\n"
            + "} catch (java.io.IOException | IllegalAccessException e) {\n"
            + "    throw new RuntimeException(e);\n"
            + "}";
    }

    // Keep this body aligned with createRecorderSource(): Spoon needs a snippet body, while tests use source text.
    private static String createEscapeValueBody() {
        return "String raw = String.valueOf(value);\n"
            + "StringBuilder escaped = new StringBuilder(raw.length());\n"
            + "for (int i = 0; i < raw.length(); i++) {\n"
            + "    char ch = raw.charAt(i);\n"
            + "    switch (ch) {\n"
            + "        case '\\\\':\n"
            + "            escaped.append(\"\\\\\\\\\");\n"
            + "            break;\n"
            + "        case '\\n':\n"
            + "            escaped.append(\"\\\\n\");\n"
            + "            break;\n"
            + "        case '\\r':\n"
            + "            escaped.append(\"\\\\r\");\n"
            + "            break;\n"
            + "        case '\\t':\n"
            + "            escaped.append(\"\\\\t\");\n"
            + "            break;\n"
            + "        default:\n"
            + "            if (Character.isISOControl(ch)) {\n"
            + "                escaped.append(String.format(\"\\\\u%04x\", (int) ch));\n"
            + "            } else {\n"
            + "                escaped.append(ch);\n"
            + "            }\n"
            + "    }\n"
            + "}\n"
            + "return escaped.toString()";
    }

    private static String escapePath(Path valueLogPath) {
        return valueLogPath.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
