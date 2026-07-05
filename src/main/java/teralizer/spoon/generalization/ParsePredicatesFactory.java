package teralizer.spoon.generalization;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;

public final class ParsePredicatesFactory {

    private ParsePredicatesFactory() {
    }

    public static CtClass<?> createParsePredicatesClass() {
        return Launcher.parseClass(source());
    }

    static String source() {
        return "private static class ParsePredicates {\n"
            + "    private ParsePredicates() {\n"
            + "    }\n"
            + "\n"
            + "    static boolean isInteger(String s) {\n"
            + "        try {\n"
            + "            Integer.parseInt(s);\n"
            + "            return true;\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    static boolean isLong(String s) {\n"
            + "        try {\n"
            + "            Long.parseLong(s);\n"
            + "            return true;\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    static boolean isFloat(String s) {\n"
            + "        try {\n"
            + "            Float.parseFloat(s);\n"
            + "            return true;\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    static boolean isDouble(String s) {\n"
            + "        try {\n"
            + "            Double.parseDouble(s);\n"
            + "            return true;\n"
            + "        } catch (NumberFormatException e) {\n"
            + "            return false;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    }
}
