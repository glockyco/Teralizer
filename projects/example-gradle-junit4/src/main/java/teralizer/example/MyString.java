package teralizer.example;

public class MyString {
    public static boolean contains(String str, CharSequence s) {
        return str.contains(s);
    }

    public static boolean startsWith(String str, String prefix) {
        return str.startsWith(prefix);
    }

    public static boolean endsWith(String str, String suffix) {
        return str.endsWith(suffix);
    }

//    public static String toUpperCase(String s) {
//        return s.toUpperCase();
//    }
}
