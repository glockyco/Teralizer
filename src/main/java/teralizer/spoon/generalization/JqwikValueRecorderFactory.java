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
        return recorderClass;
    }

    static String createRecorderSource(Path valueLogPath) {
        return "public static class JqwikValueRecorder {\n"
            + "    private static final java.nio.file.Path VALUE_LOG_PATH = java.nio.file.Paths.get(\"" + escapePath(valueLogPath) + "\");\n"
            + "    public static synchronized void record(final TestParameters parameters) {\n"
            + createRecordBody(valueLogPath).replace("\n", "\n        ") + "\n"
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
            + "        row.append(field.getName()).append('=').append(String.valueOf(field.get(parameters)));\n"
            + "    }\n"
            + "    java.nio.file.Files.write(VALUE_LOG_PATH, java.util.Collections.singletonList(row.toString()), java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);\n"
            + "} catch (java.io.IOException | IllegalAccessException e) {\n"
            + "    throw new RuntimeException(e);\n"
            + "}";
    }

    private static String escapePath(Path valueLogPath) {
        return valueLogPath.toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
